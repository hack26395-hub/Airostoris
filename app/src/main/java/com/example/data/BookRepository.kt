package com.example.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBookById(id: Int): BookEntity? {
        return bookDao.getBookById(id)
    }

    suspend fun insertBook(book: BookEntity): Long {
        return bookDao.insertBook(book)
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteBook(book)
    }
}
