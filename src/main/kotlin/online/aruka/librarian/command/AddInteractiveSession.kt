package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.parse
import online.aruka.librarian.database.DatabaseInitializer
import online.aruka.librarian.database.entity.BookEntity
import online.aruka.librarian.database.service.BookService
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.reader.SyntaxError
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.TerminalBuilder

/**
 * `add -I` の対話セッション。CLIオプションの定義や1件登録のロジックは [Add] 側に置き、
 * このクラスはREPLループ・フィールド専用モード・登録待ちバッファの管理に専念する。
 */
internal class AddInteractiveSession(
    private val dbPath: String,
    private val echo: (String) -> Unit
) {
    enum class FieldMode(val flagName: String, val optionName: String, val label: String) {
        ISBN("--mode-isbn", "--isbn", "ISBN"),
        JANCODE("--mode-jancode", "--jancode", "書籍JANコード"),
        CODE_SET("--mode-code-set", "--code-set", "2段バーコード"),
        TITLE("--mode-title", "--title", "タイトル")
    }

    private val buffer = mutableListOf<BookEntity.New>()
    private var fieldMode: FieldMode? = null

    fun run(initialMode: FieldMode?) {
        fieldMode = initialMode
        echo("インタラクティブモードに入りました。'h' または 'help' でヘルプ、'exit' で終了します。")

        val terminal = TerminalBuilder.builder().build()
        terminal.use {
            val lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(LINE_PARSER)
                .build()

            runLoop(lineReader)
        }
    }

    private fun runLoop(lineReader: LineReader) {
        while (true) {
            val mode = fieldMode
            val prompt = if (mode != null) "add(${mode.label})> " else "add> "

            val line = try {
                lineReader.readLine(prompt)
            } catch (e: EndOfFileException) {
                break
            } catch (e: UserInterruptException) {
                flush()
                break
            }
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

    companion object {
        private const val BUFFER_SIZE = 3
        private val UNAVAILABLE_IN_INTERACTIVE_MODE = setOf(
            "--db", "-I", "--interactive",
            "--mode-isbn", "--mode-jancode", "--mode-code-set", "--mode-title"
        )
        private val LINE_PARSER = DefaultParser().eofOnUnclosedQuote(true)
        private val MODE_SWITCH_COMMANDS = FieldMode.entries.associateBy { it.flagName }

        fun resolveInitialMode(
            modeIsbn: Boolean,
            modeJancode: Boolean,
            modeCodeSet: Boolean,
            modeTitle: Boolean
        ): FieldMode? {
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
    }
}
