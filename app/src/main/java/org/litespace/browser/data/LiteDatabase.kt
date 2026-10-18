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

@Database(entities = [LiteAppEntity::class, ToolRecord::class], version = 2, exportSchema = true)
abstract class LiteDatabase : RoomDatabase() { abstract fun apps(): LiteAppDao
    abstract fun tools(): ToolDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                for(name in listOf("tags", "scriptIds", "searchUrl", "shareUrl")) db.execSQL("ALTER TABLE lite_apps ADD COLUMN config_${name} TEXT NOT NULL DEFAULT ''")
                for(name in listOf("removeTracking", "dataSaver", "externalLinks", "incognito")) db.execSQL("ALTER TABLE lite_apps ADD COLUMN config_${name} INTEGER NOT NULL DEFAULT 0")
                for(name in listOf("images", "pullToRefresh")) db.execSQL("ALTER TABLE lite_apps ADD COLUMN config_${name} INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE TABLE IF NOT EXISTS tools (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, appId TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL, payload TEXT NOT NULL, enabled INTEGER NOT NULL, snapshot TEXT NOT NULL, checkedAt INTEGER NOT NULL, error TEXT NOT NULL)")
            }
        }
    }
}
