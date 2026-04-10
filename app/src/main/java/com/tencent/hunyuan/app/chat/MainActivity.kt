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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Base64
import java.util.concurrent.TimeUnit

// Miuix 核心
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
fun formatFileDate(timeMillis: Long): String {
    return SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

// ====================== 🔒 CoverShell 核心隐身工具类 ======================
object CoverShellSecurity {
    private const val ENCODED_SU = "c3U="
    private const val ENCODED_C = "LWM="
    private const val ENCODED_ID = "aWQ="
    private const val ENCODED_LS_L = "bHMgLWw="
    private const val ENCODED_LS_A = "bHMgLWE="

    private fun decode(encoded: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String(Base64.getDecoder().decode(encoded))
        } else {
            android.util.Base64.decode(encoded, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
        }
    }

    fun getSuArgs(): Array<String> = arrayOf(decode(ENCODED_SU), decode(ENCODED_C))
    fun getRootCheckCmd(): String = decode(ENCODED_ID)
    fun getFilePermCmd(path: String): String = "${decode(ENCODED_LS_L)} $path | head -n1"
    fun getListFileCmd(path: String): String = "${decode(ENCODED_LS_A)} $path"

    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.MODEL.contains("Emulator")
                || Build.MANUFACTURER.contains("Genymotion"))
    }

    fun silentError(): String = "-?????????"
}

