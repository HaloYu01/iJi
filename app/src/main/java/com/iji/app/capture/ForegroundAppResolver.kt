package com.iji.app.capture

import android.content.Context
import com.iji.app.accessibility.PaymentNotifyAccessibilityService
import com.iji.app.shizuku.ShizukuBridge
import com.iji.app.shizuku.ShizukuState

/**
 * 截屏记账时的「前台应用」识别：只取**包名**，不读界面控件、不解析屏幕文字。
 *
 * 为什么两条来源：Shizuku 可用时 `dumpsys` 给的是此刻真实焦点窗口（实时、准）；
 * 只开了无障碍的用户则由 [PaymentNotifyAccessibilityService] 缓存窗口切换事件里的包名。
 * 两条都拿不到就返回 null —— 前台应用只是辅助信息，识别必须照常进行，绝不因此报错或阻塞。
 *
 * 隐私：包名只在本次识屏的内存里用（选账户 + 通知里的一句来源），不落库、不上传；
 * 只在用户主动触发识屏时读取，不做后台常驻监听。
 */
object ForegroundAppResolver {

    /** dumpsys 输出里标识焦点窗口的字段，按可信度从高到低兜底 */
    private val FOCUS_KEYS = listOf(
        "mCurrentFocus",
        "mFocusedApp",
        "topResumedActivity",
        "mResumedActivity",
        "Top activity",
    )

    /** `com.xxx.yyy/.SomeActivity` 里的包名部分；只认至少两段的合法包名 */
    private val RE_COMPONENT = Regex("""([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/""")

    /**
     * 一次拿齐几种写法：`dumpsys` 字段跨 Android 版本 / 厂商不统一，
     * 末尾再补官方的 `cmd activity get-top-activity`（老系统不支持时输出报错，解析时会被过滤掉）。
     */
    private const val FOCUS_CMD =
        "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'; " +
            "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'; " +
            "cmd activity get-top-activity"

    /**
     * 系统窗口 / 桌面 / 本 App 都不可能是账单来源。
     *
     * 下拉栏收起瞬间焦点会短暂落到 SystemUI；各厂商 Launcher 名字里普遍带
     * `launcher` 或以 `.home` 结尾（如 HyperOS 的 `com.miui.home`）。用特征过滤，
     * 不维护一份需要跟着 ROM 更新的机型清单。
     */
    fun isBillAppCandidate(pkg: String?, selfPkg: String): Boolean {
        if (pkg.isNullOrBlank() || pkg == selfPkg) return false
        val p = pkg.lowercase()
        if (p == "android" || p.startsWith("com.android.")) return false
        if (p.contains("systemui") || p.contains("settings") ||
            p.contains("launcher") || p.endsWith(".home")
        ) return false
        return true
    }

    /** 从 shell 命令输出里挑当前焦点窗口包名；解析不出来返回 null */
    fun parseDumpsys(output: String?, selfPkg: String): String? {
        if (output.isNullOrBlank()) return null
        val lines = output.lineSequence().toList()
        for (key in FOCUS_KEYS) {
            val line = lines.firstOrNull { it.contains(key) } ?: continue
            val pkg = RE_COMPONENT.find(line)?.groupValues?.getOrNull(1)
            if (isBillAppCandidate(pkg, selfPkg)) return pkg
        }
        return null
    }

    /**
     * 包名 → 账户名（与内置账户同名，可直接落到 `Transaction.account`）。
     * 只映射能确认是「账单列表页」的几家；认不出返回 null，交给 OCR 文本推断。
     */
    fun accountFor(pkg: String?): String? = when {
        pkg == null -> null
        pkg == "com.eg.android.AlipayGphone" -> "支付宝"
        pkg == "com.tencent.mm" -> "微信"
        pkg.startsWith("com.unionpay") -> "云闪付"
        else -> null
    }

    /**
     * 取当前前台包名，失败一律降级为 null。
     *
     * Shizuku 优先（实时 `dumpsys`）；不可用时退回无障碍的窗口事件缓存。
     * 调用方应紧挨着截图调用，避免倒计时期间用户又切了 App 造成来源错配。
     */
    suspend fun current(context: Context): String? {
        val selfPkg = context.packageName
        if (ShizukuBridge.state(context) == ShizukuState.READY) {
            parseDumpsys(ShizukuBridge.exec(context, FOCUS_CMD), selfPkg)?.let { return it }
        }
        return PaymentNotifyAccessibilityService.foregroundPackage()
            .takeIf { isBillAppCandidate(it, selfPkg) }
    }
}
