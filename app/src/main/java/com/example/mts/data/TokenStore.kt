package com.example.mts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore("session")

class TokenStore(private val context: Context) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")

    val isSignedIn: Flow<Boolean> = context.sessionStore.data.map { it[refreshKey] != null }

    suspend fun accessToken(): String? = context.sessionStore.data.first()[accessKey]
    suspend fun refreshToken(): String? = context.sessionStore.data.first()[refreshKey]

    suspend fun save(session: Session) {
        context.sessionStore.edit {
            it[accessKey] = session.accessToken
            it[refreshKey] = session.refreshToken
        }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.clear() }
    }
}
