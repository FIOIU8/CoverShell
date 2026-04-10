package com.tencent.hunyuan.app.chat

object AppConfig {
    // 应用配置
    const val APP_NAME = "文件管理器"
    const val APP_VERSION = "1.0.0"

    // Root配置
    const val DEFAULT_SU_PATH = "/system/bin/su"
    val ALTERNATIVE_SU_PATHS = arrayOf(
        "/system/xbin/su",
        "/sbin/su",
        "/data/adb/magisk/bin/su",
        "/su/bin/su"
    )

    // 终端配置
    const val DEFAULT_TERMINAL_COMMAND = "ls -la"
    const val MAX_COMMAND_HISTORY = 100

    // 文件操作配置
    const val DEFAULT_COPY_BUFFER_SIZE = 8192

    // 权限配置
    val REQUIRED_PERMISSIONS = arrayOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    )

    // 特殊目录
    val SPECIAL_DIRECTORIES = listOf(
        Pair("下载", "DIRECTORY_DOWNLOADS"),
        Pair("图片", "DIRECTORY_DCIM"),
        Pair("文档", "DIRECTORY_DOCUMENTS"),
        Pair("音乐", "DIRECTORY_MUSIC"),
        Pair("视频", "DIRECTORY_MOVIES")
    )
}