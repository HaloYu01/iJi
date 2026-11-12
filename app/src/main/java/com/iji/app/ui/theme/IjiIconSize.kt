package com.iji.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局图标尺寸规范（2026-11-04 定稿）。
 *
 * 只约束**图标本体**，不改变容器、点击区与行高；同一类场景必须引用同一个 token，
 * 新增图标不要再写裸 dp。光学尺寸以「顶栏动作 22dp」为基准：
 *
 * | 场景 | token | 尺寸 |
 * |---|---|---|
 * | 行内/胶囊内的账户、分类小图标 | [Inline] | 16dp |
 * | 顶栏动作、行尾 chevron | [Action] | 22dp |
 * | 设置行彩色方块 / 方块内图标 | [BadgeBox] / [BadgeGlyph] | 28 / 20dp |
 * | 高密度图表行、多选 chip | [Compact] | 32dp |
 * | 统计分类构成行 | [ChartRow] | 30dp |
 * | 管理列表行首分类/账户图标 | [ListRow] | 36dp |
 * | 首页账单行 | [BillRow] | 40dp |
 * | 分类选择器宫格 / 二级分类 | [Grid] / [GridChild] | 40 / 36dp |
 * | 资产页账户头图 | [Hero] | 44dp |
 * | 分类编辑页图标预览 | [EditPreview] | 52dp |
 *
 * 说明：`CategoryIconImage` 内部再取容器 56% 画线条，`AccountIconImage` 的 logo 直接铺满；
 * 因此账户/分类的 token 是**圆形底尺寸**，不是线条尺寸。
 */
object IjiIconSize {
    val Inline = 16.dp
    val Action = 22.dp

    val BadgeBox = 28.dp
    val BadgeGlyph = 20.dp

    val Compact = 32.dp
    val ChartRow = 30.dp
    val ListRow = 36.dp

    val BillRow = 40.dp
    val Grid = 40.dp
    val GridChild = 36.dp

    val Hero = 44.dp
    val EditPreview = 52.dp
}
