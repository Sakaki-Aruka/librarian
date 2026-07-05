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

        // openBD/NDL典拠由来のauthorは "姓,名,生没年" のようにカンマ・空白を含む形で保存されているため、
        // 自然に入力された検索語（カンマ・空白なし）でも一致するよう、カラム側・検索語側の双方から
        // カンマと空白を取り除いた上で比較する。
        val exactConditions = exact.keys.map { column -> "${columnExpr(column)} = :exact_$column" }
        val partialConditions = partial.keys.map { column -> "${columnExpr(column)} LIKE :partial_$column" }

        val exactBindings = exact.mapValues { (column, value) -> normalizeForSearch(column, value) }
            .mapKeys { "exact_${it.key}" }
        val partialBindings = partial.mapValues { (column, value) -> "%${normalizeForSearch(column, value)}%" }
            .mapKeys { "partial_${it.key}" }

        return handle.createQuery(
            "SELECT * FROM books WHERE " + (exactConditions + partialConditions).joinToString(" AND ")
        )
            .bindMap(exactBindings + partialBindings)
            .mapTo(BookEntity::class.java)
            .list()
    }

    companion object {
        private val ALLOWED_COLUMNS = setOf("author", "title", "publisher", "genre", "memo", "isbn", "jan_code")

        private fun columnExpr(column: String): String =
            if (column == "author") "REPLACE(REPLACE(author, ',', ''), ' ', '')" else column

        private fun normalizeForSearch(column: String, value: String): String =
            if (column == "author") value.replace(",", "").replace(" ", "") else value
    }
}