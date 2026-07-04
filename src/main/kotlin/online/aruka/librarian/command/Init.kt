package online.aruka.librarian.command

import com.github.ajalt.clikt.core.Context
import online.aruka.librarian.database.DatabaseInitializer

class Init : LibrarianCommand(name = "init") {
    override fun help(context: Context) = "データベースを新規作成します。"

    override fun run() {
        DatabaseInitializer.create(DatabaseInitializer.Config(dbPath))
        echo("データベースを初期化しました: $dbPath")
    }
}
