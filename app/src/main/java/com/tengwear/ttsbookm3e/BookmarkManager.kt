package com.tengwear.ttsbookm3e

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Bookmark(
    val chapterIndex: Int,
    val offsetInChapter: Int,
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)

class BookmarkManager(private val context: Context, private val bookPath: String) {

    private val prefs: SharedPreferences = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val key = "bookmarks_$bookPath"
    private val gson = Gson()
    private val type = object : TypeToken<List<Bookmark>>() {}.type

    fun getBookmarks(): List<Bookmark> {
        val json = prefs.getString(key, "[]")
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun addBookmark(bookmark: Bookmark) {
        val list = getBookmarks().toMutableList()
        list.add(bookmark)
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }

    fun removeBookmark(index: Int) {
        val list = getBookmarks().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            prefs.edit().putString(key, gson.toJson(list)).apply()
        }
    }

    fun clearAll() {
        prefs.edit().remove(key).apply()
    }
}