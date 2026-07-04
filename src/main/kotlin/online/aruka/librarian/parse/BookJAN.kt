package online.aruka.librarian.parse

object BookJAN {
    val pattern = Regex("^192([0-9]{4})([0-9]{5})([0-9])$")

    fun isValidCode(code: String): Boolean {
        if (!this.pattern.matches(code)) {
            return false
        }

        val oddSum: Int = setOf(0, 2, 4, 6, 8, 10)
            .sumOf { i -> code[i].digitToInt() }
        val evenSum: Int = setOf(1, 3, 5, 7, 9, 11)
            .sumOf { i -> code[i].digitToInt() }

        val checkDigit: Int = code[12].digitToInt()
        return (10 - (((evenSum * 3) + oddSum) % 10)) % 10 == checkDigit
    }

    fun getClassificationCode(secondLine: String): Result<Int> {
        if (!isValidCode(secondLine)) {
            return Result.failure(IllegalArgumentException("invalid second line code"))
        }

        val result: MatchResult = this.pattern.find(secondLine)
            ?: return Result.failure(IllegalArgumentException("invalid second line code"))

        return result.groups[1]?.value
            ?.let { Result.success(it.toInt()) }
            ?: Result.failure(IllegalArgumentException("failed to get classification code"))
    }

    fun getPrice(secondLine: String): Result<Int> {
        if (!isValidCode(secondLine)) {
            return Result.failure(IllegalArgumentException("invalid second line code"))
        }

        val result: MatchResult = this.pattern.find(secondLine)
            ?: return Result.failure(IllegalArgumentException("invalid second line code"))

        return result.groups[2]?.value
            ?.let { Result.success(it.toInt()) }
            ?: Result.failure(IllegalArgumentException("failed to get price"))
    }
}