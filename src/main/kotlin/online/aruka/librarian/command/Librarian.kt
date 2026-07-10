package online.aruka.librarian.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class Librarian : NoOpCliktCommand(name = "librarian") {
    override fun help(context: Context) = "書籍情報を管理するCLIツール"

    init {
        subcommands(Add(), Delete(), Display(), Init(), Series())
    }
}
