package com.iji.app.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iji.app.ui.theme.Radius
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * 导入相关三页共用的分组卡片：24dp 大圆角，行仍用 `IjiSettingsRow` 那套紧凑布局。
 *
 * 与 `IjiSettingsSection` 的差别只在圆角（那边是 20dp），这里按本次重构统一放大一档。
 */
@Composable
internal fun ImportSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(text = title, insideMargin = PaddingValues(bottom = 10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = Radius.sheet,
            colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
        ) {
            Column(content = content)
        }
    }
}
