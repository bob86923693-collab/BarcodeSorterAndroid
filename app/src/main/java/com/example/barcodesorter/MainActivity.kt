package com.example.barcodesorter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.barcodesorter.data.BarcodeItem
import com.example.barcodesorter.scanner.BarcodeCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab { SCAN, SEARCH, AREAS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BarcodeSorterApp() } }
    }
}

@Composable
fun BarcodeSorterApp(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.SCAN) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.SCAN,
                    onClick = { tab = Tab.SCAN },
                    icon = { Icon(Icons.Default.QrCodeScanner, null) },
                    label = { Text("掃描") }
                )
                NavigationBarItem(
                    selected = tab == Tab.SEARCH,
                    onClick = { tab = Tab.SEARCH },
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text("查詢") }
                )
                NavigationBarItem(
                    selected = tab == Tab.AREAS,
                    onClick = { tab = Tab.AREAS },
                    icon = { Icon(Icons.Default.Inventory2, null) },
                    label = { Text("區域") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.SCAN -> ScanScreen(vm)
                Tab.SEARCH -> SearchScreen(vm)
                Tab.AREAS -> AreasScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val areas by vm.areas.collectAsState()
    val selectedArea by vm.selectedArea.collectAsState()

    LaunchedEffect(areas, selectedArea) {
        if (selectedArea.isBlank() && areas.isNotEmpty()) {
            vm.selectArea(areas.first().name)
        }
    }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var expanded by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (areas.isEmpty()) "請先到「區域」新增分類" else "將條碼中心對準十字")
    }
    var scannerEnabled by remember { mutableStateOf(true) }
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var pendingOldArea by remember { mutableStateOf<String?>(null) }
    var pendingAdd by remember { mutableStateOf(false) }
    var duplicateCode by remember { mutableStateOf<String?>(null) }
    var lastCode by remember { mutableStateOf("") }
    var lastAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(areas.size) {
        status = if (areas.isEmpty()) "請先到「區域」新增分類" else "將條碼中心對準十字"
    }

    fun feedback() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)
                    .defaultVibrator
                    .vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) { }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    "掃描分類",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        if (areas.isNotEmpty()) expanded = !expanded
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedArea.ifBlank { "尚無分類，請先新增" },
                        onValueChange = {},
                        readOnly = true,
                        enabled = areas.isNotEmpty(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        leadingIcon = { Icon(Icons.Default.Inventory2, null) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        areas.forEach { area ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        area.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (area.name == selectedArea) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    if (area.name == selectedArea) Icon(Icons.Default.Check, null)
                                },
                                onClick = {
                                    vm.selectArea(area.name)
                                    expanded = false
                                    status = "目前分類：${area.name}"
                                }
                            )
                        }
                    }
                }

                if (areas.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "目前沒有分類，請到下方「區域」頁新增後再掃描。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(Color.Black)
        ) {
            if (cameraGranted) {
                BarcodeCamera(
                    enabled = areas.isNotEmpty() && selectedArea.isNotBlank() &&
                        scannerEnabled && pendingCode == null && duplicateCode == null,
                    onCode = { code ->
                        val now = System.currentTimeMillis()
                        if (code == lastCode && now - lastAt < 1600) return@BarcodeCamera
                        lastCode = code
                        lastAt = now
                        scannerEnabled = false

                        scope.launch {
                            val result = vm.classify(code)
                            status = result.message
                            when {
                                result.needsAddConfirm -> {
                                    pendingCode = result.code
                                    pendingAdd = true
                                }
                                result.needsMoveConfirm -> {
                                    pendingCode = result.code
                                    pendingOldArea = result.oldArea
                                }
                                result.isDuplicate -> {
                                    duplicateCode = result.code
                                    feedback()
                                }
                                else -> {
                                    delay(400)
                                    scannerEnabled = true
                                }
                            }
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(44.dp)
                            .height(3.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(44.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }

                Text(
                    if (areas.isEmpty()) "請先新增分類" else "將條碼中心對準十字",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            } else {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.align(Alignment.Center)
                ) { Text("允許相機權限") }
            }
        }

        Text(
            status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }

    if (pendingCode != null && pendingAdd) {
        AlertDialog(
            onDismissRequest = {
                pendingCode = null
                pendingAdd = false
                scannerEnabled = true
            },
            icon = { Icon(Icons.Default.AddCircle, null) },
            title = { Text("確認加入") },
            text = { Text("條碼 $pendingCode 要加入分類 $selectedArea 嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    val code = pendingCode ?: return@TextButton
                    scope.launch {
                        val result = vm.confirmAdd(code)
                        status = result.message
                        feedback()
                        pendingCode = null
                        pendingAdd = false
                        delay(400)
                        scannerEnabled = true
                    }
                }) { Text("加入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    status = "已取消加入 $pendingCode"
                    pendingCode = null
                    pendingAdd = false
                    scannerEnabled = true
                }) { Text("取消") }
            }
        )
    }

    if (pendingCode != null && !pendingAdd && pendingOldArea != null) {
        AlertDialog(
            onDismissRequest = {
                pendingCode = null
                pendingOldArea = null
                scannerEnabled = true
            },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("條碼已在其他分類") },
            text = { Text("$pendingCode 已登記在 $pendingOldArea，要移到 $selectedArea 嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    val code = pendingCode ?: return@TextButton
                    scope.launch {
                        val result = vm.move(code)
                        status = result.message
                        feedback()
                        pendingCode = null
                        pendingOldArea = null
                        delay(400)
                        scannerEnabled = true
                    }
                }) { Text("移到 $selectedArea") }
            },
            dismissButton = {
                TextButton(onClick = {
                    status = "$pendingCode 保留在 $pendingOldArea"
                    pendingCode = null
                    pendingOldArea = null
                    scannerEnabled = true
                }) { Text("保留原分類") }
            }
        )
    }

    if (duplicateCode != null) {
        AlertDialog(
            onDismissRequest = {
                duplicateCode = null
                scannerEnabled = true
            },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("重複掃描") },
            text = { Text("條碼 $duplicateCode 已經在 $selectedArea，不會重複加入。") },
            confirmButton = {
                TextButton(onClick = {
                    duplicateCode = null
                    scannerEnabled = true
                }) { Text("知道了") }
            }
        )
    }
}

