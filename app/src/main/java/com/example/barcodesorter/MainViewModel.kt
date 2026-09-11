package com.example.barcodesorter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.barcodesorter.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScanResult(
    val code: String,
    val message: String,
    val needsAddConfirm: Boolean = false,
    val needsMoveConfirm: Boolean = false,
    val isDuplicate: Boolean = false,
    val oldArea: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    val allItems = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val areas = dao.observeAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedArea = MutableStateFlow("")
    val selectedArea = _selectedArea.asStateFlow()

    init {
        viewModelScope.launch {
            areas.collect { currentAreas ->
                val selected = _selectedArea.value
                if (selected.isBlank() || currentAreas.none { it.name == selected }) {
                    _selectedArea.value = currentAreas.firstOrNull()?.name ?: ""
                }
            }
        }
    }

    fun selectArea(area: String) {
        _selectedArea.value = area
    }

    fun addArea(raw: String) {
        val name = raw.trim().uppercase()
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.addArea(AreaEntity(name))
            _selectedArea.value = name
        }
    }

    fun deleteArea(name: String, deleteContents: Boolean = true) {
        viewModelScope.launch {
            if (deleteContents) dao.deleteByArea(name)
            dao.deleteArea(name)
            if (_selectedArea.value == name) {
                _selectedArea.value = ""
            }
        }
    }

    suspend fun classify(codeRaw: String): ScanResult {
        val code = codeRaw.trim()
        if (code.isBlank()) return ScanResult("", "讀不到條碼")

        val target = selectedArea.value
        if (target.isBlank()) {
            return ScanResult(code, "請先新增並選擇一個區域")
        }

        val current = dao.find(code)

        if (current == null) {
            return ScanResult(
                code = code,
                message = "掃描到 $code，是否加入 $target？",
                needsAddConfirm = true
            )
        }

        if (current.area == target) {
            return ScanResult(
                code = code,
                message = "重複掃描：$code 已經在 $target",
                isDuplicate = true
            )
        }

        return ScanResult(
            code = code,
            message = "$code 已登記在 ${current.area}",
            needsMoveConfirm = true,
            oldArea = current.area
        )
    }

    suspend fun confirmAdd(code: String): ScanResult {
        val target = selectedArea.value
        if (target.isBlank()) return ScanResult(code, "請先新增並選擇一個區域")
        dao.upsert(BarcodeItem(code = code, area = target))
        return ScanResult(code, "$code → $target 已加入")
    }

    suspend fun move(code: String): ScanResult {
        val target = selectedArea.value
        if (target.isBlank()) return ScanResult(code, "請先新增並選擇一個區域")
        dao.upsert(BarcodeItem(code = code, area = target))
        return ScanResult(code, "$code 已移到 $target")
    }

    suspend fun lookup(codeRaw: String): BarcodeItem? =
        dao.find(codeRaw.trim())

    fun deleteBarcode(code: String) {
        viewModelScope.launch { dao.delete(code) }
    }

    suspend fun exportRows(): List<BarcodeItem> = dao.allForExport()
}
