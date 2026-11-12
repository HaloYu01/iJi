package com.iji.app.ai

import android.util.Log
import java.io.File

/**
 * 取图识别的临时图片清理。
 *
 * 拍照（FileProvider 写 `cache/camera`）与系统分享（复制进 `cache/`）都会留下原图，
 * 位图 decode 进内存后文件就该删——账单截图不该长期躺在缓存里。
 */
internal object TempImages {

    private const val TAG = "TempImages"

    /** 删不掉不影响识别结果，只记日志 */
    fun delete(file: File?) {
        if (file == null || !file.exists()) return
        runCatching { file.delete() }
            .onFailure { Log.w(TAG, "临时图片删除失败：${file.name}", it) }
    }

    /**
     * 清掉上次遗留（进程被杀 / 取消拍照）。
     * 只删 [minAgeMillis] 之前的，避免扫掉当前这轮刚写好的文件。
     *
     * @param prefix 为空表示整个目录都算临时图
     */
    fun sweep(dir: File?, prefix: String = "", minAgeMillis: Long = 60L * 60 * 1000) {
        if (dir == null || !dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - minAgeMillis
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.lastModified() < cutoff }
            ?.forEach { delete(it) }
    }
}
