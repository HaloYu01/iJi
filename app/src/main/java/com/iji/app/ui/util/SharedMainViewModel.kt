package com.iji.app.ui.util

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iji.app.MainViewModel

/**
 * 全 App 共享一份 [MainViewModel]，以 Activity 作为 ViewModelStoreOwner。
 *
 * 页面里直接写 `viewModel()` 时，owner 是当前 NavBackStackEntry：四个主 tab 会各建一份，
 * 每份 init 都要做播种检查、订阅账本/分类/周期规则等 Room 流，切 tab 的首帧因此被拖慢。
 * 弹层/对话框不在 NavHost 内，默认 owner 本来就是 Activity；统一到这里后，
 * 页面、对话框、记一笔面板拿到的是同一实例，避免状态分叉。
 */
@Composable
fun sharedMainViewModel(): MainViewModel {
    val owner = LocalContext.current.findComponentActivity()
    return if (owner != null) viewModel(viewModelStoreOwner = owner) else viewModel()
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is ComponentActivity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}
