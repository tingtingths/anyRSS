package me.itdog.rssthis.evaluate

import org.ccil.cowan.tagsoup.Parser
import org.xml.sax.InputSource
import java.io.InputStream
import javax.xml.transform.Source
import javax.xml.transform.sax.SAXSource


class HtmlEvaluator : XPathEvaluator() {

    init {
        compiler.declareNamespace("", "http://www.w3.org/1999/xhtml")
    }

    override fun buildSource(inputStream: InputStream): Source {
        return SAXSource(Parser(), InputSource(inputStream))
    }
}
