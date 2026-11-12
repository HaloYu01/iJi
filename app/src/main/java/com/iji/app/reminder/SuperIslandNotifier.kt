package com.iji.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.iji.app.MainActivity
import com.iji.app.R
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.shizuku.ShizukuBridge
import com.iji.app.shizuku.ShizukuState
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * 小米超级岛（焦点通知）样式的记账提醒。
 *
 * 实现口径来自 NexioSchedule 与 Hyper-pick-up-code：往一条普通通知的 extras 里塞
 * `miui.focus.param`（岛卡片的参数 JSON）与 `miui.focus.pics`（岛上的图标 Bundle），
 * MIUI/HyperOS 的状态栏框架识别到这两项就会把它渲染成超级岛。
 *
 * 焦点通知有两代协议：老 MIUI 走 `protocol: 1`（NexioSchedule），HyperOS 2+ 走
 * `protocol: 3`（Hyper-pick-up-code，读系统设置 `notification_focus_protocol` 判定），
 * 协议发错一代系统会当普通通知处理——这正是「发了却不上岛」的最常见原因。
 * 另外第三方应用可能被云端白名单校验拦下：Shizuku 可用时借它短暂切断
 * 小米推送框架（XMSF）的网络，断网窗口内本地 notify 会跳过校验直接上岛，发完立即恢复。
 */
object SuperIslandNotifier {

    private const val TAG = "SuperIslandNotifier"

    /** 独立渠道且重要性高、允许绕过勿扰：不然用户开勿扰后岛会被一并屏蔽 */
    const val CHANNEL_ID = "record_reminder_island"
    const val NOTIFICATION_ID = 9002

    /**
     * 测试发送专用 id（对齐 NexioSchedule 的测试岛独立 ID）：同 id 重复发会被岛框架
     * 视为静默替换、不重新浮出，测试与真实提醒必须分开。
     */
    const val ISLAND_TEST_NOTIFICATION_ID = 5000

    private const val CHANNEL_NAME = "记账提醒（超级岛）"
    private const val CHANNEL_DESC = "以小米超级岛（焦点通知）样式显示的记账提醒"

    /** 官方必选：运营场景标识，岛框架靠它归类 */
    private const val BUSINESS_TAG = "record_reminder"

    /** 同一条岛的更新序号，保证多次 notify 不乱序 */
    private val sequence = AtomicLong(System.currentTimeMillis() / 1000)

    /** 超级岛展开光效（outer_glow）的设置键；默认开，对齐 NexioSchedule 的默认值 */
    const val KEY_ISLAND_GLOW = "island_glow"

    /** 岛展开光效开关：展开超级岛时的外侧辉光动效（JSON 的 outEffectSrc 字段） */
    fun isGlowEnabled(context: Context): Boolean =
        SettingsRepository.getBoolean(context, KEY_ISLAND_GLOW, true)

    /** 发送结果：sent = 通知是否真的发出；detail = 实际走了哪条路，给设置页的提示用 */
    data class SendResult(val sent: Boolean, val detail: String)

    // ── 设备能力检测（三项都只做只读探测，异常一律当「不支持」）──

    /** 系统是否开了超级岛特性开关（非小米 / 未开机型开关时为 false） */
    fun isIslandFeature(): Boolean = runCatching {
        val method = Class.forName("android.os.SystemProperties")
            .getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        method.invoke(null, "persist.sys.feature.island", false) as Boolean
    }.getOrDefault(false)

    /** 焦点通知协议代数：0 = 未知（读不到设置项），3 = HyperOS 2+ */
    fun focusProtocol(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol")
    }.getOrDefault(0)

    /** 是否 MIUI / HyperOS（读 ro.miui.ui.version.name，非小米机型为空串） */
    fun isMiui(): Boolean = runCatching {
        val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        !(get.invoke(null, "ro.miui.ui.version.name") as? String).isNullOrBlank()
    }.getOrDefault(false)

