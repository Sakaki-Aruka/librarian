package online.aruka.librarian.parse

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object ISBN {

    fun isValidCode(code: String): Boolean {
        return isValidIsbn10(code) || isValidIsbn13(code)
    }

    private val isbn10Pattern = Regex("([0-9]{1,5})[- ]?([0-9]+)[- ]?([0-9]+)[- ]?([0-9X])")
    fun isValidIsbn10(code: String): Boolean {
        val formattedCode: String = code.replace(Regex("[- ]"), "")
        if (!isbn10Pattern.matches(code) || formattedCode.length != 10) {
            return false
        }

        val checkDigit: Char = formattedCode.last()
        val sum: Int = (2..10).reversed().withIndex()
            .sumOf { (index, weight) -> formattedCode[index].digitToInt() * weight }

        val calculatedDigitNum: Int = (11 - (sum % 11)) % 11
        return if (calculatedDigitNum < 10) {
            calculatedDigitNum.toChar() == checkDigit
        } else {
            checkDigit == 'X'
        }
    }

    // nnn-G(GGGG)-AAAA-BBBB-C
    private val isbn13Pattern = Regex("97[89][- ]?([0-9]{1,5})[- ]?([0-9]+)[- ]?([0-9]+)[- ]?([0-9])")
    fun isValidIsbn13(code: String): Boolean {
        val formattedCode: String = code.replace(Regex("[- ]"), "")
        if (!isbn13Pattern.matches(code) || formattedCode.length != 13) {
            return false
        }

        val checkDigit: Int = formattedCode.last().digitToInt()
        val sum: Int = (1..12).withIndex()
            .sumOf { (index, i) -> formattedCode[index].digitToInt() * (if (i % 2 == 0) 3 else 1) }
        val calculatedDigit: Int = (10 - (sum % 10)) % 10
        return checkDigit == calculatedDigit
    }

    data class BookInfo(
        val title: String?,
        val author: String?,
        val publisher: String?,
        val price: Int?
    )

    private val gson = Gson()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private const val MAX_ATTEMPTS = 4 // 初回 + 最大3回のリトライ
    private val RETRY_DELAY_MILLIS = Duration.ofMillis(500).toMillis()

    fun fetchBookInfo(isbn: String): BookInfo? {
        val request = HttpRequest.newBuilder(URI.create("https://api.openbd.jp/v1/get?isbn=$isbn"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = sendWithRetry(request) ?: return null

        val entry = try {
            gson.fromJson(response.body(), Array<OpenBdEntry?>::class.java).firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return null

        return BookInfo(
            title = entry.summary?.title,
            author = entry.summary?.author,
            publisher = entry.summary?.publisher,
            price = entry.onix?.productSupply?.supplyDetail?.price?.firstOrNull()?.priceAmount?.toIntOrNull()
        )
    }

    // 通信例外や非200応答は一時的な障害の可能性があるため、初回を含め最大 MAX_ATTEMPTS 回まで試行する。
    private fun sendWithRetry(request: HttpRequest): HttpResponse<String>? {
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = try {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (_: Exception) {
                null
            }

            if (response != null && response.statusCode() == 200) {
                return response
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                Thread.sleep(RETRY_DELAY_MILLIS)
            }
        }
        return null
    }

    private data class OpenBdEntry(
        val summary: OpenBdSummary?,
        val onix: OpenBdOnix?
    )

    private data class OpenBdSummary(
        val title: String?,
        val author: String?,
        val publisher: String?
    )

    private data class OpenBdOnix(
        @SerializedName("ProductSupply") val productSupply: OpenBdProductSupply?
    )

    private data class OpenBdProductSupply(
        @SerializedName("SupplyDetail") val supplyDetail: OpenBdSupplyDetail?
    )

    private data class OpenBdSupplyDetail(
        @SerializedName("Price") val price: List<OpenBdPrice>?
    )

    private data class OpenBdPrice(
        @SerializedName("PriceAmount") val priceAmount: String?
    )
}
