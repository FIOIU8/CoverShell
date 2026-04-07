package com.tencent.hunyuan.app.chat

// Android 基础导入
import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class FileManagerState(
    var currentDirectory: String = "/",
    var files: List<File> = emptyList(),
    var directories: List<File> = emptyList(),
    var selectedFile: File? = null,
    var terminalState: TerminalState = TerminalState()
)

data class TerminalState(
    var commandHistory: MutableList<String> = mutableStateListOf(),
    var currentCommand: String = "",
    var output: String = "",
    var isExecuting: Boolean = false,
    var rootEnabled: Boolean = false,
    var customSuPath: String = "/system/bin/su",
    var currentDirectory: String? = null
)

class MainActivity : ComponentActivity() {
    private val permissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    // 使用新的权限请求方式
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value == true }) {
            initializeApp()
        } else {
            Toast.makeText(this, "需要存储权限才能使用应用", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查权限
        checkPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileManagerApp()
                }
            }
        }

        handleIncomingIntent(intent)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要所有文件访问权限
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesPermission()
                return
            }
        }

        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionLauncher.launch(permissions)
        } else {
            initializeApp()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestAllFilesPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        startActivity(intent)
    }

    private fun initializeApp() {
        // 初始化应用
        Log.d("MainActivity", "应用初始化完成")
    }

    private fun handleIncomingIntent(intent: Intent?) {
        // 处理传入的intent
        intent?.let { receivedIntent ->
            if (receivedIntent.action == Intent.ACTION_VIEW) {
                val uri = receivedIntent.data
                uri?.let {
                    Log.d("MainActivity", "接收到文件: $uri")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp() {
    val context = LocalContext.current
    var uiMode by remember { mutableStateOf("file_manager") } // file_manager, terminal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiMode == "file_manager") "文件管理器" else "终端") },
                actions = {
                    IconButton(onClick = { uiMode = if (uiMode == "file_manager") "terminal" else "file_manager" }) {
                        Icon(
                            imageVector = if (uiMode == "file_manager") Icons.Default.Terminal else Icons.Default.Folder,
                            contentDescription = if (uiMode == "file_manager") "切换到终端" else "切换到文件管理"
                        )
                    }
                    IconButton(onClick = { /* 显示关于对话框 */ }) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        when (uiMode) {
            "file_manager" -> {
                FileManagementContent(Modifier.padding(paddingValues))
            }
            "terminal" -> {
                val terminalState = remember { mutableStateOf(TerminalState()) }
                TerminalPanel(terminalState.value, context, Modifier.padding(paddingValues))
            }
        }
    }
}

@Composable
fun showAppInfoDialog(context: Context) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("应用信息") },
            text = {
                Column {
                    Text("文件管理器 + 终端")
                    Text("版本: 1.0.0")
                    Text("开发者: John")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("功能特点:")
                    Text("• 双面板文件管理")
                    Text("• 内置终端模拟器")
                    Text("• Root权限支持")
                    Text("• 文件类型识别")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun FileManagementContent(modifier: Modifier = Modifier) {
    val leftPanelState = remember { mutableStateOf(FileManagerState()) }
    val rightPanelState = remember { mutableStateOf(FileManagerState()) }
    val context = LocalContext.current

    // 初始化目录
    LaunchedEffect(Unit) {
        leftPanelState.value.currentDirectory = Environment.getExternalStorageDirectory().absolutePath
        rightPanelState.value.currentDirectory = "/"
        refreshDirectoryContents(leftPanelState.value, context)
        refreshDirectoryContents(rightPanelState.value, context)
    }

    Column(modifier = modifier.fillMaxSize()) {
        DirectoryBookmarks(leftPanelState.value) { path ->
            leftPanelState.value.currentDirectory = path
            refreshDirectoryContents(leftPanelState.value, context)
        }

        Row(modifier = Modifier.weight(1f)) {
            FilePanel(
                state = leftPanelState.value,
                onFileClick = { file ->
                    if (file.isDirectory) {
                        leftPanelState.value.currentDirectory = file.absolutePath
                        refreshDirectoryContents(leftPanelState.value, context)
                    } else {
                        // 打开文件
                        openFile(context, file)
                    }
                },
                onFileLongClick = { file ->
                    if (leftPanelState.value.selectedFile == file) {
                        leftPanelState.value.selectedFile = null
                    } else {
                        leftPanelState.value.selectedFile = file
                    }
                },
                modifier = Modifier.weight(1f)
            )

            FilePanel(
                state = rightPanelState.value,
                onFileClick = { file ->
                    if (file.isDirectory) {
                        rightPanelState.value.currentDirectory = file.absolutePath
                        refreshDirectoryContents(rightPanelState.value, context)
                    } else {
                        openFile(context, file)
                    }
                },
                onFileLongClick = { file ->
                    if (rightPanelState.value.selectedFile == file) {
                        rightPanelState.value.selectedFile = null
                    } else {
                        rightPanelState.value.selectedFile = file
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        FileOperationButtons(
            leftState = leftPanelState.value,
            rightState = rightPanelState.value,
            onOperationComplete = {
                refreshDirectoryContents(leftPanelState.value, context)
                refreshDirectoryContents(rightPanelState.value, context)
            }
        )
    }
}

fun refreshDirectoryContents(state: FileManagerState, context: Context) {
    val dir = File(state.currentDirectory)
    if (dir.exists() && dir.isDirectory) {
        try {
            val allFiles = dir.listFiles() ?: emptyArray()

            state.directories = allFiles
                .filter { it.isDirectory && it.canRead() }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }

            state.files = allFiles
                .filter { it.isFile && it.canRead() }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }

            handleSpecialDirectories(state)
        } catch (e: SecurityException) {
            state.directories = emptyList()
            state.files = emptyList()
            Toast.makeText(context, "无法访问目录: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            state.directories = emptyList()
            state.files = emptyList()
            Toast.makeText(context, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } else {
        val parent = File(state.currentDirectory).parentFile
        if (parent != null && parent.exists()) {
            state.currentDirectory = parent.absolutePath
            refreshDirectoryContents(state, context)
        } else {
            state.currentDirectory = Environment.getExternalStorageDirectory().absolutePath
            refreshDirectoryContents(state, context)
        }
    }
}

fun handleSpecialDirectories(state: FileManagerState) {
    val specialPaths = listOf(
        Environment.getExternalStorageDirectory().absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
        "/sdcard",
        "/storage/emulated/0",
        "/data/local/tmp"
    )

    val currentDir = File(state.currentDirectory)
    if (currentDir.absolutePath == "/") {
        val specialDirs = specialPaths.mapNotNull { path ->
            val file = File(path)
            if (file.exists() && file.isDirectory) file else null
        }

        state.directories = (state.directories + specialDirs).distinct().sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
}

fun canAccessFile(context: Context, file: File): Boolean {
    return try {
        if (file.exists()) {
            if (file.isDirectory) {
                file.list()?.isNotEmpty() ?: true
            } else {
                file.canRead()
            }
        } else {
            false
        }
    } catch (e: SecurityException) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            context.contentResolver.openFileDescriptor(uri, "r")?.close()
            true
        } catch (e2: Exception) {
            false
        }
    } catch (e: Exception) {
        false
    }
}

fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW)
        val mimeType = getMimeType(file)
        intent.setDataAndType(uri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // 检查是否有应用可以处理这个intent
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        if (activities.isNotEmpty()) {
            context.startActivity(intent)
        } else {
            // 没有应用可以打开，复制到下载目录
            copyFileToDownloads(context, file)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun getMimeType(file: File): String {
    val extension = file.name.substringAfterLast(".", "")
    return when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "pdf" -> "application/pdf"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "ppt", "pptx" -> "application/vnd.ms-powerpoint"
        "txt" -> "text/plain"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/x-wav"
        "mp4", "m4v" -> "video/mp4"
        "3gp" -> "video/3gpp"
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }
}

fun copyFileToDownloads(context: Context, sourceFile: File) {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(downloadsDir, sourceFile.name)

        if (!destFile.exists()) {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "文件已复制到下载目录: ${destFile.name}", Toast.LENGTH_SHORT).show()

            // 尝试打开复制后的文件
            openFile(context, destFile)
        } else {
            // 文件已存在，询问是否覆盖
            Toast.makeText(context, "文件已存在于下载目录", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "复制文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DirectoryBookmarks(state: FileManagerState, onBookmarkClick: (String) -> Unit) {
    val bookmarks = listOf(
        "下载" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
        "图片" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
        "文档" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath,
        "根目录" to "/",
        "内部存储" to Environment.getExternalStorageDirectory().absolutePath
    )

    LazyRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        items(bookmarks) { (name, path) ->
            Card(
                modifier = Modifier
                    .padding(4.dp)
                    .clickable { onBookmarkClick(path) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (name) {
                            "下载" -> Icons.Default.Download
                            "图片" -> Icons.Default.Image
                            "文档" -> Icons.Default.Description
                            "根目录" -> Icons.Default.Home
                            else -> Icons.Default.Storage
                        },
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun FilePanel(
    state: FileManagerState,
    onFileClick: (File) -> Unit,
    onFileLongClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val parent = File(state.currentDirectory).parentFile
                if (parent != null) {
                    state.currentDirectory = parent.absolutePath
                    refreshDirectoryContents(state, context)
                }
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回上级")
            }

            Text(
                text = "路径: ${state.currentDirectory}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { refreshDirectoryContents(state, context) }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }

            IconButton(onClick = {
                state.currentDirectory = Environment.getExternalStorageDirectory().absolutePath
                refreshDirectoryContents(state, context)
            }) {
                Icon(Icons.Default.Home, contentDescription = "主页")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            // 目录
            item {
                Text(
                    text = "目录 (${state.directories.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(state.directories) { dir ->
                FileItem(
                    file = dir,
                    isSelected = state.selectedFile == dir,
                    onClick = { onFileClick(dir) },
                    onLongClick = { onFileLongClick(dir) },
                    isDirectory = true
                )
            }

            // 文件
            item {
                Text(
                    text = "文件 (${state.files.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(state.files) { file ->
                FileItem(
                    file = file,
                    isSelected = state.selectedFile == file,
                    onClick = { onFileClick(file) },
                    onLongClick = { onFileLongClick(file) },
                    isDirectory = false
                )
            }

            // 空状态
            if (state.directories.isEmpty() && state.files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "空目录",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "目录为空",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileItem(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isDirectory: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                onLongClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isDirectory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                    .border(BorderStroke(1.dp, if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = if (isDirectory) "目录" else "文件",
                    tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isDirectory) "${file.listFiles()?.size ?: 0} 项" else "${formatFileSize(file.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (file.lastModified() > 0) {
                Text(
                    text = formatTimestamp(file.lastModified()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

@Composable
fun formatTimestamp(timestamp: Long): String {
    val context = LocalContext.current
    val date = java.util.Date(timestamp)
    val format = android.text.format.DateFormat.getDateFormat(context)
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    return "${format.format(date)} ${timeFormat.format(date)}"
}

@Composable
fun FileOperationButtons(
    leftState: FileManagerState,
    rightState: FileManagerState,
    onOperationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onOperationComplete,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("刷新")
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = {
                leftState.selectedFile = null
                rightState.selectedFile = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("取消选择")
        }
    }
}

// 终端相关功能
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalPanel(state: TerminalState, context: Context, modifier: Modifier = Modifier) {
    var showSuDialog by remember { mutableStateOf(false) }
    var showCommandHistory by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // 终端输出区域
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                reverseLayout = true
            ) {
                itemsIndexed(state.commandHistory.reversed()) { index, entry ->
                    TerminalOutputItem(
                        entry = entry,
                        isCommand = entry.startsWith("#") || entry.startsWith("$"),
                        onLongClick = {
                            showContextMenu = true
                            contextMenuPosition = index
                        }
                    )
                }

                if (state.output.isNotEmpty()) {
                    item {
                        Text(
                            text = state.output,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 命令输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.currentCommand,
                onValueChange = { state.currentCommand = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("输入命令") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        scope.launch {
                            executeCommandAndAddToHistory(state, context)
                        }
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        executeCommandAndAddToHistory(state, context)
                    }
                },
                enabled = !state.isExecuting && state.currentCommand.isNotEmpty(),
                modifier = Modifier.width(80.dp)
            ) {
                if (state.isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Execute", modifier = Modifier.size(16.dp))
                }
            }
        }

        // 底部控制栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.rootEnabled,
                        onCheckedChange = {
                            state.rootEnabled = it
                            if (it) {
                                // 自动检测su路径
                                scope.launch {
                                    state.customSuPath = findWorkingSuPath(context)
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onError,
                            uncheckedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                    Text("Root", style = MaterialTheme.typography.bodySmall)
                }

                IconButton(onClick = { showCommandHistory = true }) {
                    Icon(Icons.Default.History, contentDescription = "历史记录")
                }

                IconButton(onClick = {
                    state.commandHistory.clear()
                    state.output = ""
                }) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "清屏")
                }

                IconButton(onClick = { showSuDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
        }
    }

    // SU路径设置对话框
    if (showSuDialog) {
        SuPathDialog(state, context, onDismiss = { showSuDialog = false })
    }

    // 命令历史对话框
    if (showCommandHistory) {
        CommandHistoryDialog(
            history = state.commandHistory,
            onCommandSelected = { command ->
                state.currentCommand = command.removePrefix("# ").removePrefix("$ ")
                showCommandHistory = false
            },
            onDismiss = { showCommandHistory = false }
        )
    }

    // 上下文菜单
    if (showContextMenu) {
        val command = state.commandHistory[state.commandHistory.size - 1 - contextMenuPosition]
        ContextMenuDialog(
            command = command,
            onCopy = {
                clipboardCopy(context, command)
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showContextMenu = false }
        )
    }
}

suspend fun executeCommandAndAddToHistory(state: TerminalState, context: Context) {
    if (state.currentCommand.isNotEmpty() && !state.isExecuting) {
        state.isExecuting = true
        val command = state.currentCommand.trim()
        val prompt = if (state.rootEnabled) "# " else "$ "

        // 将命令添加到历史
        state.commandHistory.add(0, "$prompt$command")
        if (state.commandHistory.size > 100) {
            state.commandHistory.removeLast()
        }

        // 重置当前命令
        state.currentCommand = ""

        // 在后台线程执行命令
        val output = withContext(Dispatchers.IO) {
            executeCommand(command, state, context)
        }

        withContext(Dispatchers.Main) {
            if (output.isNotEmpty()) {
                // 将输出拆分为多行，每行作为一个历史条目
                output.split("\n").filter { it.isNotEmpty() }.forEach { line ->
                    state.commandHistory.add(0, line)
                }
            }
            state.isExecuting = false

            // 如果是cd命令，更新当前目录
            if (command.startsWith("cd ")) {
                val newDir = command.substring(3).trim()
                if (newDir.isNotEmpty()) {
                    state.commandHistory.add(0, "${prompt}当前目录: $newDir")
                }
            }
        }
    }
}

fun executeCommand(command: String, state: TerminalState, context: Context): String {
    return try {
        // 处理特殊命令
        when {
            command.startsWith("cd ") -> {
                handleCdCommand(command, state, context)
            }
            command == "ls" || command.startsWith("ls ") -> {
                handleLsCommand(command, state, context)
            }
            command == "pwd" -> {
                "当前目录: ${state.currentDirectory ?: System.getProperty("user.dir")}"
            }
            command.startsWith("su") -> {
                handleSuCommand(command, state, context)
            }
            command == "clear" -> {
                ""
            }
            command == "help" -> {
                """
                可用命令:
                cd [目录]    - 切换目录
                ls [-a] [-l] - 列出文件
                pwd         - 显示当前目录
                su [命令]   - 获取root权限
                clear       - 清屏
                help        - 显示帮助
                """.trimIndent()
            }
            else -> {
                // 普通命令执行
                val process = if (state.rootEnabled && state.customSuPath.isNotEmpty()) {
                    Runtime.getRuntime().exec(arrayOf(state.customSuPath, "-c", command))
                } else {
                    Runtime.getRuntime().exec(command)
                }

                val outputReader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                val output = StringBuilder()
                var line: String?
                while (outputReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                val error = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) {
                    error.append(line).append("\n")
                }

                val exitCode = process.waitFor()

                if (exitCode != 0 && error.isNotEmpty()) {
                    "错误 (代码 $exitCode): $error"
                } else {
                    output.toString()
                }
            }
        }
    } catch (e: Exception) {
        "命令执行错误: ${e.message}\n${e.stackTraceToString()}"
    }
}

private fun handleCdCommand(command: String, state: TerminalState, context: Context): String {
    val path = command.substring(3).trim()

    return try {
        val currentDir = File(state.currentDirectory ?: System.getProperty("user.dir"))
        val targetDir = if (path.startsWith("/")) {
            File(path)
        } else if (path == "..") {
            currentDir.parentFile ?: currentDir
        } else if (path == "~") {
            Environment.getExternalStorageDirectory()
        } else {
            File(currentDir, path)
        }

        if (targetDir.exists() && targetDir.isDirectory && canAccessFile(context, targetDir)) {
            state.currentDirectory = targetDir.absolutePath
            "已切换到: ${targetDir.absolutePath}"
        } else {
            "错误: 目录不存在或无访问权限: ${targetDir.absolutePath}"
        }
    } catch (e: Exception) {
        "cd命令错误: ${e.message}"
    }
}

private fun handleLsCommand(command: String, state: TerminalState, context: Context): String {
    val args = command.substring(3).trim().split("\\s+".toRegex())
    val showAll = args.contains("-a")
    val longFormat = args.contains("-l")

    return try {
        val dir = File(state.currentDirectory ?: System.getProperty("user.dir"))
        if (!dir.exists() || !dir.isDirectory) {
            return "错误: 当前目录不存在"
        }

        val files = dir.listFiles() ?: emptyArray()
        val output = StringBuilder()

        if (longFormat) {
            output.append("权限\t大小\t修改时间\t名称\n")
            output.append("-".repeat(50)).append("\n")
        }

        files.filter { showAll || !it.name.startsWith(".") }.forEach { file ->
            if (longFormat) {
                val permissions = getUnixPermissions(file)
                val size = if (file.isDirectory) "<DIR>" else formatFileSize(file.length())
                val date = formatTimestampForTerminal(file.lastModified())
                output.append("$permissions\t$size\t$date\t${file.name}\n")
            } else {
                output.append("${file.name}\t")
            }
        }

        if (!longFormat) {
            output.append("\n") // 添加换行
        }

        output.toString()
    } catch (e: Exception) {
        "ls命令错误: ${e.message}"
    }
}

private fun handleSuCommand(command: String, state: TerminalState, context: Context): String {
    val args = command.substring(3).trim()
    return try {
        if (args.isEmpty()) {
            // 切换到root
            state.rootEnabled = true
            state.customSuPath = findWorkingSuPath(context)
            "已切换到root权限"
        } else {
            // 执行root命令
            state.rootEnabled = true
            val result = executeCommand(args, state, context)
            state.rootEnabled = false // 执行完后恢复
            result
        }
    } catch (e: Exception) {
        "su命令错误: ${e.message}"
    }
}

fun getUnixPermissions(file: File): String {
    val permissions = StringBuilder()

    // 读权限
    permissions.append(if (file.canRead()) "r" else "-")
    // 写权限
    permissions.append(if (file.canWrite()) "w" else "-")
    // 执行权限
    permissions.append(if (file.canExecute()) "x" else "-")

    return permissions.toString()
}

fun findWorkingSuPath(context: Context): String {
    val commonSuPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/adb/magisk/bin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    for (path in commonSuPaths) {
        if (File(path).exists()) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf(path, "-c", "id"))
                val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
                process.waitFor()
                if (output.contains("uid=0(root)") || output.contains("root")) {
                    return path
                }
            } catch (e: Exception) {
                Log.d("Terminal", "测试SU路径失败: $path, ${e.message}")
            }
        }
    }
    return "/system/bin/su" // 默认值
}

@Composable
fun SuPathDialog(
    state: TerminalState,
    context: Context,
    onDismiss: () -> Unit
) {
    var customPath by remember { mutableStateOf(state.customSuPath) }
    var testResult by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = "Root", tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Root权限设置")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 预设路径按钮
                Text("常用SU路径:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))

                val commonPaths = listOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/sbin/su",
                    "/data/adb/magisk/bin/su"
                )

                LazyRow(modifier = Modifier.fillMaxWidth()) {
                    items(commonPaths) { path ->
                        Button(
                            onClick = {
                                customPath = path
                                testSuPath(path, state, context) { result ->
                                    testResult = result
                                    if (result.contains("成功")) {
                                        state.customSuPath = path
                                    }
                                }
                            },
                            modifier = Modifier.padding(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (customPath == path) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(path.split("/").last(), maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 自定义路径输入
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customPath,
                        onValueChange = { customPath = it },
                        label = { Text("SU路径") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 检测按钮
                Button(
                    onClick = {
                        isTesting = true
                        scope.launch {
                            testResult = try {
                                val process = Runtime.getRuntime().exec(arrayOf(customPath, "-c", "id"))
                                val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
                                process.waitFor()
                                if (output.contains("uid=0(root)") || output.contains("root")) {
                                    state.customSuPath = customPath
                                    "测试成功！路径有效: $customPath"
                                } else {
                                    "测试失败！输出: $output"
                                }
                            } catch (e: Exception) {
                                "测试失败！错误: ${e.message}"
                            }
                            isTesting = false
                        }
                    },
                    enabled = !isTesting && customPath.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("检测中...")
                    } else {
                        Text("检测路径")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 测试结果显示
                if (testResult.isNotEmpty()) {
                    Text(
                        text = testResult,
                        color = if (testResult.contains("成功")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                // 自动检测提示
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "提示: 点击下方按钮自动搜索设备上的SU路径",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val foundPath = findWorkingSuPath(context)
                        if (foundPath != "/system/bin/su") {
                            customPath = foundPath
                            state.customSuPath = foundPath
                            testResult = "自动找到有效SU路径: $foundPath"
                        } else {
                            testResult = "未找到有效的SU路径"
                        }
                    }
                }
            ) {
                Text("自动搜索")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

fun testSuPath(path: String, state: TerminalState, context: Context, onResult: (String) -> Unit) {
    thread {
        try {
            val process = Runtime.getRuntime().exec(arrayOf(path, "-c", "id"))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            if (output.contains("uid=0(root)") || output.contains("root")) {
                onResult("测试成功！路径有效: $path")
            } else {
                onResult("测试失败！输出: $output")
            }
        } catch (e: Exception) {
            onResult("测试失败！错误: ${e.message}")
        }
    }
}

@Composable
fun CommandHistoryDialog(
    history: List<String>,
    onCommandSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("命令历史") },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(history.filter { it.isNotBlank() }) { command ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = command,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCommandSelected(command)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun ContextMenuDialog(
    command: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作选项") },
        text = {
            Column {
                Text("选择操作:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("命令: $command", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Column {
                Button(onClick = {
                    onCopy()
                    onDismiss()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制")
                    }
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TerminalOutputItem(entry: String, isCommand: Boolean, onLongClick: () -> Unit = {}) {
    Box(modifier = Modifier.clickable(onClick = onLongClick)) {
        if (isCommand) {
            // 命令行
            Text(
                text = entry,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.startsWith("#")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        } else {
            // 普通输出
            Text(
                text = entry,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 1.dp),
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun clipboardCopy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("命令", text)
    clipboard.setPrimaryClip(clip)
}

// 辅助函数：为终端格式化时间戳（不带上下文）
fun formatTimestampForTerminal(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return format.format(date)
}