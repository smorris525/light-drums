package com.stephenmorris.drums.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.PatternText
import com.stephenmorris.drums.core.SamplePatterns
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.buildDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The library's patterns (spec §3), saved locally. Every edit saves at once. The sample patterns are seeded once,
 * on first launch, and only into an empty library.
 */
class PatternStore(
    private val dao: PatternDao,
    private val isSeeded: suspend () -> Boolean,
    private val markSeeded: suspend () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Every pattern, oldest first. */
    val patterns: Flow<List<PatternData>> = dao.all().map { rows -> rows.map(PatternEntity::toData) }

    fun pattern(uuid: String): Flow<PatternData?> = dao.byUuid(uuid).map { it?.toData() }

    suspend fun seedIfNeeded() {
        if (isSeeded()) {
            return
        }
        if (dao.count() == 0) {
            dao.upsert(*SamplePatterns.seeded(now()).map(PatternEntity::from).toTypedArray())
        }
        markSeeded()
    }

    /** A new, empty pattern at the bottom of the library, named "New Pattern" or the lowest free "New Pattern n". */
    suspend fun create(): PatternData {
        val existing = patterns.first()
        val pattern = PatternData(
            uuid = UUID.randomUUID().toString(),
            name = PatternText.newPatternTitle(existing.map { it.name }),
            createdAt = PatternText.nextCreatedAt(now(), existing),
        )
        dao.upsert(PatternEntity.from(pattern))
        return pattern
    }

    /** Saves [pattern] as edited now. */
    suspend fun save(pattern: PatternData) {
        dao.upsert(PatternEntity.from(pattern.copy(editedAt = now())))
    }

    suspend fun delete(uuid: String) = dao.delete(uuid)

    /** An independent copy at the bottom of the library, named with the lowest free suffix from 2. */
    suspend fun duplicate(uuid: String): PatternData? = pattern(uuid).first()?.let { duplicate(it) }

    /** A copy of [source] as given (the newest edit, which may not have reached the store yet). */
    suspend fun duplicate(source: PatternData): PatternData {
        val existing = patterns.first()
        val copy = source.copy(
            uuid = UUID.randomUUID().toString(),
            name = PatternText.duplicateTitle(source.name, existing.map { it.name }),
            createdAt = PatternText.nextCreatedAt(now(), existing),
            editedAt = 0,
        )
        dao.upsert(PatternEntity.from(copy))
        return copy
    }

    companion object {
        private val SEEDED = booleanPreferencesKey("seeded")

        /** The store in the tool's database, with its seeded flag in the tool's preferences. */
        fun open(context: SealedLightContext): PatternStore {
            val database = context.buildDatabase(DrumsDatabase::class.java, "drums.db")
            return PatternStore(
                database.patterns(),
                isSeeded = { context.dataStore.data.first()[SEEDED] == true },
                markSeeded = { context.dataStore.edit { it[SEEDED] = true } },
            )
        }
    }
}
