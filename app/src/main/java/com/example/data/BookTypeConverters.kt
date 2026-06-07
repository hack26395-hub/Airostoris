package com.example.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class BookTypeConverters {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, BookPage::class.java)
    private val adapter = moshi.adapter<List<BookPage>>(listType)

    @TypeConverter
    fun fromPageList(pages: List<BookPage>?): String? {
        return pages?.let { adapter.toJson(it) }
    }

    @TypeConverter
    fun toPageList(json: String?): List<BookPage>? {
        return json?.let { adapter.fromJson(it) }
    }
}
