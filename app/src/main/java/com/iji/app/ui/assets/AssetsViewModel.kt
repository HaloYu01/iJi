package com.iji.app.ui.assets

import androidx.lifecycle.ViewModel
import com.iji.app.MainViewModel
import com.iji.app.data.model.Account

/**
 * 资产页专属 ViewModel：本屏无局部 UI 状态，账户数据与增删/转账动作统一委托给 App 级 MainViewModel。
 */
class AssetsViewModel(private val app: MainViewModel) : ViewModel() {
    val accounts = app.accounts

    fun deleteAccount(account: Account) = app.deleteAccount(account)

    fun updateAccount(account: Account) = app.updateAccount(account)

    /** 更新账户（等待写库完成）—— 保存后立刻返回列表时用 */
    suspend fun updateAccountNow(account: Account) = app.updateAccountNow(account)

    fun hideAccount(account: Account) = app.hideAccount(account)

    fun showAccount(account: Account) = app.showAccount(account)

    fun reorderAccounts(orderedIds: List<Long>) = app.reorderAccounts(orderedIds)

    fun transfer(from: String, to: String, amountCents: Long, note: String) =
        app.transfer(from, to, amountCents, note)

    fun addAccount(
        name: String,
        subName: String,
        iconKey: String,
        balanceCents: Long,
        includeInNetWorth: Boolean,
        category: String = "",
    ) = app.addAccount(name, subName, iconKey, balanceCents, includeInNetWorth, category)

    /**
     * 新增账户（带重名校验）：成功返回新账户 id，重名/空名返回 null。
     * 页面据此给明确反馈，并在创建后定位到新账户。
     */
    suspend fun addAccountUnique(
        name: String,
        subName: String,
        iconKey: String,
        balanceCents: Long,
        includeInNetWorth: Boolean,
        category: String = "",
    ): Long? = app.addAccountUnique(name, subName, iconKey, balanceCents, includeInNetWorth, category)

    /** 重名检查（编辑态用，允许与自己同名） */
    suspend fun isAccountNameTaken(name: String, excludeId: Long? = null): Boolean =
        app.isAccountNameTaken(name, excludeId)

}
