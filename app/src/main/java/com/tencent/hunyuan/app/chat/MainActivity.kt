package com.tencent.hunyuan.app.chat

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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

data class FileManagerState(
    val currentDirectory: String = Environment.getExternalStorageDirectory().absolutePath,
    val files: List<File> = emptyList(),
    val directories: List<File> = emptyList(),
    val selectedFile: File? = null
)

data class TerminalState(
    val commandHistory: MutableList<String> = mutableStateListOf(),
    val currentCommand: String = "",
    val isExecuting: Boolean = false,
    val rootEnabled: Boolean = false,
    val currentDirectory: String = Environment.getExternalStorageDirectory().absolutePath
)

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FileManagerApp()
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                requestPermissionLauncher.launch(perms)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp() {
    var uiMode by remember { mutableStateOf("file") }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiMode == "file") "文件管理器" else "终端") },
                actions = {
                    IconButton(onClick = { uiMode = if (uiMode == "file") "terminal" else "file" }) {
                        Icon(if (uiMode == "file") Icons.Default.Terminal else Icons.Default.Folder, contentDescription = "切换")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
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
        AlertDialog(onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = { Text("文件管理器 + 终端\n支持Android 10+\n可浏览文件、执行命令") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("确定") } }
        )
    }
}

@Composable
fun FileManagerScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var left by remember { mutableStateOf(FileManagerState()) }
    var right by remember { mutableStateOf(FileManagerState(currentDirectory = "/")) }

    fun refresh(state: FileManagerState, update: (FileManagerState) -> Unit) {
        val dir = File(state.currentDirectory)
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: emptyArray()
        update(
            state.copy(
                directories = files.filter { it.isDirectory }.sortedBy { it.name.lowercase() },
                files = files.filter { it.isFile }.sortedBy { it.name.lowercase() }
            )
        )
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
                "内部存储" to Environment.getExternalStorageDirectory().absolutePath
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
            FilePanel(left, Modifier.weight(1f),
                onDir = { f ->
                    left = left.copy(currentDirectory = f.absolutePath)
                    refresh(left) { left = it }
                },
                onSelect = { f ->
                    left = left.copy(selectedFile = if (left.selectedFile == f) null else f)
                }
            )
            FilePanel(right, Modifier.weight(1f),
                onDir = { f ->
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
    onDir: (File) -> Unit,
    onSelect: (File) -> Unit
) {
    Column(modifier.padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val p = File(state.currentDirectory).parentFile ?: return@IconButton
                val newState = state.copy(currentDirectory = p.absolutePath)
                onDir(p)
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("路径: ${state.currentDirectory}", Modifier.weight(1f), maxLines = 1)
        }

        LazyColumn(Modifier.weight(1f)) {
            item { Text("目录 (${state.directories.size})", Modifier.padding(vertical = 4.dp)) }
            items(state.directories) { dir ->
                FileItem(dir, isDir = true, isSelected = state.selectedFile == dir, { onDir(dir) }, { onSelect(dir) })
            }
            item { Text("文件 (${state.files.size})", Modifier.padding(vertical = 4.dp)) }
            items(state.files) { f ->
                FileItem(f, isDir = false, isSelected = state.selectedFile == f, {}, { onSelect(f) })
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
            .longClick { onLongClick() },
        colors = CardDefaults.cardColors(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDir) Icons.Default.Folder else Icons.Default.Description, null, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(file.name, fontWeight = FontWeight.Medium)
                Text(
                    if (isDir) "${file.listFiles()?.size ?: 0} 项" else formatSize(file.length()),
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
    val ctx = LocalContext.current

    fun exec() {
        if (state.currentCommand.isBlank() || state.isExecuting) return
        val cmd = state.currentCommand.trim()
        state = state.copy(currentCommand = "", isExecuting = true, commandHistory = (state.commandHistory + "${if (state.rootEnabled) "#" else "$"} $cmd").toMutableList())

        scope.launch(Dispatchers.IO) {
            val result = try {
                val p = if (state.rootEnabled) Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                else Runtime.getRuntime().exec(cmd, null, File(state.currentDirectory))
                val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
                val err = BufferedReader(InputStreamReader(p.errorStream)).readText()
                out + err
            } catch (e: Exception) {
                "错误: ${e.message}"
            }

            launch(Dispatchers.Main) {
                state = state.copy(
                    isExecuting = false,
                    commandHistory = (state.commandHistory + result.lines()).toMutableList()
                )
            }
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
                itemsIndexed(state.commandHistory) { _, s ->
                    Text(s, color = if (s.startsWith("$") || s.startsWith("#")) MaterialTheme.colorScheme.primary else Color.Unspecified)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.currentCommand,
                onValueChange = { state = state.copy(currentCommand = it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("输入命令") },
                keyboardActions = KeyboardActions(onDone = { exec() })
            )
            Button(onClick = { exec() }, Modifier.width(80.dp)) {
                if (state.isExecuting) CircularProgressIndicator(Modifier.size(16.dp), Color.White)
                else Icon(Icons.Default.Send, null)
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(state.rootEnabled, { state = state.copy(rootEnabled = it) })
                Text("ROOT")
            }
            Button(onClick = { state = state.copy(commandHistory = mutableStateListOf()) }) {
                Text("清空")
            }
        }
    }
}

fun Modifier.longClick(action: () -> Unit) = clickable { action() }