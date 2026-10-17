package org.litespace.browser

import android.app.Application
import androidx.room.Room
import org.litespace.browser.data.*
import org.litespace.browser.domain.AppRepository

class LiteSpaceApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, LiteDatabase::class.java, "litespace.db").build() }
    val repository: AppRepository by lazy { LocalRepository(database) }
    val preferences by lazy { PreferencesStore(this) }
    val blocker by lazy { Blocker(this) }
}
