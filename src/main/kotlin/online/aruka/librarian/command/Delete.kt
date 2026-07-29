package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import online.aruka.librarian.database.service.BookService
import online.aruka.librarian.parse.BookJAN

class Delete : LibrarianCommand(name = "delete") {
    override fun help(context: Context) = "条件に一致する書籍を削除します。"

    private val author: String? by option("--author", help = "著者の完全一致条件")
    private val title: String? by option("--title", help = "タイトルの完全一致条件")
    private val publisher: String? by option("--publisher", help = "出版社の完全一致条件")
    private val genre: String? by option("--genre", help = "ジャンルの完全一致条件")
    private val memo: String? by option("--memo", help = "メモの完全一致条件")
    private val isbn: String? by option("--isbn", help = "ISBNの完全一致条件")
    private val janCode: String? by option("--jancode", help = "書籍JANコードの完全一致条件")

    private val kAuthor: String? by option("--kauthor", help = "著者の部分一致キーワード")
    private val kTitle: String? by option("--ktitle", help = "タイトルの部分一致キーワード")
    private val kPublisher: String? by option("--kpublisher", help = "出版社の部分一致キーワード")
    private val kGenre: String? by option("--kgenre", help = "ジャンルの部分一致キーワード")

    private val dryRun: Boolean by option("--dry-run", help = "実際には削除せず、削除対象のプレビューのみ表示します。")
        .flag(default = false)
    private val all: Boolean by option("--all", help = "フィルタを何も指定せず全件削除する場合に必須のフラグです。")
        .flag(default = false)

    override fun runCommand() {
        janCode?.let {
            if (!BookJAN.isValidCode(it)) {
                throw CliktError("JANコードが不正です。正しく入力してください。")
            }
        }

        val exact = buildMap {
            author?.let { put("author", it) }
            title?.let { put("title", it) }
            publisher?.let { put("publisher", it) }
            genre?.let { put("genre", it) }
            memo?.let { put("memo", it) }
            isbn?.let { put("isbn", it) }
            janCode?.let { put("jan_code", it) }
        }
        val partial = buildMap {
            kAuthor?.let { put("author", it) }
            kTitle?.let { put("title", it) }
            kPublisher?.let { put("publisher", it) }
            kGenre?.let { put("genre", it) }
        }

        if (exact.isEmpty() && partial.isEmpty() && !all) {
            echoFormattedHelp()
            return
        }

        val service = BookService(openDatabase())

        if (dryRun) {
            val targets = service.search(exact, partial)
            echo("[dry-run] ${targets.size}件削除されます:")
            targets.forEach { echo("  $it") }
            return
        }

        val deleted = service.deleteMatching(exact, partial)
        echo("${deleted.size}件削除しました:")
        deleted.forEach { echo("  $it") }
    }
}
