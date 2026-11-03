package com.iji.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iji.app.ui.theme.AppleRed
import com.iji.app.ui.theme.MiuixPrimary
import com.iji.app.ui.theme.TextSecondary

/** 应用启动锁屏：输入 PIN 或指纹解锁 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    pin: String,
    fingerprintAvailable: Boolean,
    fingerprintEnabled: Boolean,
    onFingerprintClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // 防暴破：连续输错 5 次冷却 30 秒。计数在加密偏好里，杀进程重进也赖不掉。
    var lockSeconds by remember { mutableStateOf(AppLockManager.lockRemainingSeconds(context)) }
    var hint by remember { mutableStateOf("") }
    val locked = lockSeconds > 0

    // 冷却倒计时（每秒刷新；到点自动放行）。
    // key 用 locked：新一轮冷却开始时（false→true）会重新启动这个计时协程，
    // 否则第一次冷却走完协程就退出了，第二次只显示一个不动的数字。
    LaunchedEffect(locked) {
        while (locked) {
            val left = AppLockManager.lockRemainingSeconds(context)
            lockSeconds = left
            if (left <= 0) {
                hint = ""
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // 进锁屏先弹系统指纹（开了指纹解锁且不在冷却期）——指纹优先，省一次手动点按；
    // 只在本轮锁屏触发一次，取消/失败后回落到手动输入 PIN，不再反复打扰。
    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(fingerprintAvailable, fingerprintEnabled, locked) {
        if (fingerprintAvailable && fingerprintEnabled && !locked && !autoPrompted) {
            autoPrompted = true
            kotlinx.coroutines.delay(400) // 等首帧上屏，避免系统弹窗抢在 Activity 绘制前
            onFingerprintClick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // 标题
        Text(
            "输入密码",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "请输入 4 位数字密码",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        Spacer(Modifier.height(40.dp))

        // PIN 圆点
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(4) { index ->
                val filled = index < input.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            // 空点跟随主题：写死的浅色分隔线在深色模式下会变成一排亮点
                            if (error) AppleRed
                            else if (filled) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        ),
                )
            }
        }

        // 提示优先说「还剩几次 / 还要等多久」，别只说「密码错误」
        val message = when {
            locked -> "错误次数过多，请 ${lockSeconds} 秒后再试"
            hint.isNotBlank() -> hint
            error -> "密码错误"
            else -> ""
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = AppleRed, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(60.dp))

        // 数字键盘
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "del"),
        )
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(72.dp))
                        "del" -> LockKey("⌫", Modifier.size(72.dp)) {
                            if (!locked && input.isNotEmpty()) input = input.dropLast(1)
                            error = false
                        }
                        else -> LockKey(key, Modifier.size(72.dp)) {
                            if (locked || input.length >= 4) return@LockKey
                            input += key
                            error = false
                            if (input.length == 4) {
                                if (input == pin) {
                                    AppLockManager.resetFailures(context)
                                    onUnlocked()
                                } else {
                                    error = true
                                    input = ""
                                    val left = AppLockManager.recordFailure(context)
                                    if (left <= 0) {
                                        lockSeconds = AppLockManager.lockRemainingSeconds(context)
                                        hint = ""
                                    } else {
                                        hint = "密码错误，还可尝试 $left 次"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 指纹按钮
        if (fingerprintAvailable && fingerprintEnabled) {
            Spacer(Modifier.height(24.dp))
            Text(
                "使用指纹解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = MiuixPrimary,
                modifier = Modifier.clickable { onFingerprintClick() },
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LockKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            // 按键底色跟随主题：此前写死浅色 AppleSecondaryBackground，深色模式下是十个小白块
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
