package com.iji.app.ui.assets

import com.iji.app.data.model.Account
import com.iji.app.data.model.AccountType

/**
 * 账户列表筛选维度。
 *
 * - [ASSET]：非信用类账户（现金 / 银行 / 网银 / 充值 / 投资 / 其他）；
 * - [LIABILITY]：信用类账户（信用卡 / 花呗等），含溢缴（余额为正）的情况，
 *   否则溢缴账户会在「资产」「负债」两个筛选里同时消失；
 * - [EXCLUDED]：被排除在净资产之外（includeInNetWorth = false）的账户。
 */
internal enum class AccountFilter(val label: String) {
    ALL("全部"),
    ASSET("资产"),
    LIABILITY("负债"),
    EXCLUDED("未计入"),
    ;

    fun matches(account: Account, type: AccountType): Boolean = when (this) {
        ALL -> true
        ASSET -> type != AccountType.CREDIT
        LIABILITY -> type == AccountType.CREDIT
        EXCLUDED -> !account.includeInNetWorth
    }
}
