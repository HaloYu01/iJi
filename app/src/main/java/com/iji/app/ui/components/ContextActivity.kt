package com.iji.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 从 Compose 的 `LocalContext` 里取回宿主 Activity。
 *
 * 用途：截屏识别前要把应用退到后台（否则截到的就是自己），`moveTaskToBack` 只能在 Activity 上调用。
 * 组合里的 Context 可能是 Activity 本身，也可能是被包装过的 ContextWrapper，所以逐层解包。
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 结束整个任务栈并退出；singleTask 下 finish() 不够彻底（隐私政策不同意 / 撤回同意共用）。 */
fun Context.exitApp() {
    findActivity()?.finishAffinity() ?: kotlin.system.exitProcess(0)
}
