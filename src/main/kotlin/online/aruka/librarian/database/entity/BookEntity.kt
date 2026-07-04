package online.aruka.librarian.database.entity

data class BookEntity(
    val id: Long,
    val author: String?,
    val title: String,
    val price: Int?,
    val publisher: String?,
    val genre: String?,
    val memo: String?,
    val isbn: String?,
    val janCode: String?
) {
    data class New(
        val author: String? = null,
        val title: String,
        val price: Int? = null,
        val publisher: String? = null,
        val genre: String? = null,
        val memo: String? = null,
        val isbn: String? = null,
        val janCode: String? = null
    ) {
        init {
            require(title.isNotBlank()) { "タイトルを空にすることはできません。" }
        }
    }
}