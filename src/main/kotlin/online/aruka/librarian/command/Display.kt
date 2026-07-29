package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import online.aruka.librarian.database.service.BookService
import online.aruka.librarian.parse.BookJAN

class Display : LibrarianCommand(name = "list") {
    override fun help(context: Context) = "条件に一致する書籍の一覧を表示します。"

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

        val books = BookService(openDatabase()).search(exact, partial)

        books.forEach { echo(it.toString()) }
    }
}
