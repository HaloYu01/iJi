// 1:1 拷贝自 NexioSchedule miuix/.../internal/CanvasShadow.kt（expect）+ CanvasShadow.android.kt（actual）。
// 本工程为纯 Android app，expect/actual 合并为单文件；skiko 分支上游留存，逻辑不涉及。

package com.iji.app.ui.components.menu

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * 在当前绘制范围里，按给定路径画一块**模糊填充阴影**。
 *
 * 与 PopupShadow 的「模糊圆角矩形描边」不同：这个模糊「任意路径填充」（形状来自
 * Shape.createOutline，可能是超椭圆等自定义 Path）。
 *
 * @param path        阴影形状（填充语义，不做描边）
 * @param color       阴影颜色（含 alpha）
 * @param blurRadius  模糊半径（px）
 */
fun DrawScope.drawBlurredPathShadow(
    path: Path,
    color: Color,
    blurRadius: Float,
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}
