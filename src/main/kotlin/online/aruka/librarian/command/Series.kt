package online.aruka.librarian.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands

class Series : NoOpCliktCommand(name = "series") {
    override fun help(context: Context) = "書籍シリーズを管理します。"

    init {
        subcommands(SeriesAdd(), SeriesUpdate(), SeriesDelete(), SeriesList())
    }
}