    /**
     * 设备该按哪代焦点协议发。官方判定是系统设置 `notification_focus_protocol`
     * （1/2 = 老焦点模板，3 = HyperOS 3 超级岛模板）；读不到（0）时兜底——
     * 只要是 MIUI/HyperOS 就按 3 发：发错一代系统最多当普通通知处理，不会更糟。
     * 注意不要拿 `persist.sys.feature.island` 当硬门槛：小米 14 这类挖孔屏机型上
     * 该属性可能为 false，但它照样支持焦点通知。
     */
    fun islandLevel(context: Context): Int {
        focusProtocol(context).takeIf { it >= 1 }?.let { return it }
        return if (isMiui() || isIslandFeature()) 3 else 0
    }

    /** 本应用是否有焦点通知展示权限（HyperOS 状态栏框架的白名单判定） */
    fun hasFocusPermission(context: Context): Boolean = runCatching {
        val uri = Uri.parse("content://miui.statusbar.notification.public")
        val args = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(uri, "canShowFocus", null, args)
            ?.getBoolean("canShowFocus", false) ?: false
    }.getOrDefault(false)

    /**
     * 发一条超级岛样式的记账提醒（通用入口 [sendIsland] 的记账提醒特化）。
     * Shizuku 可用时总是走 XMSF 断网窗口（100ms 实测口径）；
     * 失败不抛异常，结果与路径都在 [SendResult] 里。
     *
     * @param testMode 测试发送用独立通知 id：同 id 重复发会被岛框架视为静默替换
     *   （不重新浮出），测试与真实提醒分开才不会互相干扰
     */
    fun sendReminder(
        context: Context,
        title: String,
        content: String,
        testMode: Boolean = false,
    ): SendResult = sendIsland(
        context,
        channelId = CHANNEL_ID,
        channelName = CHANNEL_NAME,
        channelDesc = CHANNEL_DESC,
        notificationId = if (testMode) ISLAND_TEST_NOTIFICATION_ID else NOTIFICATION_ID,
        businessTag = BUSINESS_TAG,
        title = title,
        content = content,
        hintTitle = title.take(8),
        clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )

