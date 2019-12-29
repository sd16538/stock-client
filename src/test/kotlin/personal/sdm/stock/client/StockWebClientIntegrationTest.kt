package personal.sdm.stock.client

import com.tyro.oss.randomdata.RandomDouble.randomPositiveDouble
import com.tyro.oss.randomdata.RandomLocalDateTime.randomLocalDateTime
import com.tyro.oss.randomdata.RandomString.randomAlphabeticString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.concurrent.TimeUnit

class StockWebClientIntegrationTest {

    private val mockWebServer = MockWebServer()

    @BeforeEach
    fun `setup mock server`() {
        mockWebServer.start(8080)
    }

    @AfterEach
    fun `shut down mock server`() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should call stock price server for stock prices`() {

        val symbol = "FOO"
        val randomPrice1 = randomPositiveDouble()
        val randomPrice2 = randomPositiveDouble()
        val randomTime1 = randomLocalDateTime()
        val randomTime2 = randomLocalDateTime()

        val jsonPrice = """
            [
                {
                    "symbol": "$symbol",
                    "time": "$randomTime1",
                    "price": "$randomPrice1"
                },
                {
                    "symbol": "$symbol",
                    "time": "$randomTime2",
                    "price": "$randomPrice2"
                }
            ]
        """.trimIndent()
        mockWebServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody(jsonPrice)
                .setHeader("Content-Type", "application/json")

        )

        val stockWebClient = StockWebClient(WebClient.create())
        val fluxPrices: Flux<StockPrice> = stockWebClient.pricesFor(symbol)

        assertThat(fluxPrices).isNotNull

        StepVerifier.create(fluxPrices)
                .expectNext(StockPrice(symbol, randomPrice1, randomTime1))
                .expectNext(StockPrice(symbol, randomPrice2, randomTime2))
                .verifyComplete()
    }

    @Test
    fun `retry if error`() {
        val stockSymbol = randomAlphabeticString()
        mockWebServer.enqueue(MockResponse()
                .setResponseCode(500)
                .setBody("oops")
                .setHeader("Content-Type", "application/json")
        )

        val retriedTime = randomLocalDateTime()
        val retriedPrice = randomPositiveDouble()
        mockWebServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""
            [
                {
                    "symbol": "$stockSymbol",
                    "time": "$retriedTime",
                    "price": "$retriedPrice"
                }
            ]
        """.trimIndent())
                .setHeader("Content-Type", "application/json")
        )
        val stockWebClient = StockWebClient(WebClient.create())
        val fluxPrices: Flux<StockPrice> = stockWebClient.pricesFor(stockSymbol)

        StepVerifier.create(fluxPrices)
                .expectNext(StockPrice(stockSymbol, retriedPrice, retriedTime))
                .verifyComplete()
    }

    @Test
    fun `handle timeouts despite retries`() {

        val stockSymbol = randomAlphabeticString()
        mockWebServer.enqueue(MockResponse()
                .setBodyDelay(3, TimeUnit.SECONDS)
                .setResponseCode(200)
                .setBody("""
            [
                {
                    "symbol": "$stockSymbol",
                    "time": "${randomLocalDateTime()}",
                    "price": "${randomPositiveDouble()}"
                }
            ]
        """.trimIndent())
                .setHeader("Content-Type", "application/json")
        )
        val stockWebClient = StockWebClient(WebClient.create())
        val fluxPrices: Flux<StockPrice> = stockWebClient.pricesFor(stockSymbol)

        StepVerifier.create(fluxPrices)
                .expectErrorMessage("Retries exhausted: 3/3")
    }

}
