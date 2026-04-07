package com.tencent.hunyuan.app.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import kotlin.concurrent.thread

data class FileManagerState(
    var currentDirectory: String = "/",
    var files: List<File> = emptyList(),
    var directories: List<File> = emptyList(),
    var selectedFile: File? = null,
    var terminalState: TerminalState = TerminalState()
)

data class TerminalState(
    var commandHistory: MutableList<String> = mutableListOf(),
    var currentCommand: String = "",
    var output: String = "",
    var isExecuting: Boolean = false,
    var rootEnabled: Boolean = false,
    var customSuPath: String = "/system/bin/su"
)

class MainActivity : ComponentActivity() {
    private val permissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val permissionRequestCode = 1001

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionLauncher.launch(permissions)
        } else {
            initializeApp()
        }
    }

    // 删除旧的 onRequestPermissionsResult 方法，因为使用 registerForActivityResult 替代
    // override fun onRequestPermissionsResult(...) 方法已移除

    private fun initializeApp() {
        // 初始化应用
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val fileUri = intent.data
                if (fileUri != null) {
                    try {
                        val filePath = getPathFromUri(fileUri)
                        if (filePath != null) {
                            val file = File(filePath)
                            if (file.exists()) {
                                // 可以在这里处理预选中文件
                                Log.d("MainActivity", "Received file: $filePath")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error handling VIEW intent", e)
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val extraFile = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (extraFile != null) {
                    try {
                        val filePath = getPathFromUri(extraFile)
                        if (filePath != null) {
                            val file = File(filePath)
                            if (file.exists()) {
                                Log.d("MainActivity", "Received shared file: $filePath")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error handling SEND intent", e)
                    }
                }
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        return null
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
                TerminalPanel(terminalState.value, Modifier.padding(paddingValues))
            }
        }
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
        refreshDirectoryContents(leftPanelState.value)
        refreshDirectoryContents(rightPanelState.value)
    }

    Column(modifier = modifier.fillMaxSize()) {
        DirectoryBookmarks(leftPanelState.value) { path ->
            leftPanelState.value.currentDirectory = path
            refreshDirectoryContents(leftPanelState.value)
        }

        Row(modifier = Modifier.weight(1f)) {
            FilePanel(
                state = leftPanelState.value,
                onFileClick = { file ->
                    if (file.isDirectory) {
                        leftPanelState.value.currentDirectory = file.absolutePath
                        refreshDirectoryContents(leftPanelState.value)
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
                        refreshDirectoryContents(rightPanelState.value)
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
                refreshDirectoryContents(leftPanelState.value)
                refreshDirectoryContents(rightPanelState.value)
            }
        )
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
                    Text(name)
                }
            }
        }
    }
}

fun refreshDirectoryContents(state: FileManagerState) {
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
        }
    } else {
        val parent = File(state.currentDirectory).parentFile
        if (parent != null && parent.exists()) {
            state.currentDirectory = parent.absolutePath
            refreshDirectoryContents(state)
        } else {
            state.currentDirectory = Environment.getExternalStorageDirectory().absolutePath
            refreshDirectoryContents(state)
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

fun moveFile(source: File, targetDirectory: String): Boolean {
    return try {
        val targetFile = File(targetDirectory, source.name)
        source.renameTo(targetFile)
    } catch (e: Exception) {
        false
    }
}

fun copyFile(source: File, targetDirectory: String): Boolean {
    return try {
        val targetFile = File(targetDirectory, source.name)
        source.copyTo(targetFile, overwrite = true)
        true
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
        intent.setDataAndType(uri, context.contentResolver.getType(uri))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun FilePanel(
    state: FileManagerState,
    onFileClick: (File) -> Unit,
    onFileLongClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                text = "路径: ${state.currentDirectory}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshDirectoryContents(state) }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            // 目录
            item {
                Text(
                    text = "目录",
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
                    text = "文件",
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
                    tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
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
    return format.format(date)
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
        // 复制操作
        if (leftState.selectedFile != null) {
            Button(
                onClick = {
                    copyFile(leftState.selectedFile!!, rightState.currentDirectory)
                    onOperationComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy right")
                Spacer(modifier = Modifier.width(4.dp))
                Text("→ 复制到右侧")
            }
        }

        if (rightState.selectedFile != null) {
            Button(
                onClick = {
                    copyFile(rightState.selectedFile!!, leftState.currentDirectory)
                    onOperationComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy left")
                Spacer(modifier = Modifier.width(4.dp))
                Text("复制到左侧 ←")
            }
        }

        // 移动操作
        if (leftState.selectedFile != null) {
            Button(
                onClick = {
                    moveFile(leftState.selectedFile!!, rightState.currentDirectory)
                    onOperationComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Move right")
                Spacer(modifier = Modifier.width(4.dp))
                Text("→ 移动到右侧")
            }
        }

        if (rightState.selectedFile != null) {
            Button(
                onClick = {
                    moveFile(rightState.selectedFile!!, leftState.currentDirectory)
                    onOperationComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Move left")
                Spacer(modifier = Modifier.width(4.dp))
                Text("移动到左侧 ←")
            }
        }

        // 取消选择
        Button(
            onClick = {
                leftState.selectedFile = null
                rightState.selectedFile = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cancel")
            Spacer(modifier = Modifier.width(4.dp))
            Text("取消")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalPanel(state: TerminalState, modifier: Modifier = Modifier) {
    var showSuDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        ) {
            items(state.commandHistory) { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.startsWith("#") || entry.startsWith("$"))
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            item {
                Text(
                    text = state.output,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.rootEnabled) "# " else "$ ",
                color = if (state.rootEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = state.currentCommand,
                onValueChange = { state.currentCommand = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        executeCommandAndAddToHistory(state)
                    }
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { executeCommandAndAddToHistory(state) },
                enabled = !state.isExecuting
            ) {
                Icon(Icons.Default.Send, contentDescription = "Execute")
            }
        }

        Row(modifier = Modifier.padding(8.dp)) {
            Switch(
                checked = state.rootEnabled,
                onCheckedChange = {
                    state.rootEnabled = it
                    if (it) {
                        // 自动检测su路径
                        thread {
                            state.customSuPath = findWorkingSuPath()
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.error,
                    uncheckedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                )
            )
            Text("Root权限")

            Spacer(modifier = Modifier.width(16.dp))

            Button(onClick = { showSuDialog = true }) {
                Text("设置SU路径")
            }
        }
    }

    if (showSuDialog) {
        SuPathDialog(state, onDismiss = { showSuDialog = false })
    }
}

fun executeCommandAndAddToHistory(state: TerminalState) {
    if (state.currentCommand.isNotEmpty() && !state.isExecuting) {
        state.isExecuting = true
        val command = state.currentCommand
        state.commandHistory.add("${if (state.rootEnabled) "# " else "$ "}$command")

        thread {
            val output = executeCommand(command, state)
            state.output = output
            state.commandHistory.add(output)
            state.currentCommand = ""
            state.isExecuting = false
        }
    }
}

fun executeCommand(command: String, state: TerminalState): String {
    return try {
        val process = if (state.rootEnabled) {
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

        process.waitFor()

        if (error.isNotEmpty()) {
            "$output\nError: $error"
        } else {
            output.toString()
        }
    } catch (e: Exception) {
        "Error executing command: ${e.message}\n${e.stackTraceToString()}"
    }
}

fun checkRootAccess(suPath: String): Boolean {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf(suPath, "-c", "id"))
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        output.contains("uid=0(root)")
    } catch (e: Exception) {
        false
    }
}

fun findWorkingSuPath(): String {
    val paths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/adb/magisk/bin/su",
        "/su/bin/su"
    )

    return paths.firstOrNull { checkRootAccess(it) } ?: "/system/bin/su"
}

@Composable
fun SuPathDialog(
    state: TerminalState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customPath by remember { mutableStateOf(state.customSuPath) }
    var testResult by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SU路径配置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = customPath,
                    onValueChange = { customPath = it },
                    label = { Text("SU路径") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isTesting) {
                    Text("正在测试...", color = MaterialTheme.colorScheme.primary)
                }

                if (testResult.isNotEmpty()) {
                    Text(
                        text = testResult,
                        color = if (testResult.contains("成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isTesting = true
                    thread {
                        val success = checkRootAccess(customPath)
                        testResult = if (success) {
                            state.customSuPath = customPath
                            "测试成功！路径有效"
                        } else {
                            "测试失败！路径无效或没有root权限"
                        }
                        isTesting = false
                    }
                },
                enabled = !isTesting
            ) {
                if (isTesting) {
                    Text("测试中...")
                } else {
                    Text("测试路径")
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