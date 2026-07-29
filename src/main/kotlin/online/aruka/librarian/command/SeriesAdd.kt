package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import online.aruka.librarian.database.entity.SeriesEntity
import online.aruka.librarian.database.entity.SeriesStatus
import online.aruka.librarian.database.service.SeriesService
import online.aruka.librarian.parse.ISBN

class SeriesAdd : LibrarianCommand(name = "add") {
    override fun help(context: Context) = "シリーズを新規登録します。"

    private val title: String by option("--title", help = "シリーズのタイトル").required()
    private val status: SeriesStatus by option("--status", help = "シリーズの状態")
        .choice("ongoing" to SeriesStatus.ONGOING, "completed" to SeriesStatus.COMPLETED)
        .required()
    private val isbn: List<String> by option(
        "--isbn",
        help = "シリーズに含まれるISBN（複数指定可、未指定時は標準入力から1行1ISBNで読み込み）"
    ).multiple()

    override fun runCommand() {
        val isbns = resolveIsbnInput(isbn)
        isbns.forEach {
            if (!ISBN.isValidCode(it)) {
                throw CliktError("ISBNコードが不正です: $it")
            }
        }

        val id = SeriesService(openDatabase()).add(SeriesEntity.New(title = title, status = status, isbns = isbns))
        echo("シリーズを登録しました (id=$id): $title [${status.value}] ISBN数=${isbns.size}")
    }
}
