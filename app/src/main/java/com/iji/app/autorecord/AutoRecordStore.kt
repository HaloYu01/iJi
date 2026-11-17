package com.iji.app.autorecord

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.iji.app.accessibility.AutoBookkeepingPrefs
import com.iji.app.accessibility.CapturePreferences
import com.iji.app.ai.BillDraft
import com.iji.app.budget.NotificationHelper
import com.iji.app.data.db.DbProvider

import com.iji.app.data.repository.LedgerSelection
import com.iji.app.data.repository.SettingsRepository
import com.iji.app.data.model.TransactionType
import com.iji.app.ui.Routes
import com.iji.app.ui.overlay.BillOverlay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全自动记账的去重、最近记录与结果分发。
 *
 * **与半自动完全独立**：不使用截屏草稿状态（`ScreenCaptureFlow._drafts`）、不写识屏进度岛、
 * 不依赖截屏来源。识别结果的落点只有两个，都保持「先确认后入账」：
 * 1. 悬浮窗确认卡（主路径，用户此刻就在账单页上）；
 * 2. 悬浮窗不可用时发一条通知提醒（点开回到本页，最近识别里能看到这一笔）。
 */
object AutoRecordStore {

    private const val TAG = "AutoRecordStore"
    private const val KEY_RECENT = "auto_record_recent"
    private const val KEY_LAST_HIT = "auto_record_last_hit"
    private const val KEY_UNMATCHED = "auto_record_unmatched_asset"
    private const val MAX_RECENT = 6
    /** 同一笔账单的重复判定窗口：页面内容变化事件会连发多次，钱迹用 120s */
    private const val DEDUP_WINDOW_MS = 120_000L
    /** 跨进程的重复窗口：杀进程重开后 60s 内同指纹不再重复记（钱迹的 DB 60s 等价物） */
    private const val PERSIST_WINDOW_MS = 60_000L

