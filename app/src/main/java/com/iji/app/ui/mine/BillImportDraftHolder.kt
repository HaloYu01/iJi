package com.iji.app.ui.mine

import com.iji.app.ai.BillDraft
import com.iji.app.data.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一次解析结果（CSV 账单文件或截图 OCR），跨页交给「导入预览」页。
 *
 * 与 `ScreenCaptureFlow.drafts` 同构：解析出的账单列表可能很大，只放内存、不进路由参数，
 * 也不进 savedStateHandle；进程被杀就当作没解析过，让用户重新选一次文件。
 */
data class ImportDraft(
    val fileName: String,
    val source: String,
    val ledgerId: String,
    /** 整批统一账户；空 = 沿用文件里每笔自带的账户 */
    val account: String = "",
    val transactions: List<Transaction> = emptyList(),
    val ocrDrafts: List<BillDraft> = emptyList(),
    val ocrAccount: String = "",
    val failedCount: Int = 0,
) {
    val isOcr: Boolean get() = ocrDrafts.isNotEmpty()
    val count: Int get() = if (isOcr) ocrDrafts.size else transactions.size
}

object BillImportDraftHolder {
    private val _draft = MutableStateFlow<ImportDraft?>(null)
    val draft: StateFlow<ImportDraft?> = _draft.asStateFlow()

    fun post(value: ImportDraft) {
        _draft.value = value
    }

    /** 预览页改账本 / 改账户 / 剔除某一笔都走这里，保证只有一份状态来源 */
    fun update(transform: (ImportDraft) -> ImportDraft) {
        _draft.value = _draft.value?.let(transform)
    }

    fun clear() {
        _draft.value = null
    }
}
