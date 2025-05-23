package com.example.purrytify.auth
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "token_preferences")

@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_COUNTRY_KEY = stringPreferencesKey("user_country")
    }

    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_COUNTRY = stringPreferencesKey("user_country")
    }
    fun saveUserInfo(userId: String, countryCode: String) = runBlocking {
        dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[USER_COUNTRY_KEY] = countryCode
        }
    }
    fun clearAll() = runBlocking {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    fun getUserId(): String? = runBlocking {
        dataStore.data.first()[USER_ID_KEY]
    }

    fun getUserCountry(): String? = runBlocking {
        dataStore.data.first()[USER_COUNTRY_KEY]
    }

    fun clearUserInfo() = runBlocking {
        dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(USER_COUNTRY_KEY)
        }
    }

    fun saveAccessToken(token: String) = runBlocking {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = token
        }
    }

    fun saveRefreshToken(token: String) = runBlocking {
        dataStore.edit { preferences ->
            preferences[REFRESH_TOKEN_KEY] = token
        }
    }

    fun getAccessToken(): String? = runBlocking {
        dataStore.data.first()[ACCESS_TOKEN_KEY]
    }

    fun getRefreshToken(): String? = runBlocking {
        dataStore.data.first()[REFRESH_TOKEN_KEY]
    }

    fun clearTokens() = runBlocking {
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
        }
    }
}