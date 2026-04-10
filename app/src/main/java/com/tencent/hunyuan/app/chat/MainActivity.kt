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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainUI() {
    var uiMode by remember { mutableStateOf("file") }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiMode == "file") "文件管理器" else "终端") },
                actions = {
                    IconButton(onClick = { uiMode = if (uiMode == "file") "terminal" else "file" }) {
                        Icon(if (uiMode == "file") Icons.Default.Terminal else Icons.Default.Folder, "切换")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, "关于")
                    }
                }
            )
        }
    ) { pad ->
        if (uiMode == "file") {
            FileManagerScreen(Modifier.padding(pad))
        } else {
            TerminalScreen(Modifier.padding(pad))
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = { Text("自动 Root 适配文件管理器\n修复终端su卡死") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("确定") } }
        )
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

suspend fun execCommandSafe(
    command: String,
    directory: String,
    useRoot: Boolean
): String {
    return withContext(Dispatchers.IO) {
        try {
            val dir = File(directory)
            val process = if (useRoot) {
                ProcessBuilder("su", "-c", command)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start()
            } else {
                ProcessBuilder("sh", "-c", command)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start()
            }

            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return@withContext "错误：命令超时"
            }

            BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText().trim()
            }
        } catch (e: Exception) {
            "错误：${e.message}"
        }
    }
}

suspend fun getFilesAuto(path: String): List<File> {
    val root = hasRoot()
    return withContext(Dispatchers.IO) {
        try {
            if (root) {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -a $path"))
                val r = BufferedReader(InputStreamReader(p.inputStream))
                val list = mutableListOf<File>()
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    val name = line?.trim() ?: continue
                    if (name == "." || name == "..") continue
                    list.add(File(path, name))
                }
                r.close()
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
fun FileManagerScreen(modifier: Modifier = Modifier) {
    var left by remember { mutableStateOf(FileManagerState()) }
    var right by remember { mutableStateOf(FileManagerState(currentDirectory = "/")) }
    val scope = rememberCoroutineScope()

    fun refresh(target: FileManagerState, update: (FileManagerState) -> Unit) {
        scope.launch {
            val all = getFilesAuto(target.currentDirectory)
            update(
                target.copy(
                    directories = all.filter { it.isDirectory },
                    files = all.filter { it.isFile }
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        refresh(left) { left = it }
        refresh(right) { right = it }
    }

    Column(modifier.fillMaxSize()) {
        LazyRow(Modifier.fillMaxWidth().padding(8.dp)) {
            items(listOf(
                "下载" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                "图片" to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath,
                "存储" to Environment.getExternalStorageDirectory().absolutePath,
                "根目录" to "/"
            )) { (name, path) ->
                Card({
                    left = left.copy(currentDirectory = path)
                    refresh(left) { left = it }
                }, Modifier.padding(4.dp)) {
                    Row(Modifier.padding(8.dp)) { Text(name) }
                }
            }
        }

        Row(Modifier.weight(1f)) {
            FilePanel(
                state = left,
                modifier = Modifier.weight(1f),
                onNavigate = { f ->
                    left = left.copy(currentDirectory = f.absolutePath)
                    refresh(left) { left = it }
                },
                onSelect = { f ->
                    left = left.copy(selectedFile = if (left.selectedFile == f) null else f)
                }
            )

            FilePanel(
                state = right,
                modifier = Modifier.weight(1f),
                onNavigate = { f ->
                    right = right.copy(currentDirectory = f.absolutePath)
                    refresh(right) { right = it }
                },
                onSelect = { f ->
                    right = right.copy(selectedFile = if (right.selectedFile == f) null else f)
                }
            )
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                refresh(left) { left = it }
                refresh(right) { right = it }
            }) { Text("刷新") }
            Button(onClick = {
                left = left.copy(selectedFile = null)
                right = right.copy(selectedFile = null)
            }) { Text("取消选择") }
        }
    }
}

@Composable
fun FilePanel(
    state: FileManagerState,
    modifier: Modifier,
    onNavigate: (File) -> Unit,
    onSelect: (File) -> Unit
) {
    Column(modifier.padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val parent = File(state.currentDirectory).parentFile ?: return@IconButton
                onNavigate(parent)
            }) { Icon(Icons.Default.ArrowBack, "返回") }
            Text("路径: ${state.currentDirectory}", Modifier.weight(1f), maxLines = 1)
        }

        LazyColumn(Modifier.weight(1f)) {
            item { Text("目录 (${state.directories.size})", Modifier.padding(vertical = 4.dp)) }
            items(state.directories) { dir ->
                FileItem(dir, true, state.selectedFile == dir, { onNavigate(dir) }, { onSelect(dir) })
            }

            item { Text("文件 (${state.files.size})", Modifier.padding(vertical = 4.dp)) }
            items(state.files) { f ->
                FileItem(f, false, state.selectedFile == f, {}, { onSelect(f) })
            }
        }
    }
}

@Composable
fun FileItem(file: File, isDir: Boolean, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick)
            .clickable { onLongClick() },
        colors = CardDefaults.cardColors(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isDir) Icons.Default.Folder else Icons.Default.Description,
                modifier = Modifier.size(32.dp),
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(file.name, fontWeight = FontWeight.Medium)
                Text(
                    text = if (isDir) "${file.listFiles()?.size ?: 0} 项" else formatSize(file.length()),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun formatSize(s: Long): String {
    return when {
        s < 1024 -> "$s B"
        s < 1024*1024 -> "${s/1024} KB"
        s < 1024*1024*1024 -> "${s/1024/1024} MB"
        else -> "${s/1024/1024/1024} GB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf(TerminalState()) }
    val scope = rememberCoroutineScope()
    var rootAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootAvailable = hasRoot()
    }

    fun exec() {
        val cmd = state.currentCommand.trim()
        if (cmd.isBlank() || state.isExecuting) return

        state = state.copy(
            currentCommand = "",
            isExecuting = true,
            commandHistory = (state.commandHistory + "\$ $cmd").toMutableList()
        )

        scope.launch {
            val useRoot = rootAvailable
            val res = execCommandSafe(cmd, state.currentDirectory, useRoot)

            state = state.copy(
                isExecuting = false,
                commandHistory = (state.commandHistory + res.lines()).toMutableList()
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        Surface(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            LazyColumn(Modifier.padding(12.dp), reverseLayout = true) {
                items(state.commandHistory.size) { i ->
                    val t = state.commandHistory[i]
                    Text(
                        t,
                        color = when {
                            t.startsWith("$") -> MaterialTheme.colorScheme.primary
                            t.startsWith("错误") -> Color.Red
                            else -> Color.Unspecified
                        }
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.currentCommand,
                onValueChange = { state = state.copy(currentCommand = it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(if (rootAvailable) "ROOT已就绪" else "普通模式") },
                keyboardActions = KeyboardActions(onDone = { exec() })
            )
            Button(
                onClick = { exec() },
                Modifier.width(80.dp),
                enabled = !state.isExecuting
            ) {
                if (state.isExecuting) {
                    CircularProgressIndicator(Modifier.size(16.dp), Color.White)
                } else {
                    Icon(Icons.Default.Send, null)
                }
            }
        }

        Button(
            onClick = { state = state.copy(commandHistory = mutableStateListOf()) },
            Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Text("清空输出")
        }
    }
}