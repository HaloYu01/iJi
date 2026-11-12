package com.iji.app.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片 → 账单的统一入口：「图片识别用 AI」开着先问视觉模型，失败回落本机 OCR；关着只跑本机 OCR。
 * 模型按 `BILL_IMAGE` 提示词一张图只出一笔，本地 OCR 可按行切出多笔。
 * 后台无感识屏不走这里，永远只跑本地。
 */
object ImageBillRecognizer {

    private const val HINT_BOTH_FAILED = "模型识别失败，已回落本地识别，仍未认出账单"
    private const val HINT_LOCAL_ONLY = "本机未认出金额；可在「AI 记账」页开启「图片识别用 AI」后重试"

    data class DraftsOutcome(val drafts: List<BillDraft> = emptyList(), val hint: String? = null)

    data class BillOutcome(
        val bill: AiBookkeepingService.BillResult? = null,
        val hint: String? = null,
    )

    private suspend fun askModel(context: Context, bmp: Bitmap): AiBookkeepingService.BillResult? {
        val base64 = withContext(Dispatchers.Default) { encodeImageForModel(bmp) }
        return runCatching { AiBookkeepingService.parseBillFromImage(base64, context) }.getOrNull()
    }

    suspend fun recognizeDrafts(
        context: Context,
        bmp: Bitmap,
        sourcePkg: String? = null,
    ): DraftsOutcome {
        if (AiConfig.visionUsesAi(context)) {
            val bill = askModel(context, bmp)?.takeIf { it.amountCents > 0 }
            if (bill != null) return DraftsOutcome(listOf(bill.toImportDraft()))
            val local = ScreenshotBillParser.recognizeDrafts(bmp, sourcePkg = sourcePkg)
            return if (local.isEmpty()) DraftsOutcome(hint = HINT_BOTH_FAILED) else DraftsOutcome(local)
        }
        return DraftsOutcome(ScreenshotBillParser.recognizeDrafts(bmp, sourcePkg = sourcePkg))
    }

    suspend fun recognizeSingle(context: Context, bmp: Bitmap): BillOutcome {
        if (AiConfig.visionUsesAi(context)) {
            val bill = askModel(context, bmp)
            if (bill != null) return BillOutcome(bill)
            val local = LocalOcrEngine.recognize(bmp)
            return if (local != null) BillOutcome(local) else BillOutcome(hint = HINT_BOTH_FAILED)
        }
        val local = LocalOcrEngine.recognize(bmp)
        return if (local != null) BillOutcome(local) else BillOutcome(hint = HINT_LOCAL_ONLY)
    }
}
