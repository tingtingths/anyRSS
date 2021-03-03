package me.itdog.rssthis

import com.google.gson.JsonParser
import me.itdog.rssthis.rarbg.RarbgApiKt
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.Proxy

class RarbgApiTest {

    val api = RarbgApiKt(
        Proxy(Proxy.Type.SOCKS, InetSocketAddress("localhost", 58080))
    )

    @Test
    fun `Proxy test`() {
        println(api.ipInfo())
    }

    @Test
    fun `Search with string`() {
        val future = api.searchKeyword("mentalist", 1)
        val respJson = future.get()
        println(JsonParser().parse(respJson).asJsonObject)
    }

    @Test
    fun `Rate controlled search with imdb`() {
        for (i in 0..3) {
            api.searchImdb("tt1196946", 1).also { future ->
                println(JsonParser().parse(future.get()).asJsonObject)
            }
        }
    }
}
