package org.litespace.browser.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.litespace.browser.domain.LiteApp

@Entity(tableName = "lite_apps")
data class LiteAppEntity(@PrimaryKey val id: String, @Embedded(prefix = "config_") val configuration: LiteApp)

@Dao
interface LiteAppDao {
    @Query("SELECT * FROM lite_apps ORDER BY config_favorite DESC, config_name COLLATE NOCASE") fun observe(): Flow<List<LiteAppEntity>>
    @Query("SELECT * FROM lite_apps WHERE id = :id") suspend fun get(id: String): LiteAppEntity?
    @Query("SELECT * FROM lite_apps") suspend fun all(): List<LiteAppEntity>
    @Upsert suspend fun save(app: LiteAppEntity)
    @Upsert suspend fun saveAll(apps: List<LiteAppEntity>)
    @Query("DELETE FROM lite_apps WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [LiteAppEntity::class], version = 1, exportSchema = true)
abstract class LiteDatabase : RoomDatabase() { abstract fun apps(): LiteAppDao }
