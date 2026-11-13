package com.stephenmorris.drums.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stephenmorris.drums.core.MeterId
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.StepGrid
import com.stephenmorris.drums.core.Swing
import com.stephenmorris.drums.core.fromJson
import com.stephenmorris.drums.core.toJson

/** A pattern as Room stores it (spec §3). Meter and swing are their raw values; the steps are JSON. */
@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val createdAt: Long,
    val editedAt: Long,
    val bpm: Int,
    val meter: String,
    val swing: String,
    val bars: Int,
    val stepsJson: String,
) {
    /** The pattern, with unknown or corrupt values read as safe defaults. */
    fun toData(): PatternData = PatternData(
        uuid = uuid,
        name = name,
        createdAt = createdAt,
        editedAt = editedAt,
        bpm = bpm,
        meter = MeterId.fromRaw(meter),
        bars = bars,
        swing = Swing.fromRaw(swing),
        steps = StepGrid.fromJson(stepsJson),
    )

    companion object {
        fun from(pattern: PatternData) = PatternEntity(
            uuid = pattern.uuid,
            name = pattern.name,
            createdAt = pattern.createdAt,
            editedAt = pattern.editedAt,
            bpm = pattern.bpm,
            meter = pattern.meter.raw,
            swing = pattern.swing.raw,
            bars = pattern.bars,
            stepsJson = pattern.steps.toJson(),
        )
    }
}
