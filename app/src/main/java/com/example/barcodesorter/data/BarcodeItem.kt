package com.example.barcodesorter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barcodes")
data class BarcodeItem(
    @PrimaryKey val code: String,
    val area: String,
    val scannedAt: Long = System.currentTimeMillis()
)
