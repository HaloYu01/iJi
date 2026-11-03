package com.iji.app.ui.mine

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iji.app.data.repository.SettingsRepository

/**
 * 个人资料（头像 / 昵称）全局状态。
 *
 * 与主题状态的 `ThemeState` 同一套做法：用 `mutableStateOf` 持有，
 * 任何写入都会让读取处的 Composable 立即重组 —— 因此「修改后实时保存并同步到展示区域」是天然的，
 * 不需要额外的刷新回调。持久化统一走 [SettingsRepository]（Preferences DataStore），
 * 不再直连 SharedPreferences。
 */
object ProfileState {

    /** 昵称；为空表示未设置（展示时显示「点击设置昵称」） */
    var nickname by mutableStateOf("")
        private set

    /** 头像图片 URI；为空表示使用默认字母头像 */
    var avatarUri by mutableStateOf("")
        private set

    private var initialized = false

    /** 从本地读取已保存的资料（幂等，可重复调用） */
    fun ensureInit(context: Context) {
        if (initialized) return
        nickname = SettingsRepository.getNickname(context)
        avatarUri = SettingsRepository.getAvatarUri(context)
        initialized = true
    }

    /** 更新昵称（立即生效 + 落盘） */
    fun setNickname(context: Context, value: String) {
        nickname = value
        SettingsRepository.setNickname(context, value)
    }

    /** 更新头像 URI（立即生效 + 落盘） */
    fun setAvatarUri(context: Context, value: String) {
        avatarUri = value
        SettingsRepository.setAvatarUri(context, value)
    }
}
