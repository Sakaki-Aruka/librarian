package online.aruka.librarian.database.entity

enum class SeriesStatus(val value: String) {
    ONGOING("ongoing"),
    COMPLETED("completed");

    companion object {
        fun fromValue(value: String): SeriesStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("不正なstatusです: $value（ongoing/completedのいずれかを指定してください）")
    }
}

data class SeriesEntity(
    val id: Long,
    val title: String,
    val status: SeriesStatus,
    val isbns: List<String>
) {
    data class New(
        val title: String,
        val status: SeriesStatus,
        val isbns: List<String> = emptyList()
    ) {
        init {
            require(title.isNotBlank()) { "タイトルを空にすることはできません。" }
        }
    }
}
