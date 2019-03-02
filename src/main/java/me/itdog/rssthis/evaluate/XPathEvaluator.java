package me.itdog.rssthis.evaluate;

import net.sf.saxon.s9api.*;
import org.apache.commons.io.IOUtils;

import javax.xml.transform.Source;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public abstract class XPathEvaluator {

    Processor processor;
    XPathCompiler compiler;
    Source source;
    XdmNode root;

    public XPathEvaluator() {
        processor = new Processor(false);
        compiler = processor.newXPathCompiler();
    }

    abstract Source buildSource(InputStream is) throws SaxonApiException;

    public void setSource(URI uri) throws IOException, SaxonApiException {
        InputStream is = uri.toURL().openConnection().getInputStream();
        source = buildSource(is);
        root = processor.newDocumentBuilder().build(source);
    }

    public void setSource(File file) throws IOException, SaxonApiException {
        source = buildSource(new FileInputStream(file));
        root = processor.newDocumentBuilder().build(source);
    }

    public void setSource(String src) throws SaxonApiException {
        this.source = buildSource(IOUtils.toInputStream(src, StandardCharsets.UTF_8));
        root = processor.newDocumentBuilder().build(source);
    }

    public XdmValue evaluate(String xpath) throws SaxonApiException {
        return compiler.evaluate(xpath, root);
    }
}
