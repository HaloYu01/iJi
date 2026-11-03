package com.iji.app.data.model

/**
 * 一条导入历史（`import_history` 表的领域模型）。
 *
 * 与数据库行一一对应，UI 只认这个类型，不直接碰 Entity。
 */
data class ImportRecord(
    val id: Long = 0L,
    val source: String = "",
    val fileName: String = "",
    val ledgerId: String = "",
    val okCount: Int = 0,
    val failedCount: Int = 0,
    val importedAtMillis: Long = 0L,
)
