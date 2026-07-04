package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import online.aruka.librarian.database.DatabaseInitializer
import online.aruka.librarian.database.entity.BookEntity
import online.aruka.librarian.database.service.BookService
import online.aruka.librarian.parse.BookJAN
import online.aruka.librarian.parse.ISBN
import org.jline.reader.Parser
import org.jline.reader.SyntaxError
import org.jline.reader.impl.DefaultParser

class Add : LibrarianCommand(name = "add") {
    override fun help(context: Context) = "書籍を1件登録します（ISBN/書籍JANコードから自動補完可能）。"

    private enum class FieldMode(val flagName: String, val optionName: String, val label: String) {
        ISBN("--mode-isbn", "--isbn", "ISBN"),
        JANCODE("--mode-jancode", "--jancode", "書籍JANコード"),
        CODE_SET("--mode-code-set", "--code-set", "2段バーコード"),
        TITLE("--mode-title", "--title", "タイトル")
    }

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

    private var onBuilt: ((BookEntity.New) -> Unit)? = null
    private val buffer = mutableListOf<BookEntity.New>()
    private var fieldMode: FieldMode? = null

    override fun run() {
        val registering = author != null || title != null || price != null || publisher != null ||
            genre != null || memo != null || isbn != null || janCode != null || codeSet != null || dryRun

        if (registering) {
            addOnce()
        }

        if (interactive) {
            fieldMode = initialFieldMode()
            runInteractive()
        }
    }

    private fun initialFieldMode(): FieldMode? {
        val selected = listOfNotNull(
            FieldMode.ISBN.takeIf { modeIsbn },
            FieldMode.JANCODE.takeIf { modeJancode },
            FieldMode.CODE_SET.takeIf { modeCodeSet },
            FieldMode.TITLE.takeIf { modeTitle }
        )
        if (selected.size > 1) {
            throw CliktError("--mode-isbn, --mode-jancode, --mode-code-set, --mode-title は同時に指定できません。")
        }
        return selected.firstOrNull()
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

    private fun runInteractive() {
        echo("インタラクティブモードに入りました。'h' または 'help' でヘルプ、'exit' で終了します。")

        while (true) {
            val mode = fieldMode
            print(if (mode != null) "add(${mode.label})> " else "add> ")
            val line = readlnOrNull() ?: break
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> continue
                trimmed == "EXIT" -> {
                    flush()
                    break
                }
                trimmed == "exit" -> {
                    if (mode != null) {
                        fieldMode = null
                    } else {
                        flush()
                        break
                    }
                }
                trimmed == "undo" -> undo()
                trimmed == "h" || trimmed == "help" -> {
                    if (mode != null) echoFieldModeHelp(mode) else echoInteractiveHelp()
                }
                MODE_SWITCH_COMMANDS.containsKey(trimmed) -> enterFieldMode(MODE_SWITCH_COMMANDS.getValue(trimmed))
                mode != null -> runFieldModeLine(mode, trimmed)
                else -> runAddLine(trimmed)
            }
        }
    }

    private fun enterFieldMode(mode: FieldMode) {
        fieldMode = mode
        echo(
            "${mode.label}モードに入りました。${mode.label}だけを入力してEnterで登録します。" +
                "'exit' でインタラクティブモードに戻り、'EXIT'（大文字）でインタラクティブモードを経由せず直接終了します。"
        )
    }

    private fun echoInteractiveHelp() {
        echo(
            """
            利用できるオプション:
              --author=<text>     著者
              --title=<text>      タイトル
              --price=<int>       価格
              --publisher=<text>  出版社
              --genre=<text>      ジャンル
              --memo=<text>       メモ
              --isbn=<text>       ISBN
              --jancode=<text>    書籍JANコード（2段目）
              --code-set=<text>   2段バーコードを連続スキャンして連結した26桁の文字列
              --dry-run           実際には登録待ちに追加せず、内容のプレビューのみ表示します。

            以下を入力すると、それぞれの値だけを入力して登録できる専用モードに入ります:
              --mode-isbn, --mode-jancode, --mode-code-set, --mode-title

            'undo' で直前に登録待ち（未書き込み）のエントリを取り消します。
            'exit' でインタラクティブモードを終了します（終了時に登録待ちのエントリはすべて書き込まれます）。
            """.trimIndent()
        )
    }

    private fun echoFieldModeHelp(mode: FieldMode) {
        echo(
            """
            ${mode.label}モード: ${mode.label}の値だけを入力してEnterで送信すると、${mode.optionName} を指定したのと同じ内容で登録待ちに追加されます。

            'undo' で直前に登録待ち（未書き込み）のエントリを取り消します。
            'exit' でインタラクティブモードに戻ります。
            'EXIT'（大文字）でインタラクティブモードを経由せず直接終了します。
            """.trimIndent()
        )
    }

    private fun runAddLine(line: String) {
        val tokens = try {
            tokenize(line)
        } catch (e: SyntaxError) {
            echo("入力を解析できませんでした: ${e.message}")
            return
        }

        if (tokens.any { it in UNAVAILABLE_IN_INTERACTIVE_MODE }) {
            echo("--db, -I, --interactive, --mode-isbn, --mode-jancode, --mode-code-set, --mode-title はインタラクティブモード内では指定できません。")
            return
        }

        val command = Add()
        command.onBuilt = { book -> enqueue(book) }
        try {
            command.parse(tokens + listOf("--db", dbPath))
        } catch (e: CliktError) {
            command.echoFormattedHelp(e)
        }
    }

    private fun runFieldModeLine(mode: FieldMode, rawValue: String) {
        val command = Add()
        command.onBuilt = { book -> enqueue(book) }
        try {
            command.parse(listOf(mode.optionName, rawValue, "--db", dbPath))
        } catch (e: CliktError) {
            command.echoFormattedHelp(e)
        }
    }

    private fun enqueue(book: BookEntity.New) {
        buffer.add(book)
        echo("登録待ちに追加しました (${buffer.size}/$BUFFER_SIZE): $book")

        if (buffer.size > BUFFER_SIZE) {
            flush()
        }
    }

    private fun undo() {
        val removed = buffer.removeLastOrNull()
        if (removed != null) {
            echo("取り消しました: $removed")
        } else {
            echo("登録待ちのエントリがないため、取り消せません。")
        }
    }

    private fun flush() {
        if (buffer.isEmpty()) return

        val jdbi = DatabaseInitializer.open(DatabaseInitializer.Config(dbPath))
        val service = BookService(jdbi)
        buffer.forEach { book ->
            val id = service.add(book)
            echo("登録待ちから書籍情報を取り出して登録しました (id=$id): $book")
        }
        buffer.clear()
    }

    private fun tokenize(input: String): List<String> {
        return LINE_PARSER.parse(input, input.length, Parser.ParseContext.ACCEPT_LINE).words()
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
        const val BUFFER_SIZE = 3
        val UNAVAILABLE_IN_INTERACTIVE_MODE = setOf(
            "--db", "-I", "--interactive",
            "--mode-isbn", "--mode-jancode", "--mode-code-set", "--mode-title"
        )
        val CODE_SET_PATTERN = Regex("^[0-9]{26}$")
        val LINE_PARSER = DefaultParser().eofOnUnclosedQuote(true)
        val MODE_SWITCH_COMMANDS = FieldMode.entries.associateBy { it.flagName }
    }
}
