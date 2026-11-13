package com.stephenmorris.drums.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The patterns table. */
@Dao
interface PatternDao {
    /** Every pattern, oldest first: the library's order. */
    @Query("SELECT * FROM patterns ORDER BY createdAt")
    fun all(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM patterns WHERE uuid = :uuid")
    fun byUuid(uuid: String): Flow<PatternEntity?>

    @Upsert
    suspend fun upsert(vararg patterns: PatternEntity)

    @Query("DELETE FROM patterns WHERE uuid = :uuid")
    suspend fun delete(uuid: String)

    @Query("SELECT COUNT(*) FROM patterns")
    suspend fun count(): Int
}
