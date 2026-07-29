package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import online.aruka.librarian.database.DatabaseInitializer
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.JdbiException
import java.io.IOException

abstract class LibrarianCommand(name: String) : CliktCommand(name = name) {
    val dbPath: String by option("--db", help = "データベースファイルのパス（省略時は ~/.librarian/books.db）")
        .default(DatabaseInitializer.defaultPath())

    // 想定内の異常（DB未作成・DB破損・入出力エラー等）はスタックトレースではなくメッセージだけを
    // 表示して終了したいので、CliktError に変換して Clikt の終了処理に任せる。
    // 想定外の例外（バグ）は握りつぶさず、そのままスタックトレースとして表面化させる。
    final override fun run() {
        try {
            runCommand()
        } catch (e: CliktError) {
            throw e
        } catch (e: JdbiException) {
            // Jdbiのメッセージは実行したSQL全文を含んで冗長なので、原因例外のメッセージだけを見せる。
            throw CliktError("データベースの操作に失敗しました: ${rootCauseMessage(e)}")
        } catch (e: IllegalStateException) {
            throw CliktError(e.message ?: "処理を続行できません。")
        } catch (e: IllegalArgumentException) {
            throw CliktError(e.message ?: "指定された値が不正です。")
        } catch (e: IOException) {
            throw CliktError("入出力エラーが発生しました: ${e.message}")
        }
    }

    abstract fun runCommand()

    protected fun openDatabase(): Jdbi = DatabaseInitializer.open(DatabaseInitializer.Config(dbPath))

    protected fun createDatabase(): Jdbi = DatabaseInitializer.create(DatabaseInitializer.Config(dbPath))
}

internal fun rootCauseMessage(e: Throwable): String =
    generateSequence(e) { it.cause }.last().message ?: e.toString()
