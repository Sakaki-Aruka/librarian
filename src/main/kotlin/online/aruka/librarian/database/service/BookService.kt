package online.aruka.librarian.database.service

import online.aruka.librarian.database.dao.BookDao
import online.aruka.librarian.database.entity.BookEntity
import org.jdbi.v3.core.Jdbi

class BookService(jdbi: Jdbi) {
    private val dao: BookDao = jdbi.onDemand(BookDao::class.java)

    fun add(book: BookEntity.New): Long = dao.insert(book)

    fun search(exact: Map<String, String> = emptyMap(), partial: Map<String, String> = emptyMap()): List<BookEntity> =
        dao.search(exact, partial)

    fun deleteMatching(exact: Map<String, String> = emptyMap(), partial: Map<String, String> = emptyMap()): List<BookEntity> {
        val targets = dao.search(exact, partial)
        targets.forEach { dao.deleteById(it.id) }
        return targets
    }
}
