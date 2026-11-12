package com.iji.app.data.repository

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * SVG 图标上传存储：把用户选中的 SVG 复制到**应用私有目录**，并记录「名称 → SVG 文件」映射。
 *
 * 当前只负责**上传与存储**（渲染方式待定，见 HANDOFF.md §8.3）。
 * 后续接入渲染时，按约定路径 [fileFor] 读文件即可，无需改动存储结构。
 *
 * 为什么复制到私有目录而不是直接用 Uri：content Uri 的读权限是临时的，
 * 进程重启后可能失效；复制后图标就与来源解耦，也不占长期授权。
 */
object SvgIconStore {

    private const val PREF = "iji_svg_icons"
    private const val DIR = "svg_icons"

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** 该名称对应的 SVG 文件（不一定存在） */
    fun fileFor(context: Context, name: String): File = File(dir(context), "$name.svg")

    /** 该名称是否已上传过 SVG */
    fun has(context: Context, name: String): Boolean = fileFor(context, name).exists()

    /**
     * 保存：把 [uri] 的内容复制到私有目录（同名覆盖），并记录映射。
     * @return 成功返回目标文件；失败返回 null
     */
    fun save(context: Context, name: String, uri: Uri): File? = runCatching {
        val target = fileFor(context, name)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }
        // 记录映射：便于后续排查与迁移（渲染按约定路径 fileFor() 取，不必再查 SP）
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(name, target.name)
            .apply()
        target
    }.getOrNull()

    /** 删除已上传的 SVG（之后回落到素材包图标） */
    fun remove(context: Context, name: String) {
        runCatching { fileFor(context, name).delete() }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(name)
            .apply()
    }
}
