package me.itdog.rssthis.evaluate

import net.sf.saxon.s9api.Processor
import net.sf.saxon.s9api.XdmNode
import net.sf.saxon.s9api.XdmValue
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.xml.transform.Source


abstract class XPathEvaluator {

    protected val processor = Processor(false)
    protected val compiler = processor.newXPathCompiler()
    protected lateinit var source: Source
    protected lateinit var root: XdmNode

    abstract fun buildSource(inputStream: InputStream): Source

    fun setSource(uri: URI) {
        source = buildSource(uri.toURL().openStream())
        root = processor.newDocumentBuilder().build(source)
    }

    fun setSource(file: File) {
        source = buildSource(file.inputStream())
        root = processor.newDocumentBuilder().build(source)
    }

    fun setSource(string: String) {
        source = buildSource(string.byteInputStream(StandardCharsets.UTF_8))
        root = processor.newDocumentBuilder().build(source)
    }

    fun evaluate(expression: String): XdmValue {
        return compiler.evaluate(expression, root)
    }
}