    /**
     * 通用焦点通知（超级岛）发送入口：任意业务（记账提醒 / 预算提醒 / 截屏状态 /
     * 周期账单自动记账）都能借它上岛，渠道、通知 id、business 标识、点击行为各自独立。
     *
     * 岛参数整体采用 NexioSchedule 的成熟结构（协议 1：baseInfo 卡片 + hintInfo 底栏 +
     * 模板 2 岛区域 + islandTimeout 3600），在 HyperOS 2/3 上经其用户群充分验证，
     * 没有模板 16 的图标位/组件替换坑。展开卡按钮由 hintInfo.actionInfo 的
     * actionIntent URI（actionIntentType=1 跳 Activity）承载，不需要 actions Bundle。
     *
     * @param hintTitle 底部横条的短文案（横条空间窄，过长必出省略号）
     * @param clickIntent 点击展开卡/横条时的跳转意图（同时用于按钮的 intent URI）
     * @param islandActionTitle 岛按钮文案；与 [islandAction] 成对提供才生效
     * @param islandAction 岛按钮的 PendingIntent（协议 3 真机验证的承载方式见下方实现；
     *   识别结果的「确认记账」用广播，点按钮直接入账，不必回应用）
     * @param ongoing 进行中（不可滑动清除，靠 [cancel] 或超时结束）。识屏「正在识别」
     *   这类过程态用它：用户此刻在别的 App 上，岛是唯一的进度反馈，不能被误清除
     * @param islandTimeoutSeconds 岛存活秒数；过程态给短值（分钟级），
     *   万一流程异常没走到收尾，岛自己消失，不会永久占着状态栏
     */
    fun sendIsland(
        context: Context,
        channelId: String,
        channelName: String,
        channelDesc: String,
        notificationId: Int,
        businessTag: String,
        title: String,
        content: String,
        hintTitle: String,
        clickIntent: Intent,
        islandActionTitle: String? = null,
        islandAction: PendingIntent? = null,
        ongoing: Boolean = false,
        islandTimeoutSeconds: Int = 3600,
        /** 大岛样式（官方模板 16 + islandProperty=2，展开卡片直接浮出）；默认普通岛 */
        largeIsland: Boolean = false,
        /** 大岛展开卡主体的多行明细；null = 用 [content]（单行） */
        islandDetail: String? = null,
        /** 大岛头部 / 底栏的摘要短句；null = 用 [hintTitle] */
        islandSummary: String? = null,
        /** 大岛左侧大图标（当前识别到的类别图标）；null = 用应用图标 */
        islandIcon: Icon? = null,
    ): SendResult {
        ensureChannel(context, channelId, channelName, channelDesc)

        val level = islandLevel(context)
        val focusAllowed = hasFocusPermission(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 岛按钮（协议 3）：按钮不在 JSON 参数里，而是 extras 的 `miui.focus.actions`
        // Bundle（Maling-Island 真机验证的承载方式）。大岛（模板 16）的底栏按钮读
        // `miui.focus.action_hint_v2`，普通岛读 `miui.focus.action_complete`——两个键都放。
        val confirmAction = islandAction?.takeIf { level >= 3 }?.let {
            Notification.Action.Builder(null, islandActionTitle ?: "确认", it).build()
        }

        fun buildIslandNotification(params: String): Notification {
            val b = Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                // ongoing 与 autoCancel 语义互斥：进行中的岛由调用方撤下，点掉不该消失
                .setAutoCancel(!ongoing)
                // 不带时间戳、不同步到穿戴设备、同岛重复更新不重复响铃
                .setShowWhen(false)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
            if (ongoing) {
                b.setOngoing(true)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    // 兜底：流程异常（进程被杀）时通知与岛一起过期，不留残影
                    .setTimeoutAfter(islandTimeoutSeconds * 1000L)
            }
            b.addExtras(picsExtras(context, islandIcon.takeIf { largeIsland }))
            if (confirmAction != null) {
                b.addAction(confirmAction)
                b.addExtras(
                    Bundle().apply {
                        putBundle(
                            "miui.focus.actions",
                            Bundle().apply {
                                putParcelable("miui.focus.action_complete", confirmAction)
                                if (largeIsland) {
                                    putParcelable("miui.focus.action_hint_v2", confirmAction)
                                }
                            },
                        )
                    },
                )
            }
            b.extras.putString("miui.focus.param", params)
            return b.build()
        }

        val clickUri = clickIntent.toUri(Intent.URI_INTENT_SCHEME)
        // 协议按设备选：HyperOS 3（focusProtocol = 3）只认 protocol 3 的模板，
        // 拿 protocol 1 发过去 SystemUI 会建出岛事件但**不渲染**（真机 logcat 实测：
        // DynamicIslandEventCoordinator 收到 UpdateDynamicIsland，屏幕上什么都没有）。
        val params = when {
            level >= 3 && largeIsland -> islandParamsJsonV3Large(
                title = title,
                content = content,
                detailContent = islandDetail ?: content,
                summary = islandSummary ?: hintTitle,
                businessTag = businessTag,
                ticker = hintTitle,
                islandTimeoutSeconds = islandTimeoutSeconds,
                glowEnabled = isGlowEnabled(context),
                clickTargetPage = MainActivity::class.java.name,
            )
            level >= 3 -> islandParamsJsonV3(
                title = title,
                content = content,
                businessTag = businessTag,
                ticker = hintTitle,
                islandTimeoutSeconds = islandTimeoutSeconds,
                glowEnabled = isGlowEnabled(context),
            )
            else -> islandParamsJson(
                title = title,
                content = content,
                businessTag = businessTag,
                hintTitle = hintTitle,
                clickUri = clickUri,
                glowEnabled = isGlowEnabled(context),
                islandTimeoutSeconds = islandTimeoutSeconds,
            )
        }
        val notification = buildIslandNotification(params)

        // 焦点通知需要过白名单：直发时校验在线执行、未过会回落普通通知（实测）；
        // Shizuku 可用时走断网窗口（100ms，真机实测有效口径），校验被跳过直接上岛。
        // 窗口全局串行（见 ShizukuBridge.withXmsfBypass），连续测试不会互相踩。
        val manager = context.getSystemService(NotificationManager::class.java)
        val (sent, bypassed) = ShizukuBridge.withXmsfBypass(context) {
            try {
                if (manager == null) {
                    false
                } else {
                    manager.notify(notificationId, notification)
                    true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "超级岛通知发送失败", t)
                false
            }
        }

        val bypassText = when {
            bypassed -> "已生效"
            ShizukuBridge.state(context) == ShizukuState.READY -> "调用失败"
            else -> "未生效（Shizuku 未授权或未运行）"
        }
        // 诊断串尽量全：真机上只看它就能判定走哪条分支、卡在哪一环
        val detail = "岛 protocol=$level · 岛特性=${if (isIslandFeature()) "√" else "×"}" +
            " · MIUI=${if (isMiui()) "√" else "×"} · 焦点权限=${if (focusAllowed) "有" else "无"}" +
            " · bypass=$bypassText"
        return SendResult(sent, detail)
    }

    /** 撤下一条岛通知（进行中状态收尾；幂等，通知不存在时也安全） */
    fun cancel(context: Context, notificationId: Int) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        }
    }

    private fun ensureChannel(context: Context, channelId: String, channelName: String, channelDesc: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = channelDesc
                setBypassDnd(true)
            },
        )
    }

    /**
     * 岛上的图标：对齐 NexioSchedule，只备 4 个键、全部用桌面图标（岛参数只引用
     * pic_small / pic_small_dark，缩略岛显示应用图标）。
     *
     * 大岛（模板 16）另备「类别图标」键 `miui.focus.pic_category`（含 dark 同图）：
     * 展开卡左侧大图标显示当前识别到的分类（彩色圆底 + 白线素材 / 首字徽标），
     * 由调用方按识别结果合成（见 `categoryNotificationBitmap`）。
     * internal：现在只服务超级岛路径；原生实时通知刻意不再叠加焦点 extras。
     */
    internal fun picsExtras(context: Context, islandIcon: Icon? = null): Bundle = Bundle().apply {
        val icon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        putBundle(
            "miui.focus.pics",
            Bundle().apply {
                // 协议 1 用的键
                putParcelable("miui.focus.pic_app_icon", icon)
                putParcelable("miui.focus.pic_app_icon_dark", icon)
                putParcelable("miui.focus.pic_small", icon)
                putParcelable("miui.focus.pic_small_dark", icon)
                // 协议 3 用的键（见 islandParamsJsonV3 / V3Large）；两套键同时带上，
                // 系统按自己那代协议取用，多余的键会被忽略
                putParcelable("miui.focus.pic_app", icon)
                putParcelable("miui.focus.pic_icon_main", icon)
                putParcelable("miui.focus.pic_ticker", icon)
                putParcelable("miui.focus.pic_aod", icon)
                if (islandIcon != null) {
                    putParcelable("miui.focus.pic_category", islandIcon)
                    putParcelable("miui.focus.pic_category_dark", islandIcon)
                }
            },
        )
    }

    /**
     * 岛参数（协议 1），结构逐字段对齐 NexioSchedule 的 buildIslandParamsJson——
     * 其在 HyperOS 2/3 上经用户群充分验证：
     * - 卡片 = baseInfo（标题+正文，纯文本无图标位，天然对齐）+ hintInfo（底栏短文案
     *   + 「查看」按钮，按钮走 actionInfo.actionIntent URI，actionIntentType=1 跳
     *   Activity，无需 actions Bundle）；
     * - 岛 = 模板 2（左标题右正文）+ 缩略态应用图标，islandTimeout=3600 让岛存活
     *   一小时（不随应用退场立即消失）。
     * 布局实验教训：模板 16 的 iconTextInfo/baseInfo 混搭都会让参数不被识别，勿再动。
     */
    internal fun islandParamsJson(
        title: String,
        content: String,
        businessTag: String,
        hintTitle: String,
        clickUri: String,
        glowEnabled: Boolean = true,
        islandTimeoutSeconds: Int = 3600,
    ): String {
        val paramV2 = JSONObject().apply {
            put("business", businessTag)
            put("protocol", 1)
            put("enableFloat", true)
            put("updatable", true)
            // 展开岛的外侧辉光光效；关掉时传空串（NexioSchedule 同款开关的取值口径）
            put("outEffectSrc", if (glowEnabled) "outer_glow" else "")
            put("reopen", "reopen")
            put("sequence", sequence.incrementAndGet())
            put("aodTitle", title)

            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", title)
                put("content", content)
                put("subTitle", "")
                put("extraTitle", "")
                put("specialTitle", "")
                put("subContent", "")
                put("picFunction", "")
                put("showDivider", true)
                put("showContentDivider", false)
                put("colorTitle", "#111111")
                put("colorTitleDark", "#ffffff")
                put("colorContent", "#333333")
                put("colorContentDark", "#cccccc")
            })

            put("picInfo", JSONObject().apply {
                put("type", 1)
                put("pic", "")
            })

            put("hintInfo", JSONObject().apply {
                put("type", 2)
                put("title", hintTitle)
                put("content", "")
                put("timerInfo", JSONObject().apply {
                    put("timerType", 0)
                    put("timerWhen", 0)
                    put("timerTotal", 0)
                    put("timerSystemCurrent", 0)
                })
                put("subContent", "")
                put("subTitle", "")
                put("colorContent", "#666666")
                put("colorContentDark", "#aaaaaa")
                put("colorTitle", "#222222")
                put("colorTitleDark", "#eeeeee")
                put("colorSubContent", "#666666")
                put("colorSubContentDark", "#aaaaaa")
                put("colorSubTitle", "#222222")
                put("colorSubTitleDark", "#eeeeee")
                put("actionInfo", JSONObject().apply {
                    put("action", "miui.focus.action_hint_v2")
                    put("actionTitle", "查看")
                    put("actionTitleColor", "#FFFFFF")
                    put("actionTitleColorDark", "#FFFFFF")
                    put("actionBgColor", "#3482FF")
                    put("actionBgColorDark", "#3482FF")
                    // 1 = url to activity：直接拉起应用，不用再建广播接收器
                    put("actionIntentType", 1)
                    put("actionIntent", clickUri)
                })
            })

            put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                put("islandTimeout", islandTimeoutSeconds)
                put("bigIslandArea", JSONObject().apply {
                    put("templateNo", 2)
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("textInfo", JSONObject().apply {
                            put("title", title)
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    })
                    put("textInfo", JSONObject().apply {
                        put("frontTitle", "")
                        put("title", content)
                        put("content", "")
                        put("showHighlightColor", false)
                        put("narrowFont", false)
                    })
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_small")
                        put("picDark", "miui.focus.pic_small_dark")
                    })
                })
            })
        }
        return JSONObject().put("param_v2", paramV2).toString()
    }

    /**
     * 协议 3（HyperOS 2/3 超级岛）参数，结构对齐 Maling-Island 的「正在识别」模板
     * （`IslandNotificationHelper.RECOGNITION_TEMPLATE`，同项目另一条取餐码模板也同族）。
     *
     * 与协议 1 的差别（都是实测得出的）：
     * - 顶层多 `ticker` / `tickerPic` / `isShowNotification` / `islandFirstFloat` / `timeout`
     *   / `aodTitle` / `aodPic`：少了这些，SystemUI 会建出岛事件但不浮出；
     * - `param_island` 用 `imageTextInfoLeft` + `imageTextInfoRight` 两栏（左图标右文字），
     *   而不是协议 1 的 `templateNo = 2` 模板号；
     * - 没有 `hintInfo`（协议 1 的底栏 + 「查看」按钮在协议 3 里换成 shareData/actions）；
     * - 图标键换成 `miui.focus.pic_app` / `pic_ticker` / `pic_aod`（见 [picsExtras]）。
     *
     * @param ticker 状态栏横条与 AOD 的短文案（字符数很少，过长会被截断）
     */
    internal fun islandParamsJsonV3(
        title: String,
        content: String,
        businessTag: String,
        ticker: String,
        islandTimeoutSeconds: Int = 3600,
        glowEnabled: Boolean = true,
    ): String {
        val paramV3 = JSONObject().apply {
            put("protocol", 3)
            put("business", businessTag)
            put("updatable", true)
            put("ticker", ticker)
            put("tickerPic", "miui.focus.pic_ticker")
            put("enableFloat", true)
            put("isShowNotification", true)
            // 首次出现就浮出，不等用户下拉通知栏
            put("islandFirstFloat", true)
            put("timeout", islandTimeoutSeconds)
            put("aodTitle", ticker)
            put("aodPic", "miui.focus.pic_aod")
            put("outEffectSrc", if (glowEnabled) "outer_glow" else "")

            put("picInfo", JSONObject().apply {
                put("type", 1)
                put("pic", "miui.focus.pic_app")
                put("picDark", "miui.focus.pic_app")
                put("loop", false)
                put("autoplay", false)
                put("number", 0)
            })

            put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                put("islandOrder", false)
                put("islandTimeout", islandTimeoutSeconds)
                put("dismissIsland", false)
                put("needCloseAnimation", true)
                put("bigIslandArea", JSONObject().apply {
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_app")
                        })
                    })
                    put("imageTextInfoRight", JSONObject().apply {
                        put("type", 2)
                        put("textInfo", JSONObject().apply {
                            put("title", title)
                            // 大岛展开卡的副行带上正文（识别到几笔），一眼可读，不用点开通知
                            put("content", content)
                            put("colorContent", "#CCD3DD")
                            put("colorTitle", "#FFFFFF")
                            put("turnAnim", false)
                            put("narrowFont", false)
                            put("showHighlightColor", false)
                        })
                    })
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_app")
                    })
                })
            })

            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", title)
                put("content", content)
                put("showDivider", false)
                put("showContentDivider", false)
                put("colorTitle", "#000000")
                put("colorTitleDark", "#FFFFFF")
                put("colorContent", "#666666")
                put("colorContentDark", "#AAAAAA")
            })
        }
        return JSONObject().put("param_v2", paramV3).toString()
    }

    /**
     * 协议 3 **大岛样式**参数：官方模板 16（新图文组件 + 识别图形组件 1 + 按钮组件 5），
     * `islandProperty = 2` —— 岛以展开的大卡片形式浮出（普通岛 = 1 是小胶囊），
     * 结构逐字段对齐 badnng/Hyper-pick-up-code 的 SuperIslandHelper.TEMPLATE_SIXTEEN
     * （HyperOS 2/3 真机用户群充分验证）。
     *
     * **布局按信息层级分配**（保证可读性，每个区域各说各话、不复读）：
     * - 展开卡主体（`iconTextInfo`）：标题 + 逐笔明细（[detailContent]，多行）；
     * - 大岛浮出头部（`bigIslandArea.textInfo`）：标题 + 笔数摘要（[summary]，单行短句）；
     * - 底栏（`hintInfo.title`）：同一摘要——用户扫一眼底栏就知道「几笔、待确认」；
     * - 缩略岛 / 状态栏（`smallIslandArea` / `ticker`）：超短标题，不参与信息展示。
     *
     * 与普通岛 [islandParamsJsonV3] 的差别：
     * - `islandProperty` 2（大）而非 1（小）；
     * - `smallIslandArea` 用 `imageTextInfoRight`（type 6，文字+图标）而非纯 picInfo；
     * - 多 `iconTextInfo`（展开卡的图文主体，含 animIconInfo 动效图标位）；
     * - 多 `hintInfo`（type 1 底栏：状态文案 + 动作按钮）与 `smallWindowInfo`；
     * - 底栏按钮的点击走 extras `miui.focus.action_hint_v2`（见 [sendIsland]），
     *   不用协议 1 的 actionIntent URI。
     *
     * @param ticker 状态栏横条与 AOD 的短文案（过长会被截断）
     * @param clickTargetPage 小窗口点击落地的 Activity 类名（参考项目的 targetPage）
     */
    internal fun islandParamsJsonV3Large(
        title: String,
        content: String,
        detailContent: String,
        summary: String,
        businessTag: String,
        ticker: String,
        islandTimeoutSeconds: Int = 3600,
        glowEnabled: Boolean = true,
        clickTargetPage: String,
    ): String {
        val paramV3 = JSONObject().apply {
            put("protocol", 3)
            put("business", businessTag)
            put("updatable", true)
            put("ticker", ticker)
            put("tickerPic", "miui.focus.pic_ticker")
            put("enableFloat", true)
            put("isShowNotification", true)
            // 首次出现就浮出，不等用户下拉通知栏
            put("islandFirstFloat", true)
            put("timeout", islandTimeoutSeconds)
            put("aodTitle", ticker)
            put("aodPic", "miui.focus.pic_aod")
            put("outEffectSrc", if (glowEnabled) "outer_glow" else "")

            put("picInfo", JSONObject().apply {
                put("pic", "miui.focus.pic_app")
                put("type", 1)
            })
            // 小窗点击落地的页面（参考项目 targetPage = 主 Activity）
            put("smallWindowInfo", JSONObject().apply {
                put("targetPage", clickTargetPage)
            })

            put("param_island", JSONObject().apply {
                put("islandProperty", 2)
                put("islandOrder", false)
                put("dismissIsland", false)
                put("needCloseAnimation", true)
                put("islandTimeout", islandTimeoutSeconds)
                // 结构逐字段对齐**已验证能渲染**的版本（用户截图确认过）：左侧应用图标 +
                // 右侧标题/摘要；smallIslandArea 纯 picInfo。模板 16 组件位校验严格，
                // 缺 imageTextInfoLeft / 换 type 6+4 变体都会整条岛不渲染（实测教训）
                put("bigIslandArea", JSONObject().apply {
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_app")
                        })
                    })
                    put("imageTextInfoRight", JSONObject().apply {
                        put("type", 2)
                        put("textInfo", JSONObject().apply {
                            put("title", title)
                            put("content", summary)
                            put("colorContent", "#CCD3DD")
                            put("colorTitle", "#FFFFFF")
                            put("turnAnim", false)
                            put("narrowFont", false)
                            put("showHighlightColor", false)
                        })
                    })
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_app")
                    })
                })
            })

            // 展开卡图文主体：左侧图标 = 当前识别到的**类别图标**（animIconInfo 保持
            // 已验证的 type 3，src 换类别键；此前误改 type 0 双态属未验证变体），
            // 标题 + 逐笔明细（多行，readability 的核心载体）
            put("iconTextInfo", JSONObject().apply {
                put("animIconInfo", JSONObject().apply {
                    put("type", 3)
                    put("src", "miui.focus.pic_category")
                })
                put("title", title)
                put("content", detailContent)
                put("colorTitle", "#111111")
                put("colorTitleDark", "#FFFFFF")
                put("colorContent", "#666666")
                put("colorContentDark", "#AAAAAA")
            })

            // 底栏：笔数摘要 + 动作按钮（按钮点击走 extras miui.focus.action_hint_v2）
            put("hintInfo", JSONObject().apply {
                put("type", 1)
                put("title", summary)
                put("colorTitle", "#000000")
                put("colorTitleDark", "#FFFFFF")
                put("colorContent", "#666666")
                put("colorContentDark", "#AAAAAA")
                put("actionInfo", JSONObject().apply {
                    put("action", "miui.focus.action_hint_v2")
                    put("actionTitle", "查看")
                    put("actionTitleColor", "#FFFFFF")
                    put("actionTitleColorDark", "#FFFFFF")
                    put("actionBgColor", "#3482FF")
                    put("actionBgColorDark", "#3482FF")
                })
            })
        }
        return JSONObject().put("param_v2", paramV3).toString()
    }
}
