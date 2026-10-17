package org.litespace.browser.domain

import kotlinx.coroutines.flow.Flow
import org.litespace.browser.domain.LiteApp

interface AppRepository {
    val apps: Flow<List<LiteApp>>
    suspend fun get(id: String): LiteApp?
    suspend fun save(app: LiteApp)
    suspend fun delete(id: String)
    suspend fun export(): String
    suspend fun restore(json: String): Int
}