// ========================= 🔥 修复：正确识别文件夹（包括 /data/adb） =========================
suspend fun isDirectory(file: File): Boolean {
    return if (file.canRead()) {
        file.isDirectory
    } else {
        withContext(Dispatchers.IO) {
            try {
                val p = ProcessBuilder("su", "-c", "test -d ${file.absolutePath} && echo 1").start()
                val success = p.inputStream.bufferedReader().readLine() == "1"
                p.waitFor()
                p.inputStream.close()
                p.errorStream.close()
                p.destroy()
                success
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * 🔒 ROOT下获取文件权限字符串（无明文命令）
 */
suspend fun getFilePermission(file: File): String {
    return withContext(Dispatchers.IO) {
        try {
            val path = file.absolutePath
            val cmd = CoverShellSecurity.getFilePermCmd(path)
            val process = ProcessBuilder(*CoverShellSecurity.getSuArgs(), cmd).start()
            val line = BufferedReader(InputStreamReader(process.inputStream)).readLine()

            process.waitFor(1, TimeUnit.SECONDS)
            process.inputStream.close()
            process.errorStream.close()
            process.destroy()

            line?.split(" ")?.firstOrNull() ?: CoverShellSecurity.silentError()
        } catch (e: Exception) {
            CoverShellSecurity.silentError()
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
    val commandHistory: List<String> = emptyList(),
    val currentCommand: String = "",
    val isExecuting: Boolean = false,
    val currentDirectory: String = Environment.getExternalStorageDirectory().absolutePath
)

class MainActivity : ComponentActivity() {
    private val requestStorage: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onResume() {
        super.onResume()
        kotlinx.coroutines.MainScope().launch {
            delay((500L..1500L).random())
            checkPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CoverShellSecurity.isEmulator()) {
            setContent {
                MiuixTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("普通文件管理器")
                    }
                }
            }
            return
        }

        setContent {
            MiuixTheme {
                Box(Modifier.fillMaxSize()) {
                    MainUI()
                }
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

// ====================== 主UI ======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainUI() {
    var uiMode by remember { mutableStateOf("file") }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ROOT 文件管理器") },
                actions = {
                    IconButton(onClick = { uiMode = if (uiMode == "file") "terminal" else "file" }) {
                        Icon(if (uiMode == "file") Icons.Default.Terminal else Icons.Default.Folder, contentDescription = "切换模式")
                    }
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
suspend fun hasRoot(): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            delay((300L..800L).random())
            val process = ProcessBuilder(*CoverShellSecurity.getSuArgs(), CoverShellSecurity.getRootCheckCmd()).start()
            val result = BufferedReader(InputStreamReader(process.inputStream)).readText()

            process.waitFor(1, TimeUnit.SECONDS)
            process.inputStream.close()
            process.errorStream.close()
            process.destroy()

            result.contains("uid=0(root)")
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 🔒 修复：ROOT 读取文件列表
 */
suspend fun getFilesWithRoot(path: String): List<File> {
    return withContext(Dispatchers.IO) {
        try {
            val cmd = CoverShellSecurity.getListFileCmd(path)
            val process = ProcessBuilder(*CoverShellSecurity.getSuArgs(), cmd).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val fileNames = mutableListOf<String>()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val name = line?.trim()
                if (!name.isNullOrBlank() && name != "." && name != "..") {
                    fileNames.add(name)
                }
            }

            process.waitFor(1, TimeUnit.SECONDS)
            reader.close()
            process.inputStream.close()
            process.errorStream.close()
            process.destroy()

            fileNames.map { File("$path/$it") }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

suspend fun runRootCommand(command: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(*CoverShellSecurity.getSuArgs(), command).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()

            val finished = process.waitFor(2, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()

            process.inputStream.close()
            process.errorStream.close()
            process.destroy()

            output + error
        } catch (e: Exception) {
            ""
        }
    }
}

suspend fun getFilesAuto(path: String): List<File> {
    val rootFiles = getFilesWithRoot(path)
    if (rootFiles.isNotEmpty()) return rootFiles
    return withContext(Dispatchers.IO) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) dir.listFiles()?.toList() ?: emptyList() else emptyList()
    }
}

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
    var leftPanelState by remember { mutableStateOf(FileManagerState()) }
    var rightPanelState by remember { mutableStateOf(FileManagerState(currentDirectory = "/")) }
    val coroutineScope = rememberCoroutineScope()

    fun refreshFileList(currentState: FileManagerState, updateState: (FileManagerState) -> Unit) {
        coroutineScope.launch {
            val fileList = getFilesAuto(currentState.currentDirectory)
            val dirs = mutableListOf<File>()
            val files = mutableListOf<File>()

            for (file in fileList) {
                if (isDirectory(file)) {
                    dirs.add(file)
                } else {
                    files.add(file)
                }
            }

            updateState(currentState.copy(directories = dirs, files = files))
        }
    }

    LaunchedEffect(Unit) {
        refreshFileList(leftPanelState) { leftPanelState = it }
        refreshFileList(rightPanelState) { rightPanelState = it }
    }

    Row(Modifier.fillMaxSize()) {
        FilePanel(
            state = leftPanelState,
            modifier = Modifier.weight(1f),
            onNavigate = { file ->
                coroutineScope.launch {
                    if (isDirectory(file)) {
                        leftPanelState = leftPanelState.copy(currentDirectory = file.absolutePath)
                        refreshFileList(leftPanelState) { leftPanelState = it }
                    }
                }
            },
            onSelect = {
                leftPanelState = leftPanelState.copy(
                    selectedFile = if (leftPanelState.selectedFile == it) null else it
                )
            },
            onRefresh = { refreshFileList(leftPanelState) { leftPanelState = it } }
        )

        FilePanel(
            state = rightPanelState,
            modifier = Modifier.weight(1f),
            onNavigate = { file ->
                coroutineScope.launch {
                    if (isDirectory(file)) {
                        rightPanelState = rightPanelState.copy(currentDirectory = file.absolutePath)
                        refreshFileList(rightPanelState) { rightPanelState = it }
                    }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "路径: ${state.currentDirectory}",
                modifier = Modifier.weight(1f).basicMarquee(animationMode = MarqueeAnimationMode.Immediately),
                maxLines = 1
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().overScrollVertical().scrollEndHaptic(),
            overscrollEffect = null
        ) {
            item { BackFolderItem(parentDir = parentDir, onNavigate = onNavigate) }
            item { Text("目录 (${state.directories.size})", Modifier.padding(vertical = 4.dp)) }
            items(state.directories) { dir ->
                FileItem(dir, true, state.selectedFile == dir, { onNavigate(dir) }, { onSelect(dir) })
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("文件 (${state.files.size})", Modifier.padding(vertical = 4.dp)) }
            items(state.files) { file ->
                FileItem(file, false, state.selectedFile == file, {}, { onSelect(file) })
            }
        }
    }
}

@Composable
fun BackFolderItem(parentDir: File?, onNavigate: (File) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .pressable(interactionSource, TiltFeedback())
            .clickable { parentDir?.let { onNavigate(it) } }
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowUpward, null, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text("..", fontSize = 18.sp)
        }
    }
}

@Composable
fun FileItem(file: File, isDir: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    var permission by remember { mutableStateOf("-?????????") }
    val size = formatSize(file.length())
    val date = formatFileDate(file.lastModified())

    LaunchedEffect(file) {
        if (!isDir) permission = getFilePermission(file)
    }

    Surface(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .pressable(interactionSource, TiltFeedback())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDir) Icons.Default.Folder else Icons.Default.Description, null, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(file.name)
                if (isDir) Text(date) else Text("$permission $size")
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

    LaunchedEffect(Unit) {
        delay((1000L..2000L).random())
        isRootAvailable = hasRoot()
    }

    fun executeCommand() {
        val command = terminalState.currentCommand.trim()
        if (command.isBlank() || terminalState.isExecuting) return

        terminalState = terminalState.copy(currentCommand = "", isExecuting = true)

        coroutineScope.launch {
            val result = runRootCommand(command)
            val newHistory = terminalState.commandHistory + "\$ $command\n$result"
            terminalState = terminalState.copy(isExecuting = false, commandHistory = newHistory)
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Surface(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.padding(12.dp).overScrollVertical().scrollEndHaptic(), reverseLayout = true) {
                items(terminalState.commandHistory) { Text(it) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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

        Button(onClick = { terminalState = terminalState.copy(commandHistory = emptyList()) }, Modifier.fillMaxWidth()) {
            Text("清空输出")
        }
    }
}