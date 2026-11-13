package com.stephenmorris.drums.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [PatternDao], ordered by `createdAt` as the real one is. */
class FakePatternDao : PatternDao {
    val rows = MutableStateFlow<List<PatternEntity>>(emptyList())

    override fun all(): Flow<List<PatternEntity>> = rows.map { list -> list.sortedBy { it.createdAt } }

    override fun byUuid(uuid: String): Flow<PatternEntity?> = rows.map { list -> list.firstOrNull { it.uuid == uuid } }

    override suspend fun upsert(vararg patterns: PatternEntity) {
        val replaced = patterns.map { it.uuid }.toSet()
        rows.value = rows.value.filterNot { it.uuid in replaced } + patterns
    }

    override suspend fun delete(uuid: String) {
        rows.value = rows.value.filterNot { it.uuid == uuid }
    }

    override suspend fun count(): Int = rows.value.size
}
