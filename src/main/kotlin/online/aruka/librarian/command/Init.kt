package online.aruka.librarian.command

import com.github.ajalt.clikt.core.Context

class Init : LibrarianCommand(name = "init") {
    override fun help(context: Context) = "データベースを新規作成します。"

    override fun runCommand() {
        createDatabase()
        echo("データベースを初期化しました: $dbPath")
    }
}
