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

// Compose 基础
import androidx.compose.foundation.interaction.MutableInteractionSource

data class FileManagerState(
    val currentDirectory: String = Environment.getExternalStorageDirectory().absolutePath,
    val directories: List<File> = emptyList(),
    val files: List<File> = emptyList(),
    val selectedFile: File? = null
)

data class TerminalState(
    val commandHistory: MutableList<String> = mutableStateListOf(),
    val currentCommand: String = "",
    val isExecuting: Boolean = false,
    val currentDirectory: String = Environment.getExternalStorageDirectory().absolutePath
)

class MainActivity : ComponentActivity() {

    // ✅ 修复：正确的权限请求器（无任何报错）
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

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

@Composable
fun MainUI() {
    var uiMode by remember { mutableStateOf("file") }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "ROOT 文件管理器",
                actions = {
                    IconButton(onClick = { uiMode = if (uiMode == "file") "terminal" else "file" }) {
                        Icon(if (uiMode == "file") Icons.Default.Terminal else Icons.Default.Folder, null)
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, null)
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
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

suspend fun hasRoot(): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val text = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor(1, TimeUnit.SECONDS)
            p.destroy()
            text.contains("uid=0(root)")
        } catch (e: Exception) {
            false
        }
    }
}

suspend fun getFilesAuto(path: String): List<File> {
    val root = hasRoot()
    return withContext(Dispatchers.IO) {
        try {
            if (root) {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -a $path"))
                val list = mutableListOf<File>()
                BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        val name = line?.trim() ?: continue
                        if (name == "." || name == "..") continue
                        list.add(File(path, name))
                    }
                }
                p.waitFor(1, TimeUnit.SECONDS)
                p.destroy()
                list
            } else {
                File(path).listFiles()?.toList() ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Composable
fun FileManagerScreen() {
    var left by remember { mutableStateOf(FileManagerState()) }
    var right by remember { mutableStateOf(FileManagerState(currentDirectory = "/")) }
    val scope = rememberCoroutineScope()

    fun refresh(target: FileManagerState, update: (FileManagerState) -> Unit) {
        scope.launch {
            val all = getFilesAuto(target.currentDirectory)
            update(target.copy(directories = all.filter { it.isDirectory }, files = all.filter { it.isFile }))
        }
    }

    LaunchedEffect(Unit) {
        refresh(left) { left = it }
        refresh(right) { right = it }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f)) {
            FilePanel(
                state = left, modifier = Modifier.weight(1f),
                onNavigate = {
                    left = left.copy(currentDirectory = it.absolutePath)
                    refresh(left) { left = it }
                },
                onSelect = {
                    left = left.copy(selectedFile = if (left.selectedFile == it) null else it)
                },
                onRefresh = { refresh(left) { left = it } }
            )

            FilePanel(
                state = right, modifier = Modifier.weight(1f),
                onNavigate = {
                    right = right.copy(currentDirectory = it.absolutePath)
                    refresh(right) { right = it }
                },
                onSelect = {
                    right = right.copy(selectedFile = if (right.selectedFile == it) null else it)
                },
                onRefresh = { refresh(right) { right = it } }
            )
        }
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
    Column(modifier.padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { File(state.currentDirectory).parentFile?.let { onNavigate(it) } }) {
                Icon(Icons.Default.ArrowBack, null)
            }
            Text(
                text = "路径: ${state.currentDirectory}",
                modifier = Modifier.weight(1f).basicMarquee(animationMode = MarqueeAnimationMode.Immediately),
                maxLines = 1
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(),
            overscrollEffect = null
        ) {
            item { Text("目录 (${state.directories.size})", fontWeight = FontWeight.Medium) }
            items(state.directories) { dir ->
                FileItem(dir, true, state.selectedFile == dir, { onNavigate(dir) }, { onSelect(dir) })
            }
            item { Spacer(Modifier.height(8.dp)); Text("文件 (${state.files.size})", fontWeight = FontWeight.Medium) }
            items(state.files) { f ->
                FileItem(f, false, state.selectedFile == f, {}, { onSelect(f) })
            }
        }
    }
}

@Composable
fun FileItem(
    file: File, isDir: Boolean, isSelected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

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
            Icon(if (isDir) Icons.Default.Folder else Icons.Default.Description, null, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(file.name, fontWeight = FontWeight.Medium)
                Text(if (isDir) "${file.listFiles()?.size ?: 0} 项" else formatSize(file.length()))
            }
        }
    }
}

fun formatSize(s: Long): String {
    return when {
        s < 1024 -> "$s B"
        s < 1024 * 1024 -> "${s / 1024} KB"
        s < 1024 * 1024 * 1024 -> "${s / 1024 / 1024} MB"
        else -> "${s / 1024 / 1024 / 1024} GB"
    }
}

@Composable
fun TerminalScreen() {
    var state by remember { mutableStateOf(TerminalState()) }
    val scope = rememberCoroutineScope()
    var rootAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { rootAvailable = hasRoot() }

    fun exec() {
        val cmd = state.currentCommand.trim()
        if (cmd.isBlank() || state.isExecuting) return
        state = state.copy(currentCommand = "", isExecuting = true)
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    val process = if (rootAvailable) {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    } else {
                        Runtime.getRuntime().exec(cmd)
                    }
                    BufferedReader(InputStreamReader(process.inputStream)).readText()
                } catch (e: Exception) {
                    "错误: ${e.message}"
                }
            }
            state = state.copy(isExecuting = false, commandHistory = (state.commandHistory + "\$ $cmd\n$res").toMutableList())
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Surface(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.padding(12.dp).overScrollVertical().scrollEndHaptic(),
                reverseLayout = true,
                overscrollEffect = null
            ) {
                items(state.commandHistory.size) { i ->
                    Text(state.commandHistory[i])
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = state.currentCommand,
                onValueChange = { state = state.copy(currentCommand = it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { exec() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { exec() }, enabled = !state.isExecuting) {
                Icon(Icons.Default.Send, null)
            }
        }
        Button(onClick = { state = state.copy(commandHistory = mutableStateListOf()) }, Modifier.fillMaxWidth()) {
            Text("清空输出")
        }
    }
}