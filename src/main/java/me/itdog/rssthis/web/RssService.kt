package me.itdog.rssthis.web

import com.google.gson.*
import me.itdog.rssthis.evaluate.HtmlEvaluator
import me.itdog.rssthis.evaluate.XPathEvaluator
import me.itdog.rssthis.rarbg.RarbgApi
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import spark.Spark
import java.io.StringWriter
import java.net.Proxy
import java.net.SocketException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.concurrent.Future
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.IntStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.min

class RssService(val port: Int, val proxy: Proxy?) {

    val rarbgApi = RarbgApi(proxy)
    val logger = LoggerFactory.getLogger(javaClass)
    val httpClient = OkHttpClient.Builder()
        .proxy(proxy)
        .build()

    constructor() : this(80, null)

    constructor(port: Int) : this(port, null)

    init {
        web()
    }

    private fun XPathEvaluator.trySetSource(src: String) {
        try {
            this.setSource(URI(src))
        } catch (ignored: URISyntaxException) {
            this.setSource(src)
        }
    }

    @Throws(ParserConfigurationException::class, TransformerException::class)
    private fun buildXml(
        titleStrValues: List<String>,
        linkStrValues: List<String>,
        descStrValues: List<String>
    ): String {
        data class Zipped(val title: String, val link: String, val desc: String)

        val docFactory = DocumentBuilderFactory.newInstance()
        val docBuilder = docFactory.newDocumentBuilder()

        // root elements
        val doc = docBuilder.newDocument()
        val rss = doc.createElement("rss").apply {
            setAttribute("version", "2.0")
            doc.appendChild(this)
        }
        val channel = doc.createElement("channel").apply {
            rss.appendChild(this)
        }

        // zip arrays
        val zipped = IntStream
            .range(0, min(min(titleStrValues.size, linkStrValues.size), descStrValues.size))
            .mapToObj { i ->
                Zipped(
                    titleStrValues[i],
                    linkStrValues[i],
                    descStrValues[i]
                )
            }
            .collect(Collectors.toList())

        // create item
        zipped.forEach { zip ->
            val title = zip.title
            val link = zip.link
            val desc = zip.desc
            val item = doc.createElement("item").apply {
                channel.appendChild(this)
            }
            doc.createElement("title").apply {
                this.appendChild(doc.createTextNode(title))
                item.appendChild(this)
            }
            doc.createElement("description").apply {
                this.appendChild(doc.createTextNode(desc))
                item.appendChild(this)
            }
            doc.createElement("link").apply {
                this.appendChild(doc.createTextNode(link))
                item.appendChild(this)
            }
        }
        return StringWriter().let {
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.transform(DOMSource(doc), StreamResult(it))
            it.buffer.toString()
        }
    }

    private fun web() {
        Spark.port(port)

        // endpoint /xeva, html xpath evaluate
        Spark.get("/xeva") { req, resp ->
            val evaluator = HtmlEvaluator()

            // params
            val src = req.queryParams("src") // src could be uri or base64 encoded xml
            val queries = req.queryParamsValues("xpath").toList()
            var evaluatorSrc: String

            if (src == null || src.isBlank() || queries.isEmpty()) {
                resp.status(400)
                return@get "Invalid parameters."
            }

            if (Base64.isBase64(src)) {
                evaluatorSrc = String(Base64.decodeBase64(src))
            } else {
                evaluatorSrc = src
                try {
                    // fetch source
                    val srcResp = httpClient.newCall(
                        Request.Builder()
                            .url(URI(src).toURL())
                            .build()
                    ).execute()
                    if (!srcResp.isSuccessful || srcResp.body() == null) {
                        resp.status(400)
                        return@get "Failed to retrieve source."
                    }
                    evaluatorSrc = srcResp.body()!!.string()
                } catch (ignored: URISyntaxException) {
                } catch (e: SocketException) {
                    resp.status(400)
                    return@get "Failed to retrieve source. ${e.message}"
                }
            }
            evaluator.setSource(evaluatorSrc)

            val queryResult = HashMap<String, List<String>>()
            queries.forEach { query ->
                val items = ArrayList<String>()
                val result = evaluator.evaluate(query)
                result.iterator().forEachRemaining {
                    items.add(it.stringValue)
                }
                queryResult[query] = items
            }

            val respObj = JsonObject().apply {
                add("src", JsonPrimitive(src))
                add("result", Gson().toJsonTree(queryResult))
            }

            resp.type("application/json")
            return@get respObj
        }

        // endpoint /rssthis, html -> rss
        Spark.get("/rssthis") { req, resp ->
            val evaluator = HtmlEvaluator()

            // params
            var src = req.queryParams("src")
            val titleXPath = req.queryParams("title_xpath")
            val linkXPath = req.queryParams("link_xpath")
            val descXPath = req.queryParams("desc_xpath")

            if (src.isBlank() || titleXPath.isBlank() || linkXPath.isBlank()) {
                resp.status(400)
                return@get "Invalid parameters."
            }

            if (Base64.isBase64(src))
                src = String(Base64.decodeBase64(src))
            evaluator.trySetSource(src)

            val titleStrValues = evaluator.evaluate(titleXPath).map { it.stringValue }
            val linkStrValues = evaluator.evaluate(linkXPath).map { it.stringValue }
            val descStrValues = evaluator.evaluate(descXPath).map { it.stringValue }

            resp.type("application/xml")
            return@get buildXml(titleStrValues, linkStrValues, descStrValues)
        }

        // endpoint /rarbg, rarbg api wrap
        Spark.get("/rarbg") { req, resp ->
            val evaluator = HtmlEvaluator()

            // params
            val searchKeywords = req.queryParams("search_string")
            val searchImdb = req.queryParams("search_imdb")
            val limit = try {
                Integer.parseInt(req.queryParams("limit"))
            } catch (e: NumberFormatException) {
                null
            }
            val regex = req.queryParams("regex")

            var future: Future<String>
            if (!StringUtils.isBlank(searchKeywords)) {
                future = rarbgApi.searchKeyword(searchKeywords, limit)
            } else if (!StringUtils.isBlank(searchImdb)) {
                future = rarbgApi.searchImdb(searchImdb, limit)
            } else {
                resp.status(400)
                return@get "Invalid parameters."
            }
            val searchResult = future.get() // wait until it's done
            logger.info("search_result=$searchResult")

            val apiResult = JsonParser().parse(searchResult)
                ?.asJsonObject
                ?.getAsJsonArray("torrent_results")

            if (apiResult == null) {
                resp.status(500)
                return@get "Failed to parse search result. $searchResult"
            }

            if (!StringUtils.isBlank(regex)) {
                val pattern = Pattern.compile(regex)
                val toRemove: MutableSet<JsonElement> = HashSet()
                apiResult.forEach {
                    val title = it.asJsonObject.getAsJsonPrimitive("filename")
                        .asString
                    if (!pattern.matcher(title).matches()) toRemove.add(it)
                }
                toRemove.forEach { apiResult.remove(it) }
            }

            val titleStrValues = ArrayList<String>()
            val linkStrValues = ArrayList<String>()
            val descStrValues = ArrayList<String>()
            apiResult.map { it.asJsonObject }.forEach {
                titleStrValues.add(it.getAsJsonPrimitive("filename").asString)
                linkStrValues.add(it.getAsJsonPrimitive("download").asString)
                descStrValues.add(it.getAsJsonPrimitive("category").asString)
            }

            resp.type("application/xml")
            return@get buildXml(titleStrValues, linkStrValues, descStrValues)
        }
    }
}
