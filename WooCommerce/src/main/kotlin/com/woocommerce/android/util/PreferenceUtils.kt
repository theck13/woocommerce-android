package com.woocommerce.android.util

import android.content.SharedPreferences
import android.text.TextUtils

object PreferenceUtils {
    fun getInt(preferences: SharedPreferences, key: String, default: Int = 0): Int {
        return try {
            val value = getString(preferences, key)
            if (value.isEmpty()) {
                default
            } else Integer.parseInt(value)
        } catch (e: NumberFormatException) {
            default
        }
    }

    fun setInt(preferences: SharedPreferences, key: String, value: Int) {
        setString(preferences, key, Integer.toString(value))
    }

    fun getString(preferences: SharedPreferences, key: String, defaultValue: String = ""): String {
        return preferences.getString(key, defaultValue)
    }

    fun setString(preferences: SharedPreferences, key: String, value: String) {
        val editor = preferences.edit()
        if (TextUtils.isEmpty(value)) {
            editor.remove(key)
        } else {
            editor.putString(key, value)
        }
        editor.apply()
    }

    fun getBoolean(preferences: SharedPreferences, key: String, default: Boolean = false): Boolean {
        return preferences.getBoolean(key, default)
    }

    fun setBoolean(preferences: SharedPreferences, key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}