    /** key → 最近命中时刻（elapsedRealtime）；LRU 上限防止长时间运行后无限增长 */
    private val hits = object : LinkedHashMap<String, Long>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > 64
    }

    data class Entry(
        val at: Long,
        val platform: String,
        val amountCents: Long,
        val income: Boolean,
        val status: String,
        /** 匹配到的本机账户；空表示没匹配上（确认卡里由用户手选） */
        val account: String = "",
        /**
         * 识别指纹（[AutoBillDraft.dedupKey]）。
         *
         * 用户在确认卡上记完账后要靠它把对应那条记录改成「已确认」；
         * 旧数据没有这一段，回写时会退化为「同平台同金额且未确认」匹配。
         */
        val key: String = "",
    )

    /** 重复判定的结果：放行 / 标注后仍确认 / 直接丢弃 */
    enum class DupDecision { NONE, SUSPECT, DROP }

    /**
     * 投递用的应用级作用域。
     *
     * **不绑定无障碍服务**：服务会被系统反复销毁重建，`onDestroy` 取消自己的
     * scope 之后，排在旧实例上的投递会「命中却不弹窗」（真机日志 `scopeActive=false`）。
     * 识别结果属于应用级事件，不该因为服务重建而丢失。
     */
    private val deliverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 展示级去重窗口：同一笔账单在这段时间内只弹一次确认卡。
     *
     * 支付宝有 fast(160ms) + main(450ms) 两条独立通道，页面在 160ms 就渲染完成时
     * 两条通道都会命中并各弹一次（真机日志里能看到连续两次 `shown=true`，
     * 第二次还带着「疑似重复」标注）。查重策略为「标注」时不会拦截，必须在展示层再挡一道。
     */
    private const val SHOWN_DEDUP_MS = 8_000L

    /** dedupKey → 最近一次真正弹窗的时刻（LRU 上限 32，防止长期运行堆积） */
    private val lastShownAt = LinkedHashMap<String, Long>(16, 0.75f, true)

    /**
     * 异步投递一笔识别结果：查重 → 悬浮窗 / 通知。
     *
     * 从识别线程直接调用即可，内部负责切主线程弹窗；整条链路留痕，
     * 真机上「识别到了却没弹窗」能一眼分清是被去重拦了、查重异常，还是悬浮窗加不上。
     */
    fun deliverDetected(context: Context, draft: AutoBillDraft) {
        val ctx = context.applicationContext
        val dedupKey = draft.dedupKey
        val now = SystemClock.elapsedRealtime()
        synchronized(lastShownAt) {
            val prev = lastShownAt[dedupKey]
            if (prev != null && now - prev < SHOWN_DEDUP_MS) {
                AutoDiag.log(
                    ctx,
                    "deliver",
                    "同笔 ${SHOWN_DEDUP_MS / 1000}s 内已弹过窗，跳过 amount=${draft.amountCents}",
                )
                deliverScope.launch { BillOverlay.dismissLoading() }
                return
            }
        }
        AutoDiag.log(ctx, "deliver", "入队 ${draft.platform}/${draft.pageType} amount=${draft.amountCents}")
        deliverScope.launch {
            BillOverlay.showLoading(ctx)
            // 整条投递链路兜底：任一环节异常都不能让协程静默死掉（否则识别到也不弹窗）
            try {
                val decision = checkDuplicate(ctx, draft)
                AutoDiag.log(ctx, "deliver", "查重 ${decision.name}")
                val shown = when (decision) {
                    DupDecision.DROP -> false
                    DupDecision.SUSPECT -> deliver(ctx, draft, suspect = true)
                    DupDecision.NONE -> deliver(ctx, draft)
                }
                // 只有真的弹出来才记：被去重/DROP 的情况不该占用展示窗口
                if (shown) {
                    synchronized(lastShownAt) {
                        lastShownAt[dedupKey] = SystemClock.elapsedRealtime()
                        if (lastShownAt.size > 32) lastShownAt.remove(lastShownAt.keys.first())
                    }
                }
                AutoDiag.log(ctx, "deliver", "完成 shown=$shown")
            } catch (t: Throwable) {
                AutoDiag.log(
                    ctx,
                    "deliver",
                    "投递异常 ${t.javaClass.simpleName}: ${t.message}",
                )
                Log.w(TAG, "投递异常", t)
            } finally {
                BillOverlay.dismissLoading()
            }
        }
    }

    /**
     * 三层重复检测（钱迹同款思路，但更保守）：
     * 1. 内存 120s：同一页面反复触发（内容变化事件连发）；
     * 2. 持久化 60s：跨进程重启仍生效，避免杀进程后重开又记一遍；
     * 3. 同日同额同类型：只做「疑似」标注，**绝不阻断**——同一天两笔同额消费很正常。
     *
     * 策略三档由用户选：直接拦 / 确认时标注 / 不检测。默认标注。
     */
    suspend fun checkDuplicate(context: Context, draft: AutoBillDraft): DupDecision {
        val policy = AutoBookkeepingPrefs.dupPolicy(context)
        if (policy == AutoBookkeepingPrefs.DUP_OFF) return DupDecision.NONE

        val key = draft.dedupKey
        val now = SystemClock.elapsedRealtime()
        val memHit = synchronized(hits) { hits[key]?.let { now - it <= DEDUP_WINDOW_MS } == true }
        val persisted = lastHit(context)
        val persistedHit = persisted != null &&
            persisted.first == key &&
            System.currentTimeMillis() - persisted.second <= PERSIST_WINDOW_MS
        if (memHit || persistedHit) {
            // 去重命中必须留痕：真机上「识别到了却不弹窗」绝大多数就是这里，
            // 没有日志会被误判成「没识别到」
            val by = when {
                memHit && persistedHit -> "内存+持久化"
                memHit -> "内存"
                else -> "持久化"
            }
            return if (policy == AutoBookkeepingPrefs.DUP_BLOCK) {
                AutoDiag.log(
                    context,
                    "dup",
                    "${draft.platform}/${draft.pageType} amount=${draft.amountCents} 命中${by}去重，丢弃",
                )
                Log.d(TAG, "重复识别已拦截：$key")
                DupDecision.DROP
            } else {
                AutoDiag.log(
                    context,
                    "dup",
                    "${draft.platform}/${draft.pageType} amount=${draft.amountCents} 命中${by}去重，标为疑似",
                )
                DupDecision.SUSPECT
            }
        }

        // 同日同额只在确认时轻声提醒，不参与拦截
        val epochDay = draftEpochDay(draft)
        val sameDay = withContext(Dispatchers.IO) {
            runCatching {
                DbProvider.database.transactionDao()
                    .countSameAmountOnDay(draft.amountCents, draft.type.name, epochDay)
            }.getOrDefault(0)
        }
        return if (sameDay > 0) DupDecision.SUSPECT else DupDecision.NONE
    }

    /** 记下这次命中：确认卡展示后调用，避免「只是看了一眼就占掉窗口」 */
    fun markHit(context: Context, draft: AutoBillDraft) {
        val key = draft.dedupKey
        synchronized(hits) { hits[key] = SystemClock.elapsedRealtime() }
        SettingsRepository.setString(
            context,
            KEY_LAST_HIT,
            "$key|${System.currentTimeMillis()}",
        )
    }

    private fun lastHit(context: Context): Pair<String, Long>? {
        val raw = SettingsRepository.getString(context, KEY_LAST_HIT, "")
        val at = raw.lastIndexOf('|')
        if (at <= 0) return null
        val time = raw.substring(at + 1).toLongOrNull() ?: return null
        return raw.substring(0, at) to time
    }

    private fun draftEpochDay(draft: AutoBillDraft): Long =
        if (draft.timeMillis > 0) {
            runCatching {
                Instant.ofEpochMilli(draft.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    .toEpochDay()
            }.getOrDefault(LocalDate.now().toEpochDay())
        } else {
            LocalDate.now().toEpochDay()
        }

    /**
     * 最近记录的行内分隔符。
     *
     * 不能用 `|`：指纹 `dedupKey` 本身就是 `|` 连接的
     * （`包名|页面|类型|时间|金额`），同分隔符会把指纹拆散，
     * 写入后第 7 段只剩包名，`markConfirmed` 永远匹配不上——
     * 真机日志就是 `[confirm] 没有匹配的待确认记录 key=...4170`。
     * 旧数据是 `|` 分隔且没有指纹段，读时按旧规则解析、指纹视为空。
     */
    private const val SEP = "\u0001"

    /** 解析一条最近记录；旧格式（`|` 分隔）丢弃被拆散的指纹残段 */
    private fun parseEntryLine(raw: String): MutableList<String>? {
        if (raw.isBlank()) return null
        if (raw.contains(SEP)) return raw.split(SEP).toMutableList()
        val parts = raw.split('|').toMutableList()
        if (parts.size > 6) parts.subList(6, parts.size).clear()
        return parts
    }

    fun mark(context: Context, draft: AutoBillDraft, status: String, account: String = "") {
        val key = draft.dedupKey
        val existing = SettingsRepository.getString(context, KEY_RECENT, "")
            .split('\n')
            .filter { it.isNotBlank() }
        // 已确认过的同一笔不再回写「待确认」：用户点保存后如果还停在页面上，
        // 展示去重（8s）一过就会再次投递，把「已确认」覆盖回「待确认」
        val confirmedSame = existing.any { raw ->
            val parts = parseEntryLine(raw) ?: return@any false
            parts.getOrNull(6).orEmpty() == key && parts.getOrNull(4).orEmpty().startsWith("已确认")
        }
        if (confirmedSame) {
            AutoDiag.log(
                context,
                "mark",
                "${draft.platform} amount=${draft.amountCents} 已确认，跳过重复写入",
            )
            return
        }
        val entry = Entry(
            at = System.currentTimeMillis(),
            platform = draft.platform,
            amountCents = draft.amountCents,
            income = draft.type == TransactionType.INCOME,
            status = status,
            account = account,
            key = key,
        )
        val line = listOf(
            entry.at, entry.platform, entry.amountCents, if (entry.income) 1 else 0, entry.status,
            entry.account, entry.key,
        ).joinToString(SEP)
        // 同一笔只保留一条：页面反复进出会重复投递（8s 展示去重之后，120s 内仍会再来一次
        // 并带上「疑似重复」标注），不去重的话列表里会堆出好几条一模一样的记录
        val income = draft.type == TransactionType.INCOME
        val withoutSame = existing.filter { raw ->
            val parts = parseEntryLine(raw) ?: return@filter true
            val storedKey = parts.getOrNull(6).orEmpty()
            if (storedKey.isNotEmpty()) {
                storedKey != key
            } else {
                // 旧版本没有指纹段：把「同平台 + 同金额 + 同为待确认」的历史残留一并清掉，
                // 否则每次重新识别都会在那几条旧记录之外再加一条，越堆越多。
                // 已确认的历史记录不在此列，不会被误删。
                val sameLegacy = parts.size >= 5 &&
                    parts[1] == draft.platform &&
                    parts[2].toLongOrNull() == draft.amountCents &&
                    (parts[3] == "1") == income &&
                    parts[4].startsWith("待确认")
                !sameLegacy
            }
        }
        val next = (listOf(line) + withoutSame).take(MAX_RECENT)
        SettingsRepository.setString(context, KEY_RECENT, next.joinToString("\n"))
        AutoDiag.log(context, "mark", "${draft.platform} amount=${draft.amountCents} status=$status")
    }

    fun recent(context: Context): List<Entry> =
        SettingsRepository.getString(context, KEY_RECENT, "")
            .split('\n')
            .mapNotNull { raw ->
                val parts = parseEntryLine(raw) ?: return@mapNotNull null
                if (parts.size < 5) return@mapNotNull null
                val at = parts[0].toLongOrNull() ?: return@mapNotNull null
                Entry(
                    at = at,
                    platform = parts[1],
                    amountCents = parts[2].toLongOrNull() ?: 0L,
                    income = parts[3] == "1",
                    status = parts[4],
                    account = parts.getOrNull(5).orEmpty(),
                    key = parts.getOrNull(6).orEmpty(),
                )
            }

    /**
     * 把最近一条「待确认」的识别记录改成「已确认」。
     *
     * 确认动作发生在悬浮窗里（`BillOverlay` → `QuickBillWriter` 落库），那条路径不知道
     * 这份最近记录，所以由调用方在**保存成功**后回调过来。没有这一步，列表会一直显示
     * 「待确认 · 疑似重复」，看起来像没记上。
     */
    fun markConfirmed(context: Context, draft: AutoBillDraft) {
        val key = draft.dedupKey
        val lines = SettingsRepository.getString(context, KEY_RECENT, "")
            .split('\n')
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            AutoDiag.log(context, "confirm", "最近记录为空，无需标记")
            return
        }
        var changed = false
        val next = lines.map { raw ->
            if (changed) return@map raw
            val parts = parseEntryLine(raw) ?: return@map raw
            if (parts.size < 5) return@map raw
            val status = parts[4]
            if (!status.startsWith("待确认")) return@map raw
            val entryKey = parts.getOrNull(6).orEmpty()
            // 优先按指纹精确匹配；旧数据没有指纹时退化为「同平台同金额且未确认」的第一条
            val hit = if (entryKey.isNotEmpty()) {
                entryKey == key
            } else {
                parts[1] == draft.platform &&
                    parts[2].toLongOrNull() == draft.amountCents &&
                    // 方向也要一致：同额的一收一支不能被串改成已确认
                    (parts[3] == "1") == (draft.type == TransactionType.INCOME)
            }
            if (!hit) return@map raw
            changed = true
            parts.toMutableList().also { it[4] = "已确认" }.joinToString("|")
        }
        if (changed) {
            SettingsRepository.setString(context, KEY_RECENT, next.joinToString("\n"))
            AutoDiag.log(context, "confirm", "已标记为已确认 key=${key.takeLast(24)}")
        } else {
            AutoDiag.log(context, "confirm", "没有匹配的待确认记录 key=${key.takeLast(24)}")
        }
    }

    /**
     * 把识别结果交给用户确认。
     *
     * 落库前先过一遍**独立的账户匹配层**（[AutoAssetMatcher]）：页面上的「零钱 / 余额宝 /
     * 招商银行储蓄卡(1234)」不是本机账户名，直接写库会让余额联动静默失效。
     * 匹配不到时账户留空（确认卡会请用户手选），绝不硬塞一个近似名字。
     *
     * @return 是否已经以某种方式呈现（悬浮窗或通知）；false 表示两样都不行，
     *   此时只留下最近记录，绝不静默入账。
     */
    suspend fun deliver(context: Context, draft: AutoBillDraft, suspect: Boolean = false): Boolean {
        val assets = AutoAssetSource.loadAssets()
        val mappings = AutoAssetSource.loadMappings(context)
        // 收入看入账渠道，支出/转账看出账渠道；
        // 出账缺失时退回入账文案：云闪付转账详情只有「转账到」，
        // 没有「付款方式」，outAsset 为空会导致账户永远匹配不上
        val rawAsset = when {
            draft.type == TransactionType.INCOME && draft.inAsset.isNotBlank() -> draft.inAsset
            draft.outAsset.isNotBlank() -> draft.outAsset
            else -> draft.inAsset
        }
        val match = AutoAssetMatcher.match(
            raw = rawAsset,
            platform = draft.platform,
            assets = assets,
            mappings = mappings,
        )
        Log.d(TAG, "账户匹配 ${draft.platform}/${draft.pageType} → ${match.trace}")
        AutoDiag.log(context, "asset", "${draft.platform}/${draft.pageType} ${match.trace}")
        if (!match.matched && rawAsset.isNotBlank()) {
            // 记下这次没匹配上的原文，页面提供一键指定账户（指定后成为 110 级映射）
            SettingsRepository.setString(context, KEY_UNMATCHED, rawAsset)
        }
        val categoryId = AutoCategorySuggester.suggest(context, draft)
        val bill = draft.toBillDraft(match.assetName.orEmpty(), categoryId)
        val overlayEnabled = CapturePreferences.overlayConfirmEnabled(context)
        val canShow = BillOverlay.canShow(context)
        val shown = if (overlayEnabled) {
            runCatching {
                BillOverlay.show(
                    context,
                    listOf(bill),
                    AutoBookkeepingPrefs.effectiveLedger(
                        setting = AutoBookkeepingPrefs.ledger(context),
                        current = LedgerSelection.current.value,
                    ),
                    detailed = CapturePreferences.overlayStyle(context) ==
                        CapturePreferences.OVERLAY_DETAILED,
                    // 卡片里真的写库成功 → 把「最近识别」里这条改成「已确认」。
                    // 不接这个回调，列表会一直停在「待确认」，看起来像没记上。
                    onSaved = { markConfirmed(context, draft) },
                )
            }.getOrElse {
                // 悬浮窗加不上（MIUI「后台弹出界面」没开时会走到这里），
                // 原因必须落到诊断日志，否则真机只能看到「没弹窗」
                AutoDiag.log(context, "overlay", "show failed: ${it.javaClass.simpleName} ${it.message}")
                Log.w(TAG, "悬浮窗展示失败", it)
                false
            }
        } else {
            AutoDiag.log(context, "overlay", "skip enabled=false canShow=$canShow")
            false
        }
        val account = match.assetName.orEmpty()
        AutoDiag.log(context, "deliver", "shown=$shown amount=${draft.amountCents} asset=$account")
        val status = if (suspect) "待确认 · 疑似重复" else "待确认"
        if (shown) {
            markHit(context, draft)
            mark(context, draft, status, account)
            return true
        }
        val kind = if (draft.type == TransactionType.INCOME) "收入" else "支出"
        val prefix = if (suspect) "疑似重复 · " else ""
        // 用户开了悬浮窗权限还看不到卡片时，通知里必须说清原因，
        // 否则他只会以为「识别坏了」——MIUI/HyperOS 的「后台弹出界面」是独立开关
        val overlayReason = when {
            !overlayEnabled -> "悬浮窗确认已关闭"
            else -> "悬浮窗未能显示，请检查悬浮窗权限与系统「后台弹出界面」"
        }
        val body = "$prefix$kind ${money(draft.amountCents)} · ${draft.pageType}（$overlayReason），点开应用核对"
        val noticed = runCatching {
            NotificationHelper.sendScreenshotNotice(
                context = context,
                title = "自动记账 · ${draft.platform}",
                body = body,
                route = Routes.AUTO_BOOKKEEPING,
            )
        }.getOrDefault(false)
        markHit(context, draft)
        mark(
            context,
            draft,
            if (noticed) "已提醒" else "仅记录",
            account,
        )
        return noticed
    }

    private fun AutoBillDraft.toBillDraft(matchedAsset: String, categoryId: String): BillDraft {
        val date = if (timeMillis > 0) {
            runCatching {
                Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrDefault(LocalDate.now())
        } else {
            LocalDate.now()
        }
        return BillDraft(
            type = type,
            // 分类由 AutoCategorySuggester 给出（历史 → 用户规则 → 关键词 → AI → 默认）
            categoryId = categoryId,
            amountCents = amountCents,
            note = (remark.ifBlank { merchant }).take(50),
            // 匹配不到就留空：确认卡的账户选择器会补默认，用户改过的名字不会被覆盖
            account = matchedAsset,
            date = date,
            discountCents = feeCents,
            // 商户单列保存：同商户历史分类复用、商家统计都依赖它
            merchant = merchant,
        )
    }

    /** 上次没匹配上的账户原文：页面「账户匹配」卡据此提供一键指定 */
    fun lastUnmatched(context: Context): String =
        SettingsRepository.getString(context, KEY_UNMATCHED, "")

    /** 用户指定后落成一条映射，下次同平台直接命中（110 级，优先级最高） */
    fun saveMapping(context: Context, raw: String, assetName: String, platform: String) {
        if (raw.isBlank() || assetName.isBlank()) return
        AutoAssetSource.addMapping(
            context,
            AutoAssetMapping(match = raw, assetName = assetName, platform = platform),
        )
        SettingsRepository.setString(context, KEY_UNMATCHED, "")
        AutoDiag.log(context, "mapping", "用户映射 $raw → $assetName ($platform)")
    }

    fun money(cents: Long): String = String.format(Locale.CHINA, "¥%.2f", cents / 100.0)
}
