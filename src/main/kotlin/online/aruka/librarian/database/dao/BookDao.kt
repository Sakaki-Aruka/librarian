package online.aruka.librarian.database.dao

import online.aruka.librarian.database.entity.BookEntity
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface BookDao : SqlObject {
    @SqlUpdate("INSERT INTO books (author, title, price, publisher, genre, memo, isbn, jan_code) VALUES (:author, :title, :price, :publisher, :genre, :memo, :isbn, :janCode)")
    @GetGeneratedKeys("id")
    fun insert(@BindBean book: BookEntity.New): Long

    @SqlQuery("SELECT * FROM books WHERE id = :id")
    fun findById(@Bind("id") id: Long): BookEntity?

    @SqlQuery("SELECT * FROM books")
    fun findAllBooks(): List<BookEntity>

    @SqlUpdate("DELETE FROM books WHERE id = :id")
    fun deleteById(@Bind("id") id: Long): Int

    fun search(exact: Map<String, String> = emptyMap(), partial: Map<String, String> = emptyMap()): List<BookEntity> {
        if (exact.isEmpty() && partial.isEmpty()) return findAllBooks()

        (exact.keys + partial.keys).forEach { column ->
            require(column in ALLOWED_COLUMNS) { "invalid column: $column" }
        }

        val exactConditions = exact.keys.map { "$it = :exact_$it" }
        val partialConditions = partial.keys.map { "$it LIKE :partial_$it" }

        val bindings = exact.mapKeys { "exact_${it.key}" } +
            partial.mapKeys { "partial_${it.key}" }.mapValues { "%${it.value}%" }

        return handle.createQuery(
            "SELECT * FROM books WHERE " + (exactConditions + partialConditions).joinToString(" AND ")
        )
            .bindMap(bindings)
            .mapTo(BookEntity::class.java)
            .list()
    }

    companion object {
        private val ALLOWED_COLUMNS = setOf("author", "title", "publisher", "genre", "memo", "isbn", "jan_code")
    }
}