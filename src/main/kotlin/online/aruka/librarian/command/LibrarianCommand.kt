package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import online.aruka.librarian.database.DatabaseInitializer

abstract class LibrarianCommand(name: String) : CliktCommand(name = name) {
    val dbPath: String by option("--db", help = "データベースファイルのパス（省略時は ~/.librarian/books.db）")
        .default(DatabaseInitializer.defaultPath())
}
