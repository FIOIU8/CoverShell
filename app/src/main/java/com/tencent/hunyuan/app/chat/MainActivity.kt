package com.tencent.hunyuan.app.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// Miuix 核心
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.TiltFeedback

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

/**
 * 格式化文件日期（26-04-10 18:37）
 */
fun formatFileDate(time: Long): String {
    return SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
}

/**
 * ROOT下获取文件权限字符串
 */
suspend fun getFilePermission(file: File): String {
    return withContext(Dispatchers.IO) {
        try {
            val path = file.absolutePath
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -l $path | head -n1"))
            val line = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            process.destroy()
            line?.split(" ")?.firstOrNull() ?: "-?????????"
        } catch (e: Exception) {
            "-?????????"
        }
    }
}

// ====================== 状态数据类 ======================
data class FileManagerState(
    val currentDirectory: String = Environment.getExternalStorageDirectory().absolutePath,
    val directories: List<File> = emptyList(),
    val files: List<File> = emptyList(),
    val selectedFile: File? = null
)

data class TerminalState(
    val commandHistory: List<String> = emptyList(), // 修复：使用不可变列表
    val currentCommand: String = "",
    val isExecuting: Boolean = false,
    val currentDirectory: String = Environment.getExternalStorageDirectory().absolutePath
)

class MainActivity : ComponentActivity() {
    // 存储权限请求器
    private val requestStorage: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixTheme {
                Box(Modifier.fillMaxSize()) {
                    MainUI()
                }
            }
        }
    }

    // 权限检查与申请
    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 申请所有文件访问权限
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            // Android 10以下 动态申请存储读取权限
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

// ====================== 主UI ======================
@Composable
fun MainUI() {
    var uiMode by remember { mutableStateOf("file") } // file/terminal
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "ROOT 文件管理器",
                actions = {
                    // 切换文件/终端
                    IconButton(onClick = {
                        uiMode = if (uiMode == "file") "terminal" else "file"
                    }) {
                        Icon(
                            if (uiMode == "file") Icons.Default.Terminal else Icons.Default.Folder,
                            contentDescription = "切换模式"
                        )
                    }
                    // 关于按钮
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiMode == "file") FileManagerScreen() else TerminalScreen()
        }

        // 关于弹窗
        OverlayDialog(
            show = showAbout,
            title = "关于",
            summary = "ROOT 文件管理器 | Miuix UI",
            onDismissRequest = { showAbout = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = { showAbout = false }, modifier = Modifier.fillMaxWidth()) {
                Text("确定")
            }
        }
    }
}

// ====================== 工具方法 ======================
/**
 * 检测设备是否拥有ROOT权限
 */
suspend fun hasRoot(): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor(1, TimeUnit.SECONDS)
            process.destroy()
            result.contains("uid=0(root)")
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 修复：ROOT 权限读取文件列表（支持 / 根目录）
 */
suspend fun getFilesWithRoot(path: String): List<File> {
    return withContext(Dispatchers.IO) {
        try {
            // 先用普通方式尝试
            val normalFiles = File(path).listFiles()?.filter { it.exists() }
            if (!normalFiles.isNullOrEmpty()) return@withContext normalFiles

            // 普通方式失败 → 使用 ROOT 读取目录
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -a $path"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val fileNames = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val name = line?.trim()
                if (!name.isNullOrBlank() && name != "." && name != "..") {
                    fileNames.add(name)
                }
            }
            process.waitFor()
            process.destroy()
            reader.close()

            // 转为 File 对象
            fileNames.map { File("$path/$it") }.filter { it.exists() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * 修复：ROOT 命令正确执行 + 读取错误流
 */
suspend fun runRootCommand(command: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            // 读取正常输出 + 错误输出（关键！）
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor(2, TimeUnit.SECONDS)
            process.destroy()
            output + error
        } catch (e: Exception) {
            "执行异常：${e.message}"
        }
    }
}

// 统一调用：自动选择普通/ROOT
suspend fun getFilesAuto(path: String): List<File> {
    val rootFiles = getFilesWithRoot(path)
    if (rootFiles.isNotEmpty()) return rootFiles
    return withContext(Dispatchers.IO) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) dir.listFiles()?.filter { it.exists() } ?: emptyList() else emptyList()
    }
}

/**
 * 文件大小格式化（B/KB/MB/GB）
 */
fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / 1024 / 1024} MB"
        else -> "${size / 1024 / 1024 / 1024} GB"
    }
}

