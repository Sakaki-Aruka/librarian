package online.aruka.librarian.parse

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object ISBN {
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

    fun fetchBookInfo(isbn: String): BookInfo? {
        val request = HttpRequest.newBuilder(URI.create("https://api.openbd.jp/v1/get?isbn=$isbn"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: Exception) {
            return null
        }

        if (response.statusCode() != 200) {
            return null
        }

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
