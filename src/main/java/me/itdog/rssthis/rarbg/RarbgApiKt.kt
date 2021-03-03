package me.itdog.rssthis.rarbg

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import java.net.Proxy
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.*

class RarbgApiKt {

    private val APP_ID: String = "rssthis_rarbg-${UUID.randomUUID()}"
    private val REQ_WAIT_MILLIS = 2000L // rate control
    private val executor = ThreadPoolExecutor(
        1, 1, 60, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    ) // single thread request for rate control
    private val BASE_URL = "https://torrentapi.org/pubapi_v2.php?app_id=$APP_ID"
    private var token = ""
    private var lastRequestMillis = 0L

    // okhttp
    private val client: OkHttpClient
    private val testClient: OkHttpClient

    init {
        executor.allowCoreThreadTimeOut(true)
    }

    constructor() {
        client = OkHttpClient().newBuilder()
            .addInterceptor(TokenRefreshInterceptor())
            .build()
        testClient = OkHttpClient().newBuilder()
            .build()
    }

    constructor(proxy: Proxy) {
        client = OkHttpClient().newBuilder()
            .addInterceptor(TokenRefreshInterceptor())
            .proxy(proxy)
            .build()
        testClient = OkHttpClient().newBuilder()
            .proxy(proxy)
            .build()
    }

    inner class TokenRefreshInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var req = chain.request()

            // rate control
            // if last request less then REQ_WAIT_MILLIS ago, wait until REQ_WAIT_MILLIS
            val toSleep = REQ_WAIT_MILLIS - (System.currentTimeMillis() - lastRequestMillis)
            if (toSleep > 0) {
                try {
                    println("[${DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now())}] Sleep $toSleep ms")
                    Thread.sleep(toSleep)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // add/replace token
            if (token.isEmpty()) token = getToken()
            req = req.newWithToken(token)

            var resp = chain.proceed(req)
            println("[${DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now())}] ${req.url()}")
            lastRequestMillis = System.currentTimeMillis()

            // retry request for token related error
            if (resp.isSuccessful && resp.body() != null) {
                val contentType = resp.body()!!.contentType()
                val content = resp.body()!!.string()

                val parser = JsonParser()
                val result = parser.parse(content).asJsonObject
                if (result.has("error_code")
                    && setOf(2, 4).contains(result.get("error_code").asInt)
                ) {
                    token = getToken()
                    resp = chain.proceed(req.newWithToken(token))
                    println("[${DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now())}] ${req.url()}")
                    lastRequestMillis = System.currentTimeMillis()
                } else {
                    // we have to create a new response object as we already consumed the content
                    resp = resp.newBuilder().body(ResponseBody.create(contentType, content)).build()
                }
            }

            return resp
        }

        private fun Request.newWithToken(token: String): Request {
            return this.newBuilder().url(
                this.url().newBuilder()
                    .setQueryParameter("token", token)
                    .build()
            ).build()
        }

        private fun getToken(): String {
            val tokenClient = OkHttpClient.Builder().build() // new client for token request, to avoid being intercepted
            val resp = tokenClient.newCall(
                Request.Builder()
                    .get()
                    .url("$BASE_URL&get_token=get_token")
                    .build()
            ).execute()
            lastRequestMillis = System.currentTimeMillis()

            // parse or return empty string
            return resp.body()?.string().let {
                val parser = JsonParser()
                parser.parse(it).asJsonObject.getAsJsonPrimitive("token").asString
            } ?: ""
        }
    }

    enum class SearchType(val queryParam: String) {
        KEYWORD("search_string"), IMDB("search_imdb")
    }

    fun ipInfo(): JsonObject {
        return Request.Builder()
            .get()
            .url("https://ipinfo.io/json")
            .build()
            .run {
                val resp = testClient.newCall(this).execute()
                val parser = JsonParser()
                parser.parse(resp.body()!!.string()).asJsonObject
            }
    }

    private fun get(url: String): Response {
        return Request.Builder()
            .get()
            .url(url)
            .build()
            .run { client.newCall(this).execute() }
    }

    fun search(type: SearchType, value: String, limit: Int?): Future<String> {
        return executor.submit(Callable {
            get("$BASE_URL&mode=search&${type.queryParam}=$value&limit=${limit ?: 25}")
                .body()?.string() ?: ""
        })
    }

    fun searchKeyword(keyword: String, limit: Int?): Future<String> {
        return search(SearchType.KEYWORD, keyword, limit)
    }

    fun searchImdb(imdbId: String, limit: Int?): Future<String> {
        return search(SearchType.IMDB, imdbId, limit)
    }
}