@Composable
fun SearchScreen(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<BarcodeItem?>(null) }
    var searched by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("查詢條碼位置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("例如 02964") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                scope.launch {
                    result = vm.lookup(code)
                    searched = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("查詢")
        }
        Spacer(Modifier.height(24.dp))

        if (searched) {
            if (result != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(result!!.code, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "分類：${result!!.area}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("最後更新：${formatTime(result!!.scannedAt)}")
                    }
                }
            } else {
                Text("找不到這個條碼", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun AreasScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val areas by vm.areas.collectAsState()
    val allItems by vm.allItems.collectAsState()
    var newArea by remember { mutableStateOf("") }
    var expandedArea by remember { mutableStateOf<String?>(null) }
    var areaToDelete by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val csv = buildString {
                    appendLine("barcode,area")
                    vm.exportRows().forEach { r ->
                        appendLine("${csvEscape(r.code)},${csvEscape(r.area)}")
                    }
                }
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("分類管理", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newArea,
                onValueChange = { newArea = it.uppercase() },
                label = { Text("新增分類，例如 BR17") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = {
                vm.addArea(newArea)
                newArea = ""
            }) {
                Icon(Icons.Default.Add, null)
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { exportLauncher.launch("barcode-export-${System.currentTimeMillis()}.csv") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileDownload, null)
            Spacer(Modifier.width(8.dp))
            Text("匯出 CSV")
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(areas, key = { it.name }) { area ->
                val rows = allItems.filter { it.area == area.name }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    area.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("${rows.size} 個條碼")
                            }
                            TextButton(onClick = {
                                expandedArea = if (expandedArea == area.name) null else area.name
                            }) {
                                Text(if (expandedArea == area.name) "收起" else "查看")
                            }
                            IconButton(onClick = { areaToDelete = area.name }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "刪除分類")
                            }
                        }

                        if (expandedArea == area.name) {
                            HorizontalDivider()
                            rows.take(100).forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.code, fontWeight = FontWeight.SemiBold)
                                        Text(formatTime(item.scannedAt), style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { vm.deleteBarcode(item.code) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "刪除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (areaToDelete != null) {
        val target = areaToDelete!!
        val count = allItems.count { it.area == target }
        AlertDialog(
            onDismissRequest = { areaToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, null) },
            title = { Text("刪除分類 $target？") },
            text = {
                Text(
                    if (count > 0) {
                        "這個分類內有 $count 個條碼。刪除分類時也會一起刪除這些條碼。"
                    } else {
                        "確定要刪除這個分類嗎？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteArea(target, deleteContents = true)
                    if (expandedArea == target) expandedArea = null
                    areaToDelete = null
                }) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { areaToDelete = null }) { Text("取消") }
            }
        )
    }
}

fun formatTime(epoch: Long): String =
    SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(epoch))

fun csvEscape(s: String): String =
    "\"" + s.replace("\"", "\"\"") + "\""
