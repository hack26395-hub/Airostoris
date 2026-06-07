package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class BookPage(
    val pageNumber: Int,
    val text: String,
    val hasIllustration: Boolean,
    val illustrationPrompt: String? = null,
    val illustrationUrl: String? = null
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val concept: String,
    val requestedPages: Int,
    val requestedLines: Int,
    val styleIsColor: Boolean,
    val imageFrequency: Int,
    val language: String,
    val author: String = "kais",
    val coverImageUrl: String,
    val pagesJson: String, // String representation of List<BookPage>
    val createdAt: Long = System.currentTimeMillis()
)
