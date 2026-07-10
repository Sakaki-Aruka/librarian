package online.aruka.librarian.database.dao

import online.aruka.librarian.database.entity.SeriesEntity
import online.aruka.librarian.database.entity.SeriesStatus
import org.jdbi.v3.sqlobject.SqlObject
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

data class SeriesRow(
    val id: Long,
    val title: String,
    val status: String
)

interface SeriesDao : SqlObject {
    @SqlUpdate("INSERT INTO series (title, status) VALUES (:title, :status)")
    @GetGeneratedKeys("id")
    fun insertSeries(@Bind("title") title: String, @Bind("status") status: String): Long

    @SqlUpdate("INSERT OR IGNORE INTO series_isbn (series_id, isbn) VALUES (:seriesId, :isbn)")
    fun insertIsbn(@Bind("seriesId") seriesId: Long, @Bind("isbn") isbn: String): Int

    @SqlUpdate("DELETE FROM series_isbn WHERE series_id = :seriesId AND isbn = :isbn")
    fun deleteIsbn(@Bind("seriesId") seriesId: Long, @Bind("isbn") isbn: String): Int

    @SqlUpdate("DELETE FROM series_isbn WHERE series_id = :seriesId")
    fun deleteAllIsbns(@Bind("seriesId") seriesId: Long): Int

    @SqlUpdate("DELETE FROM series WHERE id = :id")
    fun deleteSeriesById(@Bind("id") id: Long): Int

    @SqlUpdate("UPDATE series SET title = :title WHERE id = :id")
    fun updateTitle(@Bind("id") id: Long, @Bind("title") title: String): Int

    @SqlUpdate("UPDATE series SET status = :status WHERE id = :id")
    fun updateStatus(@Bind("id") id: Long, @Bind("status") status: String): Int

    @SqlQuery("SELECT id, title, status FROM series WHERE id = :id")
    fun findRowById(@Bind("id") id: Long): SeriesRow?

    @SqlQuery("SELECT isbn FROM series_isbn WHERE series_id = :seriesId ORDER BY isbn")
    fun findIsbnsBySeriesId(@Bind("seriesId") seriesId: Long): List<String>

    fun insertWithIsbns(title: String, status: String, isbns: List<String>): Long {
        val id = insertSeries(title, status)
        isbns.forEach { insertIsbn(id, it) }
        return id
    }

    fun findEntityById(id: Long): SeriesEntity? {
        val row = findRowById(id) ?: return null
        return toEntity(row)
    }

    fun search(id: Long? = null, exactTitle: String? = null, keywordTitle: String? = null): List<SeriesEntity> {
        val conditions = mutableListOf<String>()
        val bindings = mutableMapOf<String, Any>()

        id?.let {
            conditions += "id = :id"
            bindings["id"] = it
        }
        exactTitle?.let {
            conditions += "title = :exactTitle"
            bindings["exactTitle"] = it
        }
        keywordTitle?.let {
            conditions += "title LIKE :keywordTitle"
            bindings["keywordTitle"] = "%$it%"
        }

        val sql = "SELECT id, title, status FROM series" +
            if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")

        val query = handle.createQuery(sql)
        bindings.forEach { (key, value) -> query.bind(key, value) }

        return query.mapTo(SeriesRow::class.java).list().map { toEntity(it) }
    }

    private fun toEntity(row: SeriesRow): SeriesEntity = SeriesEntity(
        id = row.id,
        title = row.title,
        status = SeriesStatus.fromValue(row.status),
        isbns = findIsbnsBySeriesId(row.id)
    )
}
