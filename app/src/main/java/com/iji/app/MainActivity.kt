package com.iji.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.iji.app.budget.NotificationHelper
import com.iji.app.ui.IjiApp
import com.iji.app.ui.NavIntentHolder
import com.iji.app.ui.theme.IjiTheme

import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 必须保持继承 [ComponentActivity]，**不要**改成 `FragmentActivity` / `AppCompatActivity`：
 *
 * ① 应用锁的指纹认证已改用**平台** `android.hardware.biometrics.BiometricPrompt`
 *    （只需要 Activity 作 Context），不再要求 FragmentActivity；
 * ② 若继承 FragmentActivity，会被 `androidx.biometric:biometric:1.1.0` 带入的
 *    `androidx.fragment:fragment:1.2.5` 拦截 `startActivityForResult`，对 activity 1.9.3
 *    生成的 requestCode 抛 `IllegalArgumentException: Can only use lower 16 bits for requestCode`
 *    —— 选图片/选文件等一切 `rememberLauncherForActivityResult` 调用点全部崩溃（2026-10-02 实测）。
 */
class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // App 已在前台时通知点击走这里（singleTask，Activity 不重建），把路由转给 Compose 侧导航
        NavIntentHolder.post(intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
        handleOpenAddBill(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动即由通知唤起：把通知携带的目标路由交给 Compose 侧导航
        NavIntentHolder.post(intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
        handleOpenAddBill(intent)
        enableEdgeToEdge()
        // 刘海屏 / 挖孔屏：允许内容绘制到短边 cutout 区域，否则顶部会留出一条黑边。
        // 「避开挖孔」由 Compose 侧用 displayCutout inset 处理（见 IjiTopBar 的安全区计算）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        // 初始化主题状态（统一走 SettingsRepository / DataStore，保留既有值）
        com.iji.app.ui.theme.ThemeState.themeMode =
            com.iji.app.data.repository.SettingsRepository.getString(this, "theme_mode", "system")

        setContent {
            // Miuix 弹层（OverlayDialog / OverlayBottomSheet）内部 NavigationBackHandler 需要
            // LocalNavigationEventDispatcherOwner，缺失时弹层显示即崩溃；此处在根创建 root owner。
            //
            // 注意：这个 root dispatcher 是**孤立**的（parent = null，也未挂 NavigationEventInput），
            // 它收不到任何系统返回事件，只用于满足 Miuix 内部的非空校验。activity 1.9.3 不提供
            // NavigationEventDispatcherOwner 集成（activity 1.12+ 才有），不要指望给它挂 input 接
            // 系统事件——那会与 OnBackPressedDispatcher 的系统回调形成双重注册竞争。
            // 真正的返回（含预测性返回跟手）走 OnBackPressedDispatcher 统一管线，
            // 见 GlassDialog 的 PredictiveBackHandler。
            val navigationEventOwner = rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationEventOwner) {
                IjiTheme {
                    val view = LocalView.current
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val statusBarColor = MiuixTheme.colorScheme.surface
                    val navigationBarColor = MiuixTheme.colorScheme.surface

                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        // 沉浸式：状态栏保持透明（enableEdgeToEdge 已设），让顶栏的 AGSL 模糊
                        // 延伸到状态栏区域。这里若刷成 surface 实色，顶部会出现一条与页面割裂的色块。
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = navigationBarColor.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MiuixTheme.colorScheme.background,
                    ) {
                        IjiApp()
                    }
                }
            }
        }
    }

    /**
     * 处理「自动记账」通知动作唤起的记账面板。
     *
     * 优先取内存中的 [PendingBillHolder]（进程存活时 Receiver 已写入）；
     * 进程被杀后 Receiver 只来得及落盘，这里再从 [PaymentBillStore] 取一次并消费。
     */
    private fun handleOpenAddBill(intent: android.content.Intent) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_ADD_BILL, false)) return
        if (com.iji.app.ui.PendingBillHolder.pending.value == null) {
            com.iji.app.accessibility.PaymentBillStore.consume(this)?.let {
                com.iji.app.ui.PendingBillHolder.post(it)
            }
        }
    }

    companion object {
        /** 「自动记账」通知动作：拉起 App 并打开记账面板 */
        const val EXTRA_OPEN_ADD_BILL = "open_add_bill"
    }
}
