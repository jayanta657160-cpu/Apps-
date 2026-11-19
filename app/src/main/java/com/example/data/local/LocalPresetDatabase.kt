package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.GradingRecipe
import com.example.model.ParticleStyle
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_presets")
data class LocalPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val tealShadows: Float,
    val warmHighlights: Float,
    val deepGreens: Float,
    val contrast: Float,
    val exposure: Float,
    val vibrance: Float,
    val skinProtection: Float,
    val highlightSoftness: Float,
    val adaptiveLighting: Boolean,
    val particleStyle: String,
    val particleDensity: Float,
    val particleGlow: Float,
    val particleSpeed: Float,
    val bakeParticlesOnExport: Boolean,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toRecipe(): GradingRecipe = GradingRecipe(
        name = name.ifBlank { "Custom Preset" },
        tealShadows = tealShadows.coerceIn(0f, 1f),
        warmHighlights = warmHighlights.coerceIn(0f, 1f),
        deepGreens = deepGreens.coerceIn(0f, 1f),
        contrast = contrast.coerceIn(0f, 1f),
        exposure = exposure.coerceIn(-1f, 1f),
        vibrance = vibrance.coerceIn(0f, 1f),
        skinProtection = skinProtection.coerceIn(0f, 1f),
        highlightSoftness = highlightSoftness.coerceIn(0f, 1f),
        adaptiveLighting = adaptiveLighting,
        particleStyle = ParticleStyle.fromRaw(particleStyle),
        particleDensity = particleDensity.coerceIn(0f, 1f),
        particleGlow = particleGlow.coerceIn(0f, 1f),
        particleSpeed = particleSpeed.coerceIn(0f, 1f),
        bakeParticlesOnExport = bakeParticlesOnExport
    )

    companion object {
        fun fromRecipe(
            name: String,
            description: String,
            recipe: GradingRecipe
        ): LocalPresetEntity = LocalPresetEntity(
            name = name.trim().take(60),
            description = description.trim().take(180),
            tealShadows = recipe.tealShadows.coerceIn(0f, 1f),
            warmHighlights = recipe.warmHighlights.coerceIn(0f, 1f),
            deepGreens = recipe.deepGreens.coerceIn(0f, 1f),
            contrast = recipe.contrast.coerceIn(0f, 1f),
            exposure = recipe.exposure.coerceIn(-1f, 1f),
            vibrance = recipe.vibrance.coerceIn(0f, 1f),
            skinProtection = recipe.skinProtection.coerceIn(0f, 1f),
            highlightSoftness = recipe.highlightSoftness.coerceIn(0f, 1f),
            adaptiveLighting = recipe.adaptiveLighting,
            particleStyle = recipe.particleStyle.name,
            particleDensity = recipe.particleDensity.coerceIn(0f, 1f),
            particleGlow = recipe.particleGlow.coerceIn(0f, 1f),
            particleSpeed = recipe.particleSpeed.coerceIn(0f, 1f),
            bakeParticlesOnExport = recipe.bakeParticlesOnExport,
            updatedAtMillis = System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "captured_photos")
data class CapturedPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uriString: String,
    val recipeName: String,
    val particleStyle: String,
    val capturedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface LocalPresetDao {
    @Query("SELECT * FROM local_presets ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<LocalPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: LocalPresetEntity): Long

    @Query("DELETE FROM local_presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CapturedPhotoDao {
    @Query("SELECT * FROM captured_photos ORDER BY capturedAtMillis DESC")
    fun observeAll(): Flow<List<CapturedPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: CapturedPhotoEntity): Long

    @Query("DELETE FROM captured_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(
    entities = [LocalPresetEntity::class, CapturedPhotoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AureliaLocalDatabase : RoomDatabase() {
    abstract fun localPresetDao(): LocalPresetDao
    abstract fun capturedPhotoDao(): CapturedPhotoDao

    companion object {
        @Volatile
        private var instance: AureliaLocalDatabase? = null

        fun getInstance(context: Context): AureliaLocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AureliaLocalDatabase::class.java,
                    "aurelia_local_presets.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

class LocalPresetRepository(private val dao: LocalPresetDao) {
    fun observePresets(): Flow<List<LocalPresetEntity>> = dao.observeAll()

    suspend fun savePreset(
        name: String,
        description: String,
        recipe: GradingRecipe
    ): Long {
        val cleanName = name.trim().take(60)
        require(cleanName.isNotEmpty()) { "Preset name cannot be empty." }
        return dao.insert(LocalPresetEntity.fromRecipe(cleanName, description, recipe))
    }

    suspend fun deletePreset(id: Long) {
        dao.deleteById(id)
    }
}

class CapturedPhotoRepository(private val dao: CapturedPhotoDao) {
    fun observeCapturedPhotos(): Flow<List<CapturedPhotoEntity>> = dao.observeAll()

    suspend fun recordCapture(
        uriString: String,
        recipeName: String,
        particleStyle: String
    ): Long {
        return dao.insert(
            CapturedPhotoEntity(
                uriString = uriString,
                recipeName = recipeName.trim().ifEmpty { "Teal & Warm" },
                particleStyle = particleStyle,
                capturedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCapture(id: Long) {
        dao.deleteById(id)
    }
}
