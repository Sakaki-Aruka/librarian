package online.aruka.librarian.database

import online.aruka.librarian.database.entity.BookEntity
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper
import org.jdbi.v3.sqlite3.SQLitePlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import java.io.File

object DatabaseInitializer {

    data class Config(
        val path: String
    ) {
        init {
            require(path.isNotBlank()) { "データベースパスを空にすることはできません。" }
        }
    }

    fun defaultPath(): String = File(System.getProperty("user.home"), ".librarian/books.db").path

    fun create(config: Config): Jdbi {
        val file = File(config.path)
        if (file.exists()) {
            throw IllegalStateException("データベースは既に存在します: ${config.path}")
        }
        file.absoluteFile.parentFile?.mkdirs()

        return connect(config)
    }

    fun open(config: Config): Jdbi {
        val file = File(config.path)
        if (!file.exists()) {
            throw IllegalStateException("データベースが見つかりません: ${config.path}")
        }

        return connect(config)
    }

    private fun connect(config: Config): Jdbi {
        val db: Jdbi = Jdbi.create("jdbc:sqlite:${config.path}")
        db.installPlugin(SQLitePlugin())
        db.installPlugin(SqlObjectPlugin())
        db.registerRowMapper(ConstructorMapper.factory(BookEntity::class.java))

        db.useHandle<Exception> { handle ->
            handle.execute("""
                CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                author TEXT,
                title TEXT NOT NULL,
                price INTEGER,
                publisher TEXT,
                genre TEXT,
                memo TEXT,
                isbn TEXT,
                jan_code TEXT
                )
            """.trimIndent())
        }

        return db
    }
}
