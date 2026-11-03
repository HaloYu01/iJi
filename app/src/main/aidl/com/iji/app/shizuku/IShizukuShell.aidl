package com.iji.app.shizuku;

import android.os.ParcelFileDescriptor;

/**
 * Shizuku UserService 通道：这些方法跑在 shell uid 下，不是应用自己的 uid。
 *
 * 只开放三件事——截一帧屏、跑一条命令、销毁自己。不做文件读写、
 * 不做常驻监听，避免把 shell 权限摊成一个通用后门。
 */
interface IShizukuShell {

    /** 结束服务进程，下次绑定由 Shizuku 重新拉起 */
    void destroy();

    /**
     * 执行 `screencap -p`，返回 PNG 字节流的读端。
     *
     * 走管道而不是临时文件：省掉「找一个应用和 shell 都能读写的目录 + chmod」这一步，
     * 也避免截图残留在磁盘上。
     */
    ParcelFileDescriptor screencap();

    /** 执行一条 shell 命令（如 `cmd statusbar collapse`），返回 stdout/stderr 合并文本 */
    String exec(String command);
}
