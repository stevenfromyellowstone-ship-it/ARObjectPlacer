package com.arobjectplacer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.room.*
import com.arobjectplacer.utils.BitmapUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Dao
interface CapturedObjectDao {
    @Query("SELECT * FROM captured_objects ORDER BY capture_timestamp DESC")
    fun getAllObjects(): Flow<List<CapturedObject>>

    @Query("SELECT * FROM captured_objects WHERE id = :id")
    suspend fun getObjectById(id: Long): CapturedObject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObject(obj: CapturedObject): Long

    @Delete
    suspend fun deleteObject(obj: CapturedObject)
}

@Database(entities = [CapturedObject::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capturedObjectDao(): CapturedObjectDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "ar_object_placer_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

class ObjectStore(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.capturedObjectDao()
    private val gson = Gson()

    private val objectsDir: File
        get() = File(context.filesDir, "captured_objects").also { if (!it.exists()) it.mkdirs() }

    fun getAllObjects(): Flow<List<CapturedObject>> = dao.getAllObjects()
    suspend fun getObject(id: Long): CapturedObject? = dao.getObjectById(id)

    suspend fun saveObject(
        name: String, image: Bitmap, mask: Bitmap?,
        widthMeters: Float, heightMeters: Float, depthMeters: Float,
        contourPoints: List<PointF>? = null
    ): Long = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val objectDir = File(objectsDir, uuid).also { it.mkdirs() }

        val imagePath = File(objectDir, "image.png").absolutePath
        saveBitmap(image, imagePath)

        val maskPath = mask?.let {
            File(objectDir, "mask.png").absolutePath.also { path -> saveBitmap(mask, path) }
        }

        val thumbnailPath = File(objectDir, "thumbnail.jpg").absolutePath
        Bitmap.createScaledBitmap(image, 256, 256, true).let {
            saveBitmap(it, thumbnailPath, Bitmap.CompressFormat.JPEG, 85)
            it.recycle()
        }

        val texturePath = mask?.let {
            File(objectDir, "texture.png").absolutePath.also { path ->
                BitmapUtils.applyMask(image, mask).let { tex ->
                    saveBitmap(tex, path)
                    tex.recycle()
                }
            }
        } ?: imagePath

        dao.insertObject(CapturedObject(
            name = name, imagePath = imagePath, maskPath = maskPath,
            widthMeters = widthMeters, heightMeters = heightMeters, depthMeters = depthMeters,
            thumbnailPath = thumbnailPath, texturePath = texturePath,
            contourData = contourPoints?.let { gson.toJson(it) }
        ))
    }

    suspend fun deleteObject(obj: CapturedObject) = withContext(Dispatchers.IO) {
        File(obj.imagePath).parentFile?.deleteRecursively()
        dao.deleteObject(obj)
    }

    fun loadObjectTexture(obj: CapturedObject): Bitmap? =
        BitmapFactory.decodeFile(obj.texturePath ?: obj.imagePath)

    private fun saveBitmap(
        bitmap: Bitmap, path: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 100
    ) = FileOutputStream(path).use { bitmap.compress(format, quality, it) }
}
