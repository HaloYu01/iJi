package com.iji.app.shizuku

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileOutputStream
import kotlin.system.exitProcess

/**
 * 跑在 **shell uid** 下的 UserService，由 Shizuku 拉起（参考 AutoAccounting 的 `shell` 模块）。
 *
 * 为什么需要它：`screencap` 要有 shell 才有的 `READ_FRAME_BUFFER` 权限，
 * 应用自己的 uid 直接 `ProcessBuilder("screencap")` 只会拿到空数据。
 * Shizuku 的公开 API 里唯一能借到 shell 身份的方式就是 [Shizuku.bindUserService] +
 * 一个本应用里的远程服务 —— 于是有了这个类。
 *
 * 刻意保持「能力最小」：只有截屏、跑命令、退出三个方法，
 * 不做常驻监听、不开放任意文件读写。
 */
class ShizukuShellService : IShizukuShell.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun screencap(): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        // 截图动辄几百 KB，不能阻塞 Binder 线程：另起线程往管道写，读端立刻返回给应用
        Thread({
            try {
                val process = ProcessBuilder("screencap", "-p").start()
                FileOutputStream(pipe[1].fileDescriptor).use { out ->
                    process.inputStream.use { it.copyTo(out) }
                }
                process.waitFor()
            } catch (t: Throwable) {
                Log.w(TAG, "screencap 执行失败", t)
            } finally {
                // 关闭写端 = 给应用一个 EOF，否则 decodeStream 会一直等
                runCatching { pipe[1].close() }
            }
        }, "iji-screencap").start()
        return pipe[0]
    }

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) return ""
        return runCatching {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            // 先读完再 waitFor：输出超过管道缓冲区时反过来会互相等死
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        }.getOrElse { t ->
            Log.w(TAG, "命令执行失败：$command", t)
            t.message.orEmpty()
        }
    }

    private companion object {
        const val TAG = "ShizukuShell"
    }
}
