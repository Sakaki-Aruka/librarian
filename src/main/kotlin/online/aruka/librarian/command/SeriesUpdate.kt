package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.long
import online.aruka.librarian.database.DatabaseInitializer
import online.aruka.librarian.database.entity.SeriesStatus
import online.aruka.librarian.database.service.SeriesService
import online.aruka.librarian.parse.ISBN

class SeriesUpdate : LibrarianCommand(name = "update") {
    override fun help(context: Context) = "登録済みのシリーズを更新します。"

    private val id: Long by option("--id", help = "更新対象のシリーズID").long().required()
    private val title: String? by option("--title", help = "変更後のタイトル")
    private val status: SeriesStatus? by option("--status", help = "変更後の状態")
        .choice("ongoing" to SeriesStatus.ONGOING, "completed" to SeriesStatus.COMPLETED)
    private val isbn: List<String> by option(
        "--isbn",
        help = "追加するISBN（複数指定可、未指定時は標準入力から1行1ISBNで読み込み）"
    ).multiple()
    private val removeIsbn: List<String> by option(
        "--remove-isbn",
        help = "削除するISBN（複数指定可、誤登録の訂正用）"
    ).multiple()

    override fun run() {
        val addIsbns = resolveIsbnInput(isbn)
        addIsbns.forEach {
            if (!ISBN.isValidCode(it)) {
                throw CliktError("ISBNコードが不正です: $it")
            }
        }

        if (title == null && status == null && addIsbns.isEmpty() && removeIsbn.isEmpty()) {
            throw CliktError("更新内容が指定されていません。--title / --status / --isbn / --remove-isbn のいずれかを指定してください。")
        }

        val jdbi = DatabaseInitializer.open(DatabaseInitializer.Config(dbPath))
        val updated = try {
            SeriesService(jdbi).update(
                id = id,
                title = title,
                status = status,
                addIsbns = addIsbns,
                removeIsbns = removeIsbn
            )
        } catch (e: NoSuchElementException) {
            throw CliktError(e.message ?: "更新対象が見つかりません。")
        }

        echo("シリーズを更新しました: [${updated.id}] ${updated.title} (${updated.status.value}) ISBN数=${updated.isbns.size}")
    }
}
