package com.tencent.hunyuan.app.chat

/**
 * 全局远程配置文件
 * 作用：统一管理服务器 API 地址（示例配置，本地使用真实地址）
 * 适用：文件管理APP（无登录体系）
 */
object AppConfig {

    // ==========================
    // 服务器基础地址
    // ==========================
    const val BASE_URL = "https://api.你的服务器.com/"

    // ==========================
    // 1. 远程配置 API
    // 作用：获取公告开关、更新开关、功能开关
    // ==========================
    const val API_REMOTE_CONFIG = "app/config"

    // ==========================
    // 2. APP 版本更新 API
    // 作用：检查新版本、获取下载链接、更新日志、强制更新
    // ==========================
    const val API_CHECK_UPDATE = "app/update"

    // ==========================
    // 3. 公告 API
    // 作用：获取公告标题、内容、是否显示
    // ==========================
    const val API_GET_ANNOUNCEMENT = "app/announcement"

    // ==========================
    // 4. 文件下载 API
    // 作用：文件管理APP → 云端文件下载
    // ==========================
    const val API_FILE_DOWNLOAD = "file/download"
}