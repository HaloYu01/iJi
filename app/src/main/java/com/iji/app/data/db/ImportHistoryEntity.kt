package com.iji.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 导入历史表：每完成一次批量导入（CSV / 备份恢复 / 截屏识别）写一行。
 *
 * 背景：`BillImportScreen` 的「导入记录」弹窗此前写死「暂无导入记录」——
 * 导入结果只弹一次 Toast，弹完即散，用户事后无法回答
 * 「这份账单到底导进去了没有、导的是哪一份、进了哪本账、有几条失败」。
 *
 * **只在「确实跑过一次导入」时写入**：文件读失败（Excel 二进制 / 编码问题）不记，
 * 那种情况用户已经拿到明确的 Toast，不该在历史里留下一条没有数字的空记录。
 */
@Entity(
    tableName = "import_history",
    indices = [Index("importedAtMillis")],
)
data class ImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 来源，如「微信账单」「支付宝账单」「通用 CSV」「备份恢复」「截屏批量导入」 */
    val source: String = "",
    /** 文件名（可为空：截屏导入没有文件；系统选择器也可能给不出名字） */
    val fileName: String = "",
    /** 导入到的账本（截屏/备份恢复按各笔自身账本时留当前账本） */
    val ledgerId: String = "",
    val okCount: Int = 0,
    val failedCount: Int = 0,
    val importedAtMillis: Long = 0L,
    /** 本批写入的账单 id（逗号分隔）；撤销后置空，payload 仍留着供重新导入 */
    val recordIds: String = "",
    /** 账单快照（gzip + Base64 的备份 JSON） */
    val payload: String = "",
    /** 本批是否已撤销 */
    val undone: Boolean = false,
)
