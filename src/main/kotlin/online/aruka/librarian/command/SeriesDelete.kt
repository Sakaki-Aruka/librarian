package online.aruka.librarian.command

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import online.aruka.librarian.database.DatabaseInitializer
import online.aruka.librarian.database.service.SeriesService

class SeriesDelete : LibrarianCommand(name = "delete") {
    override fun help(context: Context) = "シリーズを削除します。"

    private val id: Long by option("--id", help = "削除対象のシリーズID").long().required()

    override fun run() {
        val jdbi = DatabaseInitializer.open(DatabaseInitializer.Config(dbPath))
        val deleted = try {
            SeriesService(jdbi).delete(id)
        } catch (e: NoSuchElementException) {
            throw CliktError(e.message ?: "削除対象が見つかりません。")
        }
        echo("シリーズを削除しました: [${deleted.id}] ${deleted.title} (${deleted.status.value})")
    }
}
