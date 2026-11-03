package com.iji.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.resume

/**
 * Shizuku 的可用性状态 —— 界面按它决定给什么按钮、说什么话。
 *
 * 状态之间是**严格递进**的：没装 → 没运行 → 没授权 → 可用。
 * 每个状态都必须能给出一个用户真正能做的动作，不允许「点了没反应」。
 */
enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    UNSUPPORTED,
    NO_PERMISSION,
    READY,
}

/**
 * Shizuku 桥：查状态、要授权、以 shell 身份截一帧屏。
 *
 * 与「无障碍截屏」（[com.iji.app.accessibility.PaymentNotifyAccessibilityService]）的分工：
 * - 无障碍：系统直接给一帧，但要求用户去系统设置里开服务，且取图有频率节流；
 * - Shizuku：用户装一次 Shizuku 并授权即可，`screencap` 随时能截，
 *   代价是**必须装 Shizuku**、且同样只能截「当前可见的那一屏」。
 *
 * 两条路都不做后台偷拍：截图只发生在用户主动点按钮之后。
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"
    private const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    /** 小米推送框架：MIUI/HyperOS 上焦点通知（超级岛）要经它中转 */
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"

    /** Android ConnectivityManager 的隐藏常量：OEM 自定义防火墙链 */
    private const val FIREWALL_CHAIN_OEM_DENY = 9

    /** uid 防火墙规则值：0 = 默认（跟随链开关），2 = 拒绝 */
    private const val FIREWALL_RULE_DEFAULT = 0
    private const val FIREWALL_RULE_DENY = 2

    /** 防火墙规则下发失败重试：部分系统首次调用会失败（对齐 Halcyon 的重试策略） */
    private const val FIREWALL_MAX_RETRIES = 2
    private const val FIREWALL_RETRY_DELAY_MS = 500L

    /**
     * 断网窗口保持时长（真机实测口径 100ms）：窗口内白名单校验被跳过、通知直接上岛；
     * 过长（实测 1.5s）反而整条被吞。 Island 布局无效（如 baseInfo 实验期）也会被吞，
     * 排查时先确认布局有效再怀疑窗口。
     */
    private const val XMSF_BYPASS_WINDOW_MS = 100L

    /** bypass 窗口串行化：并发发送时两个窗口不能交错（A 先恢复了，B 还没发出去） */
    private val xmsfWindowLock = Any()

    /** XMSF 当前是否被本进程切断（恢复失败时保持 true，下个窗口先补恢复） */
    @Volatile
    private var xmsfCutByUs = false

    /** 授权请求码：只用于区分回调，Shizuku 不校验具体值 */
    private const val REQUEST_CODE = 0x494A

    @Volatile
    private var shell: IShizukuShell? = null

    fun state(context: Context): ShizukuState {
        if (!isInstalled(context)) return ShizukuState.NOT_INSTALLED
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return ShizukuState.NOT_RUNNING
        // UserService 是 Shizuku 11 才有的能力，更老的版本借不到 shell
        val supported = runCatching { Shizuku.getVersion() >= 11 && !Shizuku.isPreV11() }.getOrDefault(false)
        if (!supported) return ShizukuState.UNSUPPORTED
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) ShizukuState.READY else ShizukuState.NO_PERMISSION
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** 弹 Shizuku 的授权框；结果不在这里等，界面回前台时重新查状态即可 */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "请求 Shizuku 授权失败", it) }
    }

    /** 打开 Shizuku 管理器（未启动时由用户在那边点「启动」） */
    fun openManager(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
            ?: Intent("moe.shizuku.manager.MANAGE_APPLICATIONS").setPackage(MANAGER_PACKAGE)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /**
     * 截当前一屏。未授权 / 绑定失败 / 解码失败都返回 null，由调用方给出人话提示。
     */
    suspend fun captureScreen(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val service = awaitService(context) ?: return@withContext null
        runCatching {
            // 管道里的 PNG 读完即 EOF；AutoCloseInputStream 负责关闭 Binder 传过来的 fd
            ParcelFileDescriptor.AutoCloseInputStream(service.screencap()).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }.onFailure { Log.w(TAG, "Shizuku 截屏失败", it) }.getOrNull()
    }

    /** 以 shell 身份跑一条命令（如 `cmd statusbar collapse`）；失败返回 null */
    suspend fun exec(context: Context, command: String): String? = withContext(Dispatchers.IO) {
        val service = awaitService(context) ?: return@withContext null
        runCatching { service.exec(command) }.onFailure { Log.w(TAG, "命令失败：$command", it) }.getOrNull()
    }

    /**
     * 以 shell 权限临时切断/恢复小米推送框架（XMSF）的网络。
     *
     * MIUI/HyperOS 会把第三方应用的焦点通知（超级岛）送云端白名单校验，过不了就被
     * 静默吞掉；在 XMSF 断网的窗口内本地 notify，系统会跳过校验直接上岛。做法来自
     * NexioSchedule 的 XMSF bypass；Halcyon 在 HyperOS 3 真机上用同一机制投送超级岛
     * 歌词，证明它在 OS3 上仍然有效。
     *
     * 历代 Android 上防火墙接口的名字/签名/可反射性都变过（Android 16 起隐藏方法
     * 被移出反射列表），所以按 Halcyon 的兜底顺序逐个尝试：connectivity 服务的
     * 现代方法 → network_management 服务 → [HiddenApiBypass] 绕过 hidden-API 检查，
     * 外层再重试一次。
     *
     * @return 是否成功改了规则；未授权 / 无 XMSF（非小米）返回 false，调用方照常发送
     */
    fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        if (state(context) != ShizukuState.READY) return false
        val uid = runCatching { context.packageManager.getPackageUid(XMSF_PACKAGE, 0) }
            .onFailure { Log.i(TAG, "XMSF 未安装（非小米或已卸载）") }
            .getOrNull() ?: return false
        repeat(FIREWALL_MAX_RETRIES) { attempt ->
            val applied = runCatching { applyXmsfFirewallRule(uid, enabled) }
                .onFailure { Log.w(TAG, "XMSF 防火墙规则下发失败（第 ${attempt + 1} 次）", it) }
                .getOrDefault(false)
            if (applied) return true
            if (attempt + 1 < FIREWALL_MAX_RETRIES) Thread.sleep(FIREWALL_RETRY_DELAY_MS)
        }
        return false
    }

    /**
     * 在 XMSF 断网窗口内执行 [block]（一般为 notify）。
     *
     * 真机实测：窗口内白名单校验被跳过、焦点通知直接上岛；直发（不断网）时校验
     * 在线执行、未过白名单会回落成普通通知。整个「断网 → 执行 → 保持
     * [XMSF_BYPASS_WINDOW_MS] → 恢复」流程全局串行，避免两个窗口交错；
     * 恢复失败用 [xmsfCutByUs] 记状态，下个窗口先补恢复。
     *
     * @return block 的返回值 + bypass 是否真正生效（Shizuku 未就绪时为 false，照常执行）
     */
    fun <T> withXmsfBypass(context: Context, block: () -> T): Pair<T, Boolean> {
        synchronized(xmsfWindowLock) {
            if (xmsfCutByUs) {
                // 上个窗口恢复失败：先补恢复再重新断，避免在残缺状态上叠规则
                setXmsfNetworkingEnabled(context, enabled = true)
                xmsfCutByUs = false
            }
            val cut = setXmsfNetworkingEnabled(context, enabled = false)
            xmsfCutByUs = cut
            val result: T = try {
                block()
            } finally {
                if (cut) {
                    Thread.sleep(XMSF_BYPASS_WINDOW_MS)
                    // 恢复必须成功，否则小米推送会一直断网；失败记状态，下个窗口补恢复
                    if (setXmsfNetworkingEnabled(context, enabled = true)) {
                        xmsfCutByUs = false
                    } else {
                        Log.e(TAG, "XMSF 网络恢复失败，小米推送可能受影响，请手动检查")
                    }
                }
            }
            return result to cut
        }
    }

    /** 防火墙后端：系统服务名 + 对应 AIDL Stub 类名（新/老两个后端，逐个试） */
    private data class FirewallBackend(val service: String, val stub: String, val label: String)

    private val firewallBackends = listOf(
        FirewallBackend("connectivity", "android.net.IConnectivityManager\$Stub", "ConnectivityService"),
        FirewallBackend("network_management", "android.os.INetworkManagementService\$Stub", "NetworkManagementService"),
    )

    private fun applyXmsfFirewallRule(uid: Int, enabled: Boolean): Boolean {
        val failures = mutableListOf<String>()
        for (backend in firewallBackends) {
            val applied = runCatching {
                val binder = SystemServiceHelper.getSystemService(backend.service)
                    ?: error("${backend.service} binder 为空")
                val manager = Class.forName(backend.stub)
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, ShizukuBinderWrapper(binder))
                    ?: error("${backend.label} 代理为空")
                applyRule(manager, uid, enabled)
                true
            }.onFailure { failures += "${backend.label}: ${it.message}" }.getOrDefault(false)
            if (applied) return true
        }
        error("无可用的防火墙后端：${failures.joinToString(" | ")}")
    }

    /**
     * 在一个防火墙服务代理上落规则。开链只在断网方向需要；恢复时只把 uid 规则还原为
     * 默认、不动链开关（对齐 Halcyon）。方法名与签名在历代系统上不一致，逐个尝试。
     */
    private fun applyRule(manager: Any, uid: Int, enabled: Boolean) {
        if (!enabled) {
            runCatching { invokeHidden(manager, listOf("setFirewallChainEnabled"), FIREWALL_CHAIN_OEM_DENY, true) }
        }
        val rule = if (enabled) FIREWALL_RULE_DEFAULT else FIREWALL_RULE_DENY
        val attempted = runCatching {
            invokeHidden(manager, listOf("setUidFirewallRule", "setFirewallUidRule"), FIREWALL_CHAIN_OEM_DENY, uid, rule)
        }.isSuccess
        if (attempted) return
        // 批量签名（个别版本只有复数形式）：(chain, intArrayOf(uid), intArrayOf(rule))
        runCatching {
            invokeHidden(
                manager,
                listOf("setUidFirewallRules", "setFirewallUidRules"),
                FIREWALL_CHAIN_OEM_DENY,
                intArrayOf(uid),
                intArrayOf(rule),
            )
        }.getOrElse { error("setUidFirewallRule 系列方法全部不可用：$it") }
    }

    /**
     * 按方法名 + 参数个数匹配并调用：先走普通反射；查不到方法时（Android 16+ 把
     * 隐藏方法移出了反射列表）用 [HiddenApiBypass] 绕过 hidden-API 检查再试。
     * Boolean/int 参数按目标签名自动适配。
     */
    private fun invokeHidden(target: Any, names: List<String>, vararg args: Any) {
        val methods = target.javaClass.methods.filter { it.name in names && it.parameterCount == args.size }
        if (methods.isNotEmpty()) {
            var lastFailure: Throwable? = null
            for (method in methods) {
                try {
                    method.isAccessible = true
                    val adapted = Array(args.size) { i -> adaptArg(args[i], method.parameterTypes[i]) }
                    method.invoke(target, *adapted)
                    return
                } catch (e: InvocationTargetException) {
                    lastFailure = e.targetException ?: e
                } catch (e: Throwable) {
                    lastFailure = e
                }
            }
            throw lastFailure ?: IllegalStateException("方法调用失败：$names")
        }
        // hidden-API 反射被过滤：逐个名字用 HiddenApiBypass 直调
        var lastFailure: Throwable? = null
        for (name in names) {
            try {
                HiddenApiBypass.invoke(target.javaClass, target, name, *args)
                return
            } catch (e: Throwable) {
                lastFailure = e
            }
        }
        throw lastFailure ?: NoSuchMethodException(names.joinToString("/"))
    }

    private fun adaptArg(value: Any, expected: Class<*>): Any = when {
        expected == Int::class.javaPrimitiveType -> when (value) {
            is Boolean -> if (value) 1 else 0
            is Number -> value.toInt()
            else -> error("参数无法转为 int：$value")
        }
        expected == Boolean::class.javaPrimitiveType -> when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> error("参数无法转为 boolean：$value")
        }
        expected == IntArray::class.java && value is IntArray -> value
        expected.isInstance(value) -> value
        else -> error("参数类型不匹配：期望 ${expected.name}")
    }

    /**
     * 拿到（必要时绑定）UserService。
     *
     * 绑定成功后就一直复用，不再每次截图都重新拉起进程；Shizuku 断开时由
     * [ServiceConnection.onServiceDisconnected] 清空引用，下次自然重建。
     */
    private suspend fun awaitService(context: Context, timeoutMillis: Long = 8_000): IShizukuShell? {
        shell?.let { return it }
        if (state(context) != ShizukuState.READY) return null

        val app = context.applicationContext
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val bound = IShizukuShell.Stub.asInterface(service)
                        shell = bound
                        if (cont.isActive) cont.resume(bound)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        shell = null
                    }
                }
                val args = Shizuku.UserServiceArgs(
                    ComponentName(app.packageName, ShizukuShellService::class.java.name),
                )
                    .daemon(false)
                    .processNameSuffix("shizuku")
                    .debuggable(false)
                    .version(1)

                runCatching { Shizuku.bindUserService(args, connection) }
                    .onFailure {
                        Log.w(TAG, "绑定 UserService 失败", it)
                        if (cont.isActive) cont.resume(null)
                    }
                cont.invokeOnCancellation {
                    runCatching { Shizuku.unbindUserService(args, connection, true) }
                    shell = null
                }
            }
        }
    }
}
