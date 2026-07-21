package com.mamorukomo.kamiyama.field.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ObservationStore(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE observations (
                id TEXT PRIMARY KEY,
                photo_uri TEXT NOT NULL,
                category TEXT NOT NULL,
                candidate_id TEXT,
                custom_name TEXT NOT NULL,
                note TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracy REAL,
                observed_at_millis INTEGER NOT NULL,
                environment TEXT NOT NULL,
                rarity TEXT NOT NULL,
                ai_confidence REAL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE observations ADD COLUMN ai_confidence REAL")
        }
    }

    fun loadObservations(): List<Observation> {
        return readableDatabase.query(
            "observations",
            null,
            null,
            null,
            null,
            null,
            "observed_at_millis DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Observation(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            photoUri = cursor.getString(cursor.getColumnIndexOrThrow("photo_uri")),
                            category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
                                .toCategory(),
                            candidateId = cursor.getStringOrNull("candidate_id"),
                            customName = cursor.getString(cursor.getColumnIndexOrThrow("custom_name")),
                            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                            latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude")),
                            longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude")),
                            accuracy = cursor.getFloatOrNull("accuracy"),
                            observedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("observed_at_millis")),
                            environment = cursor.getString(cursor.getColumnIndexOrThrow("environment")),
                            rarity = cursor.getString(cursor.getColumnIndexOrThrow("rarity")).toRarity(),
                            aiConfidence = cursor.getDoubleOrNull("ai_confidence"),
                        ),
                    )
                }
            }
        }
    }

    fun saveObservation(observation: Observation) {
        val values = ContentValues().apply {
            put("id", observation.id)
            put("photo_uri", observation.photoUri)
            put("category", observation.category.name)
            put("candidate_id", observation.candidateId)
            put("custom_name", observation.customName)
            put("note", observation.note)
            put("latitude", observation.latitude)
            put("longitude", observation.longitude)
            put("accuracy", observation.accuracy)
            put("observed_at_millis", observation.observedAtMillis)
            put("environment", observation.environment)
            put("rarity", observation.rarity.name)
            put("ai_confidence", observation.aiConfidence)
        }
        writableDatabase.insertWithOnConflict(
            "observations",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteObservation(id: String) {
        writableDatabase.delete("observations", "id = ?", arrayOf(id))
    }

    private fun String.toCategory(): SpeciesCategory {
        return runCatching { SpeciesCategory.valueOf(this) }.getOrDefault(SpeciesCategory.Plant)
    }

    private fun String.toRarity(): Rarity {
        return runCatching { Rarity.valueOf(this) }.getOrDefault(Rarity.Common)
    }

    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.getFloatOrNull(columnName: String): Float? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getFloat(index)
    }

    private fun android.database.Cursor.getDoubleOrNull(columnName: String): Double? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getDouble(index)
    }

    companion object {
        private const val DATABASE_NAME = "kamiyama-field-guide.db"
        private const val DATABASE_VERSION = 2
    }
}
