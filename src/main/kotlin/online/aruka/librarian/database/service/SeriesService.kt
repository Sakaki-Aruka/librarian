package online.aruka.librarian.database.service

import online.aruka.librarian.database.dao.SeriesDao
import online.aruka.librarian.database.entity.SeriesEntity
import online.aruka.librarian.database.entity.SeriesStatus
import org.jdbi.v3.core.Jdbi

class SeriesService(jdbi: Jdbi) {
    private val dao: SeriesDao = jdbi.onDemand(SeriesDao::class.java)

    fun add(series: SeriesEntity.New): Long =
        dao.insertWithIsbns(series.title, series.status.value, series.isbns)

    fun search(id: Long? = null, exactTitle: String? = null, keywordTitle: String? = null): List<SeriesEntity> =
        dao.search(id, exactTitle, keywordTitle)

    fun update(
        id: Long,
        title: String? = null,
        status: SeriesStatus? = null,
        addIsbns: List<String> = emptyList(),
        removeIsbns: List<String> = emptyList()
    ): SeriesEntity {
        dao.findEntityById(id) ?: throw NoSuchElementException("指定されたIDのシリーズが見つかりません: $id")

        title?.let { dao.updateTitle(id, it) }
        status?.let { dao.updateStatus(id, it.value) }
        addIsbns.forEach { dao.insertIsbn(id, it) }
        removeIsbns.forEach { dao.deleteIsbn(id, it) }

        return dao.findEntityById(id) ?: throw NoSuchElementException("更新後のシリーズを取得できませんでした: $id")
    }

    fun delete(id: Long): SeriesEntity {
        val series = dao.findEntityById(id) ?: throw NoSuchElementException("指定されたIDのシリーズが見つかりません: $id")
        dao.deleteAllIsbns(id)
        dao.deleteSeriesById(id)
        return series
    }
}
