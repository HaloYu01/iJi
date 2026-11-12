package com.iji.app.data.model

/**
 * 一次批量导入的批次（`import_history` 表的领域模型）。
 *
 * 除了给用户看的统计信息，还留了两份「可恢复」数据：
 * - [recordIds] 本批真实写入的账单 id，用于「撤销本次导入」；
 * - [payload] 这批账单的快照（[ImportPayload] 压缩后的备份 JSON），用于「重新导入」。
 * 撤销只把 [undone] 置 true 并清空 [recordIds]，历史行与快照都保留，所以还能重新导入。
 */
data class ImportBatch(
    val id: Long = 0L,
    val importedAtMillis: Long = 0L,
    val source: String = "",
    val count: Int = 0,
    val recordIds: List<Long> = emptyList(),
    val fileName: String = "",
    val ledgerId: String = "",
    val payload: String = "",
    val undone: Boolean = false,
) {
    val canUndo: Boolean get() = !undone && recordIds.isNotEmpty()
    val canReimport: Boolean get() = payload.isNotEmpty()
}
