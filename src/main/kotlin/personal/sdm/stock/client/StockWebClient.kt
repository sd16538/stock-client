package personal.sdm.stock.client

import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.LocalDateTime


class StockWebClient(private val webClient: WebClient) {
    fun pricesFor(stockSymbol: String): Flux<StockPrice> = webClient.get()
            .uri("http://localhost:8080/stocks/$stockSymbol")
            .retrieve()
            .bodyToFlux(StockPrice::class.java)
            .timeout(Duration.ofSeconds(2))
            .retryBackoff(3, Duration.ofSeconds(2), Duration.ofSeconds(5))
}

data class StockPrice(val symbol: String, val price: Double, val time: LocalDateTime)