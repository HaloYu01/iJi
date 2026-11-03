package com.iji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.Radius
import com.iji.app.data.model.Transaction
import com.iji.app.data.model.TransactionType
import com.iji.app.data.repository.CategoryCatalog
import com.iji.app.ui.theme.IncomeGreen
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary
import java.time.LocalDate

/**
 * 共享账单行：圆形分类图标 + 一级/二级分类 + 时间/备注/标签 + 右侧金额（按类型带符号与配色）。
 * HomeScreen（外层包滑动/多选）与 CalendarScreen / AccountDetailScreen 共用，消除列表项重复渲染。
 *
 * - [leadingContent]：可选前置内容（如多选勾选框），由调用方控制。
 * - [subtitle]：副标题覆盖文本（CalendarScreen 传"账户"、AccountDetailScreen 传"分类"时启用）；
 *   为空时由本组件根据 [Transaction] 自动渲染「时间 · 备注 · 彩色标签」。
 * - [showAccountUnderAmount]：是否在金额下方显示账户（HomeScreen 需要，其余页不需要）。
 * - [amountOverrideCents]：覆盖右侧显示金额（组合付款在账户详情页只显示本账户分担额）。
 * - 若 [Transaction.excludeFromStats] 为 true，整体做弱化并标注「不计入」徽标，与正常账单区分。
 */
@Composable
fun TransactionRow(
    tx: Transaction,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    subtitle: String = "",
    /**
     * 分类标题的字重。默认 SemiBold（账本页 / 账户详情页的原样式）；
     * 日历页要求更轻一档，所以开放成参数。**只影响分类标题**，不影响金额、副行、标签胶囊。
     */
    titleFontWeight: FontWeight = FontWeight.SemiBold,
    showAccountUnderAmount: Boolean = false,
    amountOverrideCents: Long? = null,
    /** 退款溯源说明（由调用方算好传入，本组件不查原始账单） */
    refundNote: String = "",
    /**
     * 点附件缩略图的回调（参数是第几张）；**传了才渲染缩略图条**。
     *
     * 附件此前只能写不能看：记一笔里能加图，列表里却完全不显示，
     * 于是"我到底传没传这张票"只能再进一次编辑面板确认。
     * 默认 null 是为了不影响多选、搜索结果等不关心附件的调用点。
     */
    onOpenAttachments: ((Int) -> Unit)? = null,
) {
    val category = CategoryCatalog.of(tx.categoryId)
    val parent = category.parentId?.let { CategoryCatalog.of(it) }
    val mainName = category.name
    val subName = parent?.name // 一级分类名（无父级则为 null）
    val excluded = tx.excludeFromStats

    // 时间：仅持久化到「日期」（时分未落库），按今天/昨天/年内/跨年分级展示
    val today = LocalDate.now()
    val timeStr = when {
        tx.date == today -> "今天"
        tx.date == today.minusDays(1) -> "昨天"
        tx.date.year == today.year -> "${tx.date.monthValue}月${tx.date.dayOfMonth}日"
        else -> "${tx.date.year}年${tx.date.monthValue}月${tx.date.dayOfMonth}日"
    }

    Row(
        // 紧凑化：上下内边距 14 → 8dp（分组卡内行与行还靠分割线相连，视觉密度参照目标样式）
        modifier = modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.invoke()
        if (leadingContent != null) Spacer(Modifier.width(12.dp))

        CategoryIconImage(
            name = category.name,
            color = Color(category.color),
            size = 40.dp,
            // 传 id：用户改过名的图标也能按 id 找到（改名不再丢图标）
            categoryId = category.id,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // 标题：有父级时合并为「一级-二级」（对照目标样式的「医疗-住院」），无父级只显示自身
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (subName != null) "$subName-$mainName" else mainName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = titleFontWeight,
                    color = if (excluded) TextSecondary else MaterialTheme.colorScheme.onSurface,
                )
                if (excluded) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(MiuixPrimary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "不计入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MiuixPrimary,
                        )
                    }
                }
                if (tx.reimburse) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(IncomeGreen.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "报销",
                            style = MaterialTheme.typography.bodySmall,
                            color = IncomeGreen,
                        )
                    }
                }
                // 负数支出 = 退款：不标的话用户看到一行「+¥25.00 餐饮」会以为是记错了
                if (tx.type == TransactionType.EXPENSE && tx.amountCents < 0L) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(IncomeGreen.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "退款",
                            style = MaterialTheme.typography.bodySmall,
                            color = IncomeGreen,
                        )
                    }
                }
                // 半填转账（只有一侧账户）无法联动余额，标出来以便用户补全；
                // 两侧都空是「不带账户的转账」，属于合法状态，不加标记
                if (tx.type == TransactionType.TRANSFER &&
                    (tx.account.isBlank() != tx.transferToAccount.isBlank())
                ) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "待补全",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            // 退款溯源行：原账单显示「已退 ¥X · 净 ¥Y」，退款行显示「退款自 …」
            if (refundNote.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    refundNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            // 副行：调用方覆盖 / 否则 时间 · 备注 · #标签
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // 副行只保留「时间 · 备注」；标签移到下方胶囊行（对照目标样式）
                Text(
                    buildAnnotatedString {
                        append(timeStr)
                        if (tx.note.isNotBlank()) {
                            append(" · ")
                            append(tx.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 优惠 + 标签胶囊行：浅灰底、小圆角（有优惠/有标签才渲染，行高不为空撑）
            // 放在 if/else **外面**：首页 / 日历 / 账户详情三处列表都显示，样式统一
            val pills = buildList {
                if (tx.discountCents > 0L) add("优惠" + formatMoney(tx.discountCents))
                tx.tags.forEach { add("#$it") }
            }
            if (pills.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pills.forEach { pill ->
                        Text(
                            pill,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.dot))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            // 附件缩略图：有图才占位，进可点开看大图（由调用方提供查看器）
            if (onOpenAttachments != null) {
                AttachmentThumbs(attachments = tx.attachments, onOpen = onOpenAttachments)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            // 组合付款在「账户详情页」只该显示该账户分担的那一份，
            // 否则一笔 ¥100 的分摊在两个账户下都会显示 ¥100
            val shownCents = amountOverrideCents ?: tx.amountCents
            val amountColor = when (tx.type) {
                // 负数支出=退款（钱收回来）、负数收入=红字，配色跟着符号翻转，与 formatSignedMoney 一致
                TransactionType.EXPENSE -> if (shownCents < 0) IncomeGreen else MiuixPrimary
                TransactionType.INCOME -> if (shownCents < 0) MiuixPrimary else IncomeGreen
                TransactionType.TRANSFER -> TextSecondary
            }.let { if (excluded) TextSecondary else it }
            // 有优惠：同一行「划线原价（小、灰） + 实付（大、彩色）」，字号与颜色双重对比
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tx.discountCents > 0L) {
                    Text(
                        formatMoney(shownCents + tx.discountCents),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    // 走统一的带符号格式化：原先这里自己写了一遍 `"-" + formatMoney(...)`，
                    // 遇到负数金额会输出 `--¥25.00`
                    formatSignedMoney(tx.type, shownCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
            }
            if (showAccountUnderAmount && tx.account.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountIconImage(iconKey = tx.account, size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        // 组合付款：列出各账户分担额，而不是只显示首选账户
                        if (tx.splits.size > 1) {
                            tx.splits.joinToString(" + ") { split ->
                                "${split.account} ${formatMoney(split.amountCents)}"
                            }
                        } else {
                            tx.account
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (tx.attachments.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "📎 ${tx.attachments.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}
