package online.aruka.librarian.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import com.google.gson.Gson
import online.aruka.librarian.database.DatabaseInitializer
import online.aruka.librarian.database.entity.SeriesEntity
import online.aruka.librarian.database.service.BookService
import online.aruka.librarian.database.service.SeriesService

class SeriesList : LibrarianCommand(name = "list") {
    override fun help(context: Context) = "条件に一致するシリーズの一覧を表示します。"

    private val id: Long? by option("--id", help = "シリーズIDの完全一致条件").long()
    private val title: String? by option("--title", help = "タイトルの完全一致条件")
    private val kTitle: String? by option("--ktitle", help = "タイトルの部分一致キーワード")

    private val json: Boolean by option("--json", help = "全件を1つのJSON配列で出力します。")
        .flag(default = false)
    private val jsonl: Boolean by option("--jsonl", help = "1シリーズ1行のJSON Linesで出力します。")
        .flag(default = false)
    private val oneline: Boolean by option("--1", help = "ISBNを1行1件で列挙します。")
        .flag(default = false)
    private val not: Boolean by option(
        "--not",
        help = "ISBN欄を、そのシリーズのうち未所持のISBNだけに絞ります。未所持巻が0件のシリーズは結果から除外されます。"
    ).flag(default = false)

    private val gson = Gson()

    override fun run() {
        val jdbi = DatabaseInitializer.open(DatabaseInitializer.Config(dbPath))
        val matched = SeriesService(jdbi).search(id = id, exactTitle = title, keywordTitle = kTitle)

        val seriesList = if (not) {
            val owned = BookService(jdbi).ownedIsbns()
            matched
                .map { it.copy(isbns = it.isbns.filterNot { isbn -> isbn in owned }) }
                .filter { it.isbns.isNotEmpty() }
        } else {
            matched
        }

        when {
            json -> echo(gson.toJson(seriesList.map { it.toJsonMap() }))
            jsonl -> seriesList.forEach { echo(gson.toJson(it.toJsonMap())) }
            oneline -> seriesList.forEach {
                echo(header(it))
                it.isbns.forEach { isbn -> echo("  ISBN: $isbn") }
            }
            else -> seriesList.forEach { echo("${header(it)} ISBN: ${it.isbns.joinToString(",")}") }
        }
    }

    private fun header(series: SeriesEntity): String =
        "[${series.id}] タイトル: ${series.title} (${series.status.value})"

    private fun SeriesEntity.toJsonMap(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "status" to status.value,
        "books" to isbns
    )
}
