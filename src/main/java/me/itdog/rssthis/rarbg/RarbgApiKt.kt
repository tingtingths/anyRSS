package me.itdog.rssthis.rarbg

import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.*

class RarbgApiKt {

    val APP_ID: String = "rssthis_rarbg-${UUID.randomUUID()}"
    var client: OkHttpClient

    init {
        client = OkHttpClient().newBuilder()
            // TODO : add token refresh interceptor
            .addInterceptor(TokenRefreshInterceptor())
            .build()
    }

    val REQ_WAIT_MILLIS = 2000L // rate control
    val BASE_URL = "https://torrentapi.org/pubapi_v2.php?app_id=$APP_ID"
    var token = ""
    var lastRequestMillis = 0L

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

            if (resp.isSuccessful && resp.body() != null) {
                val parser = JsonParser()
                val result = parser.parse(resp.body()!!.string()).asJsonObject
                if (result.has("error_code") && setOf<Int>(2, 4).contains(result.get("error_code").asInt)) {
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

            var token = ""
            if (resp.body()?.string() != null) {
                val parser = JsonParser()
                token = parser.parse(resp.body()!!.string()).asJsonObject
                    .getAsJsonPrimitive("token").asString
            }
            return token
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

    fun search(type: SearchType, value: String, limit: Integer?): String {
        return with(get("$BASE_URL&mode=search&${type.queryParam}=$value&limit=${limit ?: 25}")) {
            body()?.string() ?: ""
        }
    }

    fun searchKeyword(keyword: String, limit: Integer?): String {
        return search(SearchType.KEYWORD, keyword, limit)
    }

    fun searchImdb(imdbId: String, limit: Integer?): String {
        return search(SearchType.IMDB, imdbId, limit)
    }
}
