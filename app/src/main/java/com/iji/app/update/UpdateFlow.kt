package com.iji.app.update

import android.content.Context
import com.iji.app.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 更新流程状态（纯数据，供弹窗渲染） */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val reason: String) : UpdateState
    data class Available(val info: UpdateChecker.UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateChecker.UpdateInfo, val received: Long, val total: Long) : UpdateState
    data class Ready(val info: UpdateChecker.UpdateInfo, val file: File) : UpdateState
    data class NeedPermission(val info: UpdateChecker.UpdateInfo, val file: File) : UpdateState
    data class Installing(val info: UpdateChecker.UpdateInfo, val file: File) : UpdateState
}

/**
 * 更新流程的**唯一**状态机（进程级）。
 *
 * 两个入口共用它：A = App 启动静默检查后弹窗，B = 关于页「检查更新」手动打开。
 * 状态放在 Application 级作用域而不是 `rememberCoroutineScope`：下载中用户点「后台下载」
 * 关掉弹窗后，任务不能随组合销毁被取消（与无障碍截屏那条踩坑同理）。
 * A / B 因此也不会并发下载同一个 `.part` 文件。
 */
object UpdateFlow {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _show = MutableStateFlow(false)
    val show: StateFlow<Boolean> = _show.asStateFlow()

    private var job: Job? = null

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /**
     * A：App 启动即检查。每天首次进入主界面跑一次（可关），失败静默不打扰；
     * 只有真的发现新版本且「更新提醒」开着才弹窗，并稍微延后避免压住启动画面。
     */
    fun startupCheck(context: Context) {
        scope.launch {
            val prefs = withContext(Dispatchers.IO) {
                StartupPrefs(
                    enabled = SettingsRepository.getBoolean(context, SettingsRepository.KEY_AUTO_CHECK_UPDATE, true),
                    reminder = SettingsRepository.getBoolean(context, SettingsRepository.KEY_UPDATE_REMINDER, true),
                    today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    lastCheck = SettingsRepository.getString(context, SettingsRepository.KEY_UPDATE_LAST_CHECK_DATE),
                )
            }
            if (!prefs.enabled) return@launch
            if (prefs.lastCheck == prefs.today) return@launch
            SettingsRepository.setString(context, SettingsRepository.KEY_UPDATE_LAST_CHECK_DATE, prefs.today)

            val result = runCheck(context)
            if (result is UpdateChecker.CheckResult.Available && prefs.reminder) {
                delay(800)
                _show.value = true
            }
        }
    }

    private data class StartupPrefs(
        val enabled: Boolean,
        val reminder: Boolean,
        val today: String,
        val lastCheck: String,
    )

    /** B：关于页点「检查更新」。正在下载/安装时只把弹窗重新显示出来，不打断任务 */
    fun openManual(context: Context) {
        _show.value = true
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Installing -> return
            else -> check(context)
        }
    }

    /** 真实联网检查：只得到「有新版本」，绝不自动开始下载 */
    fun check(context: Context) {
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Installing -> return
            else -> Unit
        }
        job?.cancel()
        _state.value = UpdateState.Checking
        job = scope.launch {
            val result = runCatching { runCheck(context) }
                .getOrElse { t -> UpdateChecker.CheckResult.Failed(t.message ?: "未知错误") }
            when (result) {
                is UpdateChecker.CheckResult.UpToDate -> _state.value = UpdateState.UpToDate
                is UpdateChecker.CheckResult.Failed -> _state.value = UpdateState.Failed(result.reason)
                is UpdateChecker.CheckResult.Available -> {
                    // 上次已下好的同版本包直接给「安装」，不重复下载
                    val cached = withContext(Dispatchers.IO) {
                        UpdateInstaller.existingApk(context, result.info)
                            ?.takeIf { UpdateInstaller.verify(context, it, result.info) == null }
                    }
                    _state.value = if (cached != null) {
                        UpdateState.Ready(result.info, cached)
                    } else {
                        UpdateState.Available(result.info)
                    }
                }
            }
        }
    }

    /** 点「立即下载」才真正开始；进度只在整数百分比变化时推状态 */
    fun download(context: Context, info: UpdateChecker.UpdateInfo) {
        job?.cancel()
        _state.value = UpdateState.Downloading(info, 0L, info.sizeBytes ?: -1L)
        job = scope.launch {
            var lastPercent = -1
            val result = runCatching {
                UpdateInstaller.download(context, info) { received, total ->
                    val percent = if (total > 0) (received * 100 / total).toInt() else -1
                    if (percent != lastPercent) {
                        lastPercent = percent
                        withContext(Dispatchers.Main.immediate) {
                            _state.value = UpdateState.Downloading(info, received, total)
                        }
                    }
                }
            }
            result.onSuccess { file ->
                _state.value = if (UpdateInstaller.canInstall(context)) {
                    UpdateState.Ready(info, file)
                } else {
                    UpdateState.NeedPermission(info, file)
                }
            }.onFailure { e ->
                if (e is CancellationException) return@launch
                _state.value = UpdateState.Failed(e.message ?: "下载失败")
            }
        }
    }

    fun install(context: Context, info: UpdateChecker.UpdateInfo, file: File) {
        if (!UpdateInstaller.canInstall(context)) {
            _state.value = UpdateState.NeedPermission(info, file)
            return
        }
        _state.value = UpdateState.Installing(info, file)
        if (UpdateInstaller.install(context, file)) {
            // 交棒给系统安装器后无条件收尾：用户在系统页点「取消」也不会卡在「安装中」
            _show.value = false
            _state.value = UpdateState.Ready(info, file)
        } else {
            _state.value = UpdateState.Failed("没有可用的系统安装器，请到更新源手动下载安装")
        }
    }

    /** 取消正在进行的检查/下载；下载中回到「立即下载」态 */
    fun cancelActive() {
        job?.cancel()
        job = null
        when (val s = _state.value) {
            is UpdateState.Downloading -> _state.value = UpdateState.Available(s.info)
            is UpdateState.Checking -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    /**
     * 关闭弹窗。下载中只关窗、任务继续（「后台下载」）；安装中把状态收回 Ready，
     * 避免系统安装器被取消后下次打开永远显示「安装中」。
     */
    fun close() {
        _show.value = false
        val s = _state.value
        if (s is UpdateState.Installing) _state.value = UpdateState.Ready(s.info, s.file)
    }

    private suspend fun runCheck(context: Context): UpdateChecker.CheckResult =
        UpdateChecker.check(currentVersion(context))
}
