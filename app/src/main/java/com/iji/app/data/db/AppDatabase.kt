package com.iji.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        LedgerEntity::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
        BudgetAlertEntity::class,
        ImportHistoryEntity::class,
    ],
    version = 24,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetAlertDao(): BudgetAlertDao
    abstract fun importHistoryDao(): ImportHistoryDao
}

/** v1 → v2：新增 accounts 资产账户表（保留既有账单数据） */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `accounts` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `subName` TEXT NOT NULL,
                `iconKey` TEXT NOT NULL,
                `balanceCents` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

/** v2 → v3：transactions 表新增 ledgerId 列（默认"默认账本"） */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN ledgerId TEXT NOT NULL DEFAULT '默认账本'")
    }
}

/** v3 → v4：accounts 表新增 includeInNetWorth 列（默认 true） */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN includeInNetWorth INTEGER NOT NULL DEFAULT 1")
    }
}

/** v4 → v5：transactions / accounts 加常用查询索引 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateMillis` ON `transactions` (`dateMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId` ON `transactions` (`ledgerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)")
    }
}

/** v5 → v6：新增 ledgers / recurring_rules 两张表 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ledgers` (
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`name`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_rules` (
                `id` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `amountCents` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                `account` TEXT NOT NULL,
                `freq` TEXT NOT NULL,
                `nextRunDate` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v6 → v7：accounts 表新增 category 列（账户分类，默认空串）。
 *
 * **不回填存量数据**：分类推断依赖 AccountType 的中文关键词与英文 iconKey 映射，
 * SQL 里做不了；统一由 `AccountType.ofAccount(category, iconKey, name)` 在读取时兜底。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN category TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v7 → v8：accounts 表新增 hidden 列（默认不隐藏）。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v8 → v9：transactions 表新增 excludeFromStats 列（默认计入收支/预算）。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN excludeFromStats INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v9 → v10：transactions 表新增 4 列
 * - excludeFromBudget（不计入预算）
 * - reimburse（报销标记）
 * - discountCents（优惠金额，分）
 * - attachments（附件 URI，逗号分隔）
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN excludeFromBudget INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN reimburse INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN discountCents INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v10 → v11：新增 budgets（预算）与 budget_alerts（已触发告警）两张表。
 *
 * budget_alerts 上的**唯一索引** (budgetId, periodKey, level) 是「同一预算、同一周期、
 * 同一级别只提醒一次」的数据库级保障（插入用 IGNORE，冲突返回 -1）。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `ledgerId` TEXT NOT NULL,
                `periodType` TEXT NOT NULL,
                `amountCents` INTEGER NOT NULL,
                `alertThresholdPercent` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                `startEpochDay` INTEGER NOT NULL,
                `endEpochDay` INTEGER NOT NULL,
                `createdAtMillis` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_ledgerId` ON `budgets` (`ledgerId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budget_alerts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `budgetId` INTEGER NOT NULL,
                `periodKey` TEXT NOT NULL,
                `level` TEXT NOT NULL,
                `spentCents` INTEGER NOT NULL,
                `progressPercent` INTEGER NOT NULL,
                `triggeredAtMillis` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_alerts_budgetId_periodKey_level`
            ON `budget_alerts` (`budgetId`, `periodKey`, `level`)
            """.trimIndent(),
        )
    }
}

/**
 * v11 → v12：budgets 表新增 reminderEnabled 列（是否发送提醒通知）。
 *
 * 与 enabled 分离：enabled 控制预算是否参与检查/展示，
 * reminderEnabled 只控制是否打扰用户。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v12 → v13：补齐「可信度 + 防丢钱」相关字段。
 *
 * - transactions 新增 merchant（商家/收款方，与 note 分离以便聚合）、
 *   reconciled（已对账）、reimburseState（垫付/借款状态）、counterparty（对方，用于按人归集核销）
 * - budgets 新增 carryOver（是否结转）、rolloverCents（上期结转额度）
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN merchant TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE transactions ADD COLUMN reconciled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN reimburseState TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN counterparty TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE budgets ADD COLUMN carryOver INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE budgets ADD COLUMN rolloverCents INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v13 → v14：recurring_rules 新增 ledgerId（规则所属账本）。
 *
 * 此前周期规则不绑定账本，生成账单时用「当时打开的账本」，导致房租被记进工作账本。
 * 存量规则无从判断原属账本，统一落到「默认账本」，用户可在规则里改。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recurring_rules ADD COLUMN ledgerId TEXT NOT NULL DEFAULT '默认账本'")
    }
}

/**
 * 14 → 15：账单表补`transferToAccount`（转账的转入账户）。
 *
 * 背景：转账此前只有`account` 一个账户字段，导致「转出账户扣钱、转入账户加钱」无法表达，
 * `adjustAccountBalance` 对 TRANSFER 直接 return，转账记了也不动任何余额。
 * 存量转账统一落到空串（= 未指定转入账户），保持原行为、不臆造数据；
 * 用户编辑这类旧账单时补选转入账户即可。
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN transferToAccount TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 15 → 16：账单表补`splits`（组合付款的拆分明细）。
 *
 * 背景：此前一笔账只能记在一个账户上，`account` 是单值字符串，
 * 信用卡 + 储蓄卡分摊、家庭共同支付这类场景无法表达。
 * 存量账单统一落到空串（= 单一账户），行为与改造前完全一致。
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN splits TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 16 → 17：账单表补`refundOfId`（退款溯源的原账单 id）。
 *
 * 背景：退款按负数支出记账（账户收钱、统计冲减、报销应收自动抵扣），
 * 但列表上只能看到一行孤零零的负数，看不出它退的是哪一笔消费。
 * 该字段**不参与任何金额聚合**，只用于展示「这笔消费已退多少、净成本多少」。
 * 存量统一落 0（= 无关联）。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN refundOfId INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 17 → 18：ledgers 表补`createdAt`（账本创建时间）。
 *
 * 背景：账本列表原先按 `sortOrder, name` 排序，而新建账本统一写
 * `sortOrder = Int.MAX_VALUE`，导致用户新建的账本之间只能按名称排——
 * 滚轮 / 下拉里看到的顺序与用户的创建先后无关，看起来是乱的。
 * 存量行用 rowid 回填：SQLite 行的 rowid 随插入递增，且改名不改变它，
 * 因此回填结果就是用户当初的创建顺序（默认账本 → 旅行 → 工作 → …）。
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ledgers ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE ledgers SET createdAt = rowid")
    }
}

/**
 * 18 → 19：新增 `import_history`（导入历史）表。
 *
 * 背景：导入结果此前只弹一次 Toast，导入页的「导入记录」入口写死「暂无导入记录」——
 * 用户事后无法确认某份账单导没导进去、进了哪本账、有几条失败。
 * 新表只记「跑过的每一次导入」，不动任何既有账单数据。
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `import_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `source` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                `ledgerId` TEXT NOT NULL,
                `okCount` INTEGER NOT NULL,
                `failedCount` INTEGER NOT NULL,
                `importedAtMillis` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_import_history_importedAtMillis` ON `import_history` (`importedAtMillis`)",
        )
    }
}

/**
 * 19 → 20：账单表新增对方资料两列（联系方式 / 单位）。
 *
 * 借入借出需要记清「欠谁、怎么联系、哪个单位」，此前只有一个 `counterparty` 名字字段。
 * 两列都可空串兜底，老数据不受影响。
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN counterpartyContact TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE transactions ADD COLUMN counterpartyOrg TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 20 → 21：账本表新增币种列。
 *
 * 多币种只做「账本级币种 + 展示」：金额仍以分存、不换算汇率，避免凭空发明跨币种合计。
 * 老账本一律回填 CNY，行为与升级前完全一致。
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ledgers ADD COLUMN currency TEXT NOT NULL DEFAULT 'CNY'")
    }
}

/**
 * 22 → 23：账单新增 `minuteOfDay`（当天的第几分钟，-1 = 只记到天）。
 *
 * 独立成列而不是把时分揉进 `dateMillis`：后者全库存的是 epochDay，
 * 月份区间查询直接拿它当边界，混入时分会让所有区间口径错位。
 * 存量行一律 -1（未指定时间），与升级前行为一致。
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN minuteOfDay INTEGER NOT NULL DEFAULT -1")
    }
}

/**
 * 23 → 24：导入历史升级为可撤销 / 可重放的批次。
 *
 * 老记录没有账单 id 与快照，三列都给空值：历史仍能看，但「撤销 / 重新导入」对它不可用，
 * 不能假装能恢复一批我们根本没记下 id 的账单。
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE import_history ADD COLUMN recordIds TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE import_history ADD COLUMN payload TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE import_history ADD COLUMN undone INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 21 → 22：收入侧「其他」的 categoryId 由 `other` 改为 `other-income`。
 *
 * 旧版支出与收入共用 `other`，索引按 id 去重时收入那条被丢弃，
 * 导致"改收入的其他"实际改到支出那条。分类现在全部可编辑，这个 id 冲突必须先拆掉。
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE transactions SET categoryId = 'other-income' WHERE categoryId = 'other' AND type = 'INCOME'")
        db.execSQL("UPDATE recurring_rules SET categoryId = 'other-income' WHERE categoryId = 'other' AND type = 'INCOME'")
    }
}

/** 数据库持有者：由 Application 初始化 */
object DbProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun init(context: Context) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "iji.db",
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)
                        // 降级（如旧版 APK 覆盖新版）时破坏性重建，避免抛 IllegalStateException 直接崩溃；
                        // 正常升级仍依赖上面的显式迁移，不会触发。
                        // dropAllTables = true：连非 Room 管理的表也一并删干净（官方推荐值），
                        // 否则换过表名的旧库可能残留无人使用的数据。
                        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                        .build()
                }
            }
        }
    }

    val database: AppDatabase
        get() = checkNotNull(instance) { "AppDatabase 尚未初始化" }
}
