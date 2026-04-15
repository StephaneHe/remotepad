package com.remotepad.viewmodel

/**
 * Abstraction over SharedPreferences for testability.
 */
interface PreferencesStore {
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}
