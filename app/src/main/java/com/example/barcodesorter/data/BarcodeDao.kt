package com.example.barcodesorter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM barcodes ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<BarcodeItem>>

    @Query("SELECT * FROM barcodes WHERE area = :area ORDER BY scannedAt DESC")
    fun observeByArea(area: String): Flow<List<BarcodeItem>>

    @Query("SELECT * FROM barcodes WHERE code = :code LIMIT 1")
    suspend fun find(code: String): BarcodeItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BarcodeItem)

    @Query("DELETE FROM barcodes WHERE code = :code")
    suspend fun delete(code: String)

    @Query("SELECT * FROM areas ORDER BY name")
    fun observeAreas(): Flow<List<AreaEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addArea(area: AreaEntity)

    @Query("DELETE FROM areas WHERE name = :name")
    suspend fun deleteArea(name: String)

    @Query("SELECT COUNT(*) FROM barcodes WHERE area = :area")
    suspend fun countArea(area: String): Int

    @Query("SELECT * FROM barcodes ORDER BY area, code")
    suspend fun allForExport(): List<BarcodeItem>
}
