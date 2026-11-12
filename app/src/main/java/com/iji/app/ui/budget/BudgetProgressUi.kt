package com.iji.app.ui.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iji.app.data.model.BudgetAlertLevel
import com.iji.app.data.model.BudgetProgress
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「接近超支」的琥珀色。
 *
 * Miuix 稳定版没有 warning/alert 语义色（官方红色状态角色是 error），
 * 因此中间态用应用自有色，不伪造框架 token。
 *
 * internal：首页概览卡的状态底色与进度条填充共用同一琥珀，保证同一状态一个色。
 */
internal val NearLimitColor = Color(0xFFFF9F0A)

/** 按当前级别取进度条颜色（MiuixTheme.colorScheme 是 @Composable，故本函数也需 @Composable） */
@Composable
private fun progressColor(progress: BudgetProgress): Color = when (progress.level) {
    BudgetAlertLevel.OVER_LIMIT -> MiuixTheme.colorScheme.error
    BudgetAlertLevel.NEAR_LIMIT -> NearLimitColor
    null -> MiuixTheme.colorScheme.primary
}

/**
 * 预算进度条：已用比例 + 按级别变色（正常=主色 / 接近超支=琥珀 / 已超支=红）。
 *
 * 纯展示组件，无状态、无依赖，可直接放进首页卡片、预算列表行或详情页。
 * 进度变化走临界阻尼弹簧：改预算、记一笔后平滑走到新位置，而不是跳变。
 *
 * @param trackColor / fillColor 显式覆盖轨道与填充色；缺省用主题色。
 *   首页概览卡是实色状态卡（内容全白），传白色系覆盖，避免主题轨道色在卡上发灰。
 */
@Composable
fun BudgetProgressBar(
    progress: BudgetProgress,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color? = null,
    fillColor: Color? = null,
) {
    // 超支时 percent 可能 >100，进度条最多铺满，颜色已表达超支状态
    val fraction = (progress.percent / 100f).coerceIn(0f, 1f)
    // 首次组合直接落在当前值（animateFloatAsState 的 initial 即 target），不会从 0 重放；
    // 只有同一组合实例内 target 变化（改预算/新账单落库）才触发动画
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 1f, stiffness = 300f),
        label = "budgetProgressFill",
    )
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor ?: MiuixTheme.colorScheme.secondaryContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .fillMaxHeight()
                .clip(shape)
                .background(fillColor ?: progressColor(progress)),
        )
    }
}
