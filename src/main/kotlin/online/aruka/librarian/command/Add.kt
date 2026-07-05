package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import online.aruka.librarian.database.DatabaseInitializer
import online.aruka.librarian.database.entity.BookEntity
import online.aruka.librarian.database.service.BookService
import online.aruka.librarian.parse.BookJAN
import online.aruka.librarian.parse.ISBN

class Add : LibrarianCommand(name = "add") {
    override fun help(context: Context) = "書籍を1件登録します（ISBN/書籍JANコードから自動補完可能）。"

    private val author: String? by option("--author", help = "著者")
    private val title: String? by option("--title", help = "タイトル（ISBN等から解決できない場合は必須）")
    private val price: Int? by option("--price", help = "価格").int()
    private val publisher: String? by option("--publisher", help = "出版社")
    private val genre: String? by option("--genre", help = "ジャンル")
    private val memo: String? by option("--memo", help = "メモ")
    private val isbn: String? by option("--isbn", help = "ISBN")
    private val janCode: String? by option("--jancode", help = "書籍JANコード（2段目）")
    private val codeSet: String? by option(
        "--code-set",
        help = "本の2段バーコードを上段(ISBN)・下段(書籍JANコード)の順に連続スキャンして連結した26桁の文字列"
    )
    private val dryRun: Boolean by option("--dry-run", help = "実際には登録せず、登録される内容のプレビューのみ表示します。")
        .flag(default = false)
    private val interactive: Boolean by option(
        "-I", "--interactive",
        help = "登録後（値が指定されていれば）、続けて add をインタラクティブに実行できるモードに入ります。"
    ).flag(default = false)
    private val modeIsbn: Boolean by option(
        "--mode-isbn",
        help = "インタラクティブモードで、ISBNだけを入力して登録できるモードに直接入ります。"
    ).flag(default = false)
    private val modeJancode: Boolean by option(
        "--mode-jancode",
        help = "インタラクティブモードで、書籍JANコードだけを入力して登録できるモードに直接入ります。"
    ).flag(default = false)
    private val modeCodeSet: Boolean by option(
        "--mode-code-set",
        help = "インタラクティブモードで、2段バーコード連結文字列だけを入力して登録できるモードに直接入ります。"
    ).flag(default = false)
    private val modeTitle: Boolean by option(
        "--mode-title",
        help = "インタラクティブモードで、タイトルだけを入力して登録できるモードに直接入ります。"
    ).flag(default = false)

    // AddInteractiveSession が使い捨てのAddインスタンスを生成する際、登録内容の受け取り先として設定するフック。
    internal var onBuilt: ((BookEntity.New) -> Unit)? = null

    override fun run() {
        val registering = author != null || title != null || price != null || publisher != null ||
            genre != null || memo != null || isbn != null || janCode != null || codeSet != null || dryRun

        if (registering) {
            addOnce()
        }

        if (interactive) {
            val initialMode = AddInteractiveSession.resolveInitialMode(modeIsbn, modeJancode, modeCodeSet, modeTitle)
            AddInteractiveSession(dbPath, echo = { msg -> echo(msg) }).run(initialMode)
        }
    }

    private fun addOnce() {
        val codeSetIsbn: String?
        val codeSetJan: String?
        if (codeSet != null) {
            val split = splitCodeSet(codeSet!!)
            codeSetIsbn = split.first
            codeSetJan = split.second
        } else {
            codeSetIsbn = null
            codeSetJan = null
        }

        val effectiveIsbn = isbn ?: codeSetIsbn
        val effectiveJanCode = janCode ?: codeSetJan

        effectiveJanCode?.let {
            if (!BookJAN.isValidCode(it)) {
                throw CliktError("JANコードが不正です。正しく入力してください。")
            }
        }

        val janClassification = effectiveJanCode?.let { BookJAN.getClassificationCode(it).getOrNull()?.toString()?.padStart(4, '0') }
        val janPrice = effectiveJanCode?.let { BookJAN.getPrice(it).getOrNull() }

        val openBdInfo = effectiveIsbn?.let { ISBN.fetchBookInfo(it) }
        if (effectiveIsbn != null && openBdInfo == null) {
            echo("ISBN $effectiveIsbn の書誌情報をopenBDから取得できませんでした。指定されたフィールドのみで登録を続けます。")
        }

        val newBook = BookEntity.New(
            author = author ?: openBdInfo?.author,
            title = title ?: openBdInfo?.title
                ?: throw CliktError("タイトルが必要です。--title を指定するか、解決可能な --isbn を指定してください。"),
            price = price ?: janPrice ?: openBdInfo?.price,
            publisher = publisher ?: openBdInfo?.publisher,
            genre = genre ?: janClassification,
            memo = memo,
            isbn = effectiveIsbn,
            janCode = effectiveJanCode
        )

        val hook = onBuilt
        if (hook != null) {
            if (dryRun) {
                echo("[dry-run] 登録待ちに追加される内容: $newBook")
                return
            }
            hook(newBook)
            return
        }

        val jdbi = DatabaseInitializer.open(DatabaseInitializer.Config(dbPath))

        if (dryRun) {
            echo("[dry-run] 登録される内容: $newBook")
            return
        }

        val id = BookService(jdbi).add(newBook)
        echo("登録しました (id=$id): $newBook")
    }

    // 日本の書籍の2段バーコードは、上段(ISBN)・下段(書籍JANコード)ともに13桁で、
    // 上から順にスキャンすると26桁の数字列になる。前半13桁をISBN、後半13桁を書籍JANコードとみなす。
    private fun splitCodeSet(raw: String): Pair<String, String> {
        if (!CODE_SET_PATTERN.matches(raw)) {
            throw CliktError("--code-set は2段バーコードを連結した26桁の数字で指定してください。")
        }
        return raw.substring(0, 13) to raw.substring(13, 26)
    }

    private companion object {
        val CODE_SET_PATTERN = Regex("^[0-9]{26}$")
    }
}