// ====================== 文件管理器 ======================
@Composable
fun FileManagerScreen() {
    // 左右双面板独立状态
    var leftPanelState by remember { mutableStateOf(FileManagerState()) }
    var rightPanelState by remember { mutableStateOf(FileManagerState(currentDirectory = "/")) }
    val coroutineScope = rememberCoroutineScope()

    // 刷新文件列表
    fun refreshFileList(
        currentState: FileManagerState,
        updateState: (FileManagerState) -> Unit
    ) {
        coroutineScope.launch {
            val fileList = getFilesAuto(currentState.currentDirectory)
            val directories = fileList.filter { it.isDirectory }
            val files = fileList.filter { !it.isDirectory }
            updateState(currentState.copy(directories = directories, files = files))
        }
    }

    // 初始化加载
    LaunchedEffect(Unit) {
        refreshFileList(leftPanelState) { leftPanelState = it }
        refreshFileList(rightPanelState) { rightPanelState = it }
    }

    // 双面板布局
    Row(Modifier.fillMaxSize()) {
        // 左侧面板
        FilePanel(
            state = leftPanelState,
            modifier = Modifier.weight(1f),
            onNavigate = { file ->
                if (file.isDirectory) {
                    leftPanelState = leftPanelState.copy(currentDirectory = file.absolutePath)
                    refreshFileList(leftPanelState) { leftPanelState = it }
                }
            },
            onSelect = {
                leftPanelState = leftPanelState.copy(
                    selectedFile = if (leftPanelState.selectedFile == it) null else it
                )
            },
            onRefresh = { refreshFileList(leftPanelState) { leftPanelState = it } }
        )

        // 右侧面板
        FilePanel(
            state = rightPanelState,
            modifier = Modifier.weight(1f),
            onNavigate = { file ->
                if (file.isDirectory) {
                    rightPanelState = rightPanelState.copy(currentDirectory = file.absolutePath)
                    refreshFileList(rightPanelState) { rightPanelState = it }
                }
            },
            onSelect = {
                rightPanelState = rightPanelState.copy(
                    selectedFile = if (rightPanelState.selectedFile == it) null else it
                )
            },
            onRefresh = { refreshFileList(rightPanelState) { rightPanelState = it } }
        )
    }
}

/**
 * 单个文件面板（可复用）
 */
@Composable
fun FilePanel(
    state: FileManagerState,
    modifier: Modifier,
    onNavigate: (File) -> Unit,
    onSelect: (File) -> Unit,
    onRefresh: () -> Unit
) {
    val currentDir = File(state.currentDirectory)
    val parentDir = currentDir.parentFile

    Column(modifier.padding(8.dp)) {
        // 路径栏 + 刷新按钮
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "路径: ${state.currentDirectory}",
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(animationMode = MarqueeAnimationMode.Immediately),
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }

        // 文件列表
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(),
            overscrollEffect = null
        ) {
            // 返回上级目录
            item {
                BackFolderItem(parentDir = parentDir, onNavigate = onNavigate)
            }

            // 文件夹列表
            item {
                Text(
                    "目录 (${state.directories.size})",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(state.directories) { dir ->
                FileItem(
                    file = dir,
                    isDir = true,
                    isSelected = state.selectedFile == dir,
                    onClick = { onNavigate(dir) },
                    onLongClick = { onSelect(dir) }
                )
            }

            // 文件列表
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    "文件 (${state.files.size})",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(state.files) { file ->
                FileItem(
                    file = file,
                    isDir = false,
                    isSelected = state.selectedFile == file,
                    onClick = {},
                    onLongClick = { onSelect(file) }
                )
            }
        }
    }
}

/**
 * 返回上级目录项
 */
@Composable
fun BackFolderItem(parentDir: File?, onNavigate: (File) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .pressable(interactionSource = interactionSource, indication = TiltFeedback())
            .clickable { parentDir?.let { onNavigate(it) } }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowUpward, null, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = "..", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

/**
 * 文件/文件夹 列表项（最终版）
 * 文件夹 = 显示修改日期
 * 文件 = 显示权限 + 大小
 */
@Composable
fun FileItem(
    file: File,
    isDir: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var permission by remember { mutableStateOf("-?????????") }
    val size = formatSize(file.length())
    val date = formatFileDate(file.lastModified())

    // 只有【文件】才加载权限
    LaunchedEffect(file) {
        if (!isDir) {
            permission = getFilePermission(file)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .pressable(interactionSource = interactionSource, indication = TiltFeedback())
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isDir) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(file.name, fontWeight = FontWeight.Medium)

                // ====================== 最终逻辑 ======================
                if (isDir) {
                    // ✅ 文件夹：显示日期
                    Text(date)
                } else {
                    // ✅ 文件：显示 权限 + 大小
                    Text("$permission $size")
                }
            }
        }
    }
}

// ====================== 终端界面 ======================
@Composable
fun TerminalScreen() {
    var terminalState by remember { mutableStateOf(TerminalState()) }
    val coroutineScope = rememberCoroutineScope()
    var isRootAvailable by remember { mutableStateOf(false) }

    // 初始化检测ROOT权限
    LaunchedEffect(Unit) { isRootAvailable = hasRoot() }

    // 执行命令
    fun executeCommand() {
        val command = terminalState.currentCommand.trim()
        if (command.isBlank() || terminalState.isExecuting) return

        terminalState = terminalState.copy(currentCommand = "", isExecuting = true)

        coroutineScope.launch {
            // 统一用修复的 ROOT 命令执行
            val result = runRootCommand(command)
            val newHistory = terminalState.commandHistory + "\$ $command\n$result"
            terminalState = terminalState.copy(
                isExecuting = false,
                commandHistory = newHistory
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // 命令输出区域
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(12.dp)
                    .overScrollVertical()
                    .scrollEndHaptic(),
                reverseLayout = true, // 最新内容在底部
                overscrollEffect = null
            ) {
                items(terminalState.commandHistory) { log ->
                    Text(log)
                }
            }
        }

        // 命令输入栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = terminalState.currentCommand,
                onValueChange = { terminalState = terminalState.copy(currentCommand = it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { executeCommand() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { executeCommand() }, enabled = !terminalState.isExecuting) {
                Icon(Icons.Default.Send, contentDescription = "发送")
            }
        }

        // 清空按钮
        Button(
            onClick = { terminalState = terminalState.copy(commandHistory = emptyList()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清空输出")
        }
    }
}