package com.iji.app.data.model

/**
 * 币种：**账本级**配置，只影响金额的展示符号。
 *
 * 设计边界（很重要，别越过）：
 * - 金额仍以「分」（`amountCents`）存储，本类**不参与任何换算**，不存汇率；
 * - 一个账本一种币种，因此同一账本内的求和永远是同币种相加；
 * - 跨账本的金额**绝不合并成一个数**（100 USD + 100 CNY ≠ 200），
 *   界面上任何合计都只对「当前账本」取数。
 *
 * [decimals] 目前统一为 2（金额按「分」存）。日元 / 韩元没有分位，
 * 但强行区分会引入「存 1 分 = 0.01 日元」的怪值，故不做特例。
 */
data class Currency(
    /** ISO 4217 代码，如 CNY / USD；数据库里存的就是它 */
    val code: String,
    /** 展示符号，如 ¥ / $ / € */
    val symbol: String,
    /** 中文名，用于选择器 */
    val name: String,
) {
    companion object {
        val CNY = Currency("CNY", "¥", "人民币")
        val HKD = Currency("HKD", "HK$", "港币")
        val TWD = Currency("TWD", "NT$", "新台币")
        val USD = Currency("USD", "$", "美元")
        val EUR = Currency("EUR", "€", "欧元")
        val GBP = Currency("GBP", "£", "英镑")
        // 日元没有分位，但符号刻意与人民币区分（两个「¥」并列会看不出记的是哪国钱）
        val JPY = Currency("JPY", "JP¥", "日元")
        val KRW = Currency("KRW", "₩", "韩元")
        val SGD = Currency("SGD", "S$", "新加坡元")
        val MYR = Currency("MYR", "RM", "马来西亚林吉特")
        val THB = Currency("THB", "฿", "泰铢")
        val AUD = Currency("AUD", "A$", "澳元")
        val CAD = Currency("CAD", "C$", "加元")
        val RUB = Currency("RUB", "₽", "卢布")

        /** 全部可选币种（选择器用）；顺序 = 常用程度 */
        val all: List<Currency> = listOf(
            CNY, HKD, TWD, USD, EUR, GBP, JPY, KRW,
            SGD, MYR, THB, AUD, CAD, RUB,
        )

        /** 代码 → 币种；未知 / 空值一律回落到人民币（老数据只有 CNY） */
        fun of(code: String?): Currency =
            all.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: CNY
    }
}

/**
 * 当前**展示币种**（进程内共享）。
 *
 * 为什么是全局：金额格式化（`formatMoney`）散布在 20+ 个界面文件、上百个调用点，
 * 逐个透传币种既侵入又极易漏改。这里做成「环境级」状态，由 [com.iji.app.MainViewModel]
 * 在账本 / 币种变化时写入，格式化默认读它。
 *
 * 刻意**不依赖 Compose 运行时**：`FormattersTest` 用纯 JVM 直跑，
 * 一旦这里引入 `mutableStateOf`，单测会因缺 compose-runtime 直接 NoClassDefFoundError。
 * 代价是改币种后**同一屏内不会立刻刷新**——但币种只在「账本管理」里改，
 * 该页不显示金额，返回其他页时是重新组合，读到的已是新值。
 */
object CurrencyDisplay {

    @Volatile
    var current: Currency = Currency.CNY
        private set

    /** 按 ISO 代码切换（未知代码回落 CNY）；返回是否发生了变化 */
    fun set(code: String?): Boolean {
        val next = Currency.of(code)
        if (next == current) return false
        current = next
        return true
    }
}
