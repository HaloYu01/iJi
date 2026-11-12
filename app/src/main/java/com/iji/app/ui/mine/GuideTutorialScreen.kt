// 引导教程：App 使用说明文档页（原「新手教程」弹窗已废弃，改为一页读到底的详细文档）。
// 结构与 ChangelogScreen 同款：MineSubPage + 分节卡片，从上往下读一遍就是全部。
// 页底提供「重新配置引导」入口：跳回开局配置向导重配（不重启 App、不触发应用锁），
// 完成后覆盖现有配置并回主页。本页不是自动记账教程，也不是分步向导本身。

package com.iji.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.iji.app.ui.components.IjiPrimaryButton
import com.iji.app.ui.components.ijiTopBarContentPadding
import com.iji.app.ui.theme.LocalDimens
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.Radius
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/** 一节使用说明：节标题 + 若干要点 */
@Immutable
private data class GuideSection(
    val title: String,
    val points: List<String>,
)

/** 详细使用说明内容：按新用户第一次接触功能的顺序排列 */
private val guideSections: List<GuideSection> = listOf(
    GuideSection(
        title = "基础记账",
        points = listOf(
            "点底栏的「＋」打记账面板：选分类、填金额、保存，一笔就记完了。",
            "记账面板里的「再记」会在保存后保留分类与账户，连续记多笔最快。",
            "账单支持多分类拆分（组合付款）与备注、标签，长按账单行可进入只读详情。",
            "首页按天分组展示，点右上角月份可翻历史月份；顶部搜索与筛选能按分类、标签、报销、对账状态过滤。",
        ),
    ),
    GuideSection(
        title = "AI 与自动记账",
        points = listOf(
            "「我的 → AI 记账」可以一句话记账：把消费描述发给 AI，解析成账单后确认落账。",
            "识屏记账：开启 Shizuku / 无障碍授权后，从微信、支付宝等页面截屏即可批量识别账单。",
            "自动记账（通知识别）：开启通知权限后，支付通知会自动生成待确认账单，不会直接入账。",
            "识别规则可以在「提示词与规则」里调整：AI 提示词与本地关键词规则都支持增删改。",
        ),
    ),
    GuideSection(
        title = "账单导入",
        points = listOf(
            "「我的 → 数据与备份 → 账单导入」支持微信、支付宝导出的 CSV 账单文件。",
            "截屏批量导入：把账单页截屏发给识别引擎，一次能进几十笔。",
            "导入前可以逐条预览、去重（同一笔在导入窗口期内不会重复入账）。",
        ),
    ),
    GuideSection(
        title = "资产管理",
        points = listOf(
            "资产页展示所有账户的余额与净资产；账户分现金、储蓄卡、电子钱包、信用等类型。",
            "每个账户都有独立流水（账户详情页可导出 CSV），转账会在两个账户间各记一笔。",
            "借入 / 借出单独管理：借款记录与普通支出分开，还清后可标记结清。",
            "点状菜单提供编辑、转账、隐藏（不再统计）与删除。",
        ),
    ),
    GuideSection(
        title = "预算与统计",
        points = listOf(
            "预算在首页顶部卡片设置：月度总预算 + 分类预算，超额会在账单里高亮提醒。",
            "统计页支持按周 / 月 / 年三个维度查看收支构成，点任意分类可下钻到具体账单。",
            "报销与对账：账单可标记报销与对账状态，资产页顶部入口可以批量处理。",
        ),
    ),
    GuideSection(
        title = "数据安全与备份",
        points = listOf(
            "所有数据只存在这台手机上；「数据与备份」提供本地备份文件与网盘（WebDAV）备份。",
            "建议开启自动备份：换机或重装时用备份文件一键恢复全部分类、账户与账单。",
            "应用锁在「我的 → 密码保护」：4 位 PIN + 可选指纹，只在冷启动时验证一次。",
        ),
    ),
    GuideSection(
        title = "个性化",
        points = listOf(
            "「偏好设置」里可以调主题（浅色 / 深色 / 跟随系统）、字号与首页折叠习惯。",
            "分类、标签、账本都支持增删改与排序；分类图标可在分类管理里逐个设置。",
            "「降低动态效果」会关闭液态玻璃的模糊与折射，老设备建议开启。",
        ),
    ),
)

/**
 * 引导教程页（原「新手教程」）：App 使用说明文档 + 页底「重新配置引导」入口。
 *
 * 内容为一页读到底的详细说明；重配入口是真实跳转（开局配置向导的重配模式），不是假入口。
 */
@Composable
fun GuideTutorialScreen(
    onBack: () -> Unit,
    onReconfigure: () -> Unit,
) {
    MineSubPage(title = "引导教程", onBack = onBack) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = ijiTopBarContentPadding(),
                start = LocalDimens.current.screenPadding,
                end = LocalDimens.current.screenPadding,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap),
        ) {
            // 开篇一句话：这份文档是什么
            item(key = "intro") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = Radius.xxl,
                    colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        "这里汇总了 i记 的全部使用说明，从记账到备份一次读完。" +
                            "每一条都对应真实存在的功能入口，可以在「我的」里逐个找到。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            items(items = guideSections, key = { it.title }) { section ->
                GuideSectionCard(section)
            }

            item(key = "reconfigure") {
                IjiPrimaryButton(
                    text = "重新配置引导",
                    onClick = onReconfigure,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 一节使用说明卡片：节标题 + 逐条要点 */
@Composable
private fun GuideSectionCard(section: GuideSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = Radius.xxl,
        colors = CardDefaults.defaultColors(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MiuixPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                section.points.forEach { point ->
                    Text(
                        "· " + point,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
