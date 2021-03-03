package me.itdog.rssthis.rarbg

import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.*
import java.util.concurrent.*

class RarbgApiKt {

    val APP_ID: String = "rssthis_rarbg-${UUID.randomUUID()}"
    var client: OkHttpClient
    val REQ_WAIT_MILLIS = 2000L // rate control
    val executor = ThreadPoolExecutor(
        1, 1, 60, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    ) // single thread request for rate control
    val BASE_URL = "https://torrentapi.org/pubapi_v2.php?app_id=$APP_ID"
    var token = ""
    var lastRequestMillis = 0L

    init {
        client = OkHttpClient().newBuilder()
            .addInterceptor(TokenRefreshInterceptor())
            .build()
        executor.allowCoreThreadTimeOut(true)
    }

    inner class TokenRefreshInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var req = chain.request()

            // rate control
            // if last request less then REQ_WAIT_MILLIS ago, wait until REQ_WAIT_MILLIS
            val toSleep = REQ_WAIT_MILLIS - (System.currentTimeMillis() - lastRequestMillis)
            if (toSleep > 0) {
                try {
                    Thread.sleep(toSleep)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // add/replace token
            req = req.newWithToken(token)

            var resp = chain.proceed(req)
            lastRequestMillis = System.currentTimeMillis()

            // retry request for token related error
            if (resp.isSuccessful && resp.body() != null) {
                val parser = JsonParser()
                val result = parser.parse(resp.body()!!.string()).asJsonObject
                if (result.has("error_code")
                    && setOf(2, 4).contains(result.get("error_code").asInt)
                ) {
                    token = getToken()
                    resp = chain.proceed(req.newWithToken(token))
                    lastRequestMillis = System.currentTimeMillis()
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
            val tokenClient = OkHttpClient.Builder().build()
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

    private fun get(url: String): Response {
        return Request.Builder()
            .get()
            .url(url)
            .build()
            .let {
                client.newCall(it).execute()
            }
    }

    fun search(type: SearchType, value: String, limit: Integer?): Future<String> {
        return executor.submit(Callable {
            get("$BASE_URL&mode=search&${type.queryParam}=$value&limit=${limit ?: 25}")
                .body()?.string() ?: ""
        })
    }

    fun searchKeyword(keyword: String, limit: Integer?): Future<String> {
        return search(SearchType.KEYWORD, keyword, limit)
    }

    fun searchImdb(imdbId: String, limit: Integer?): Future<String> {
        return search(SearchType.IMDB, imdbId, limit)
    }
}
