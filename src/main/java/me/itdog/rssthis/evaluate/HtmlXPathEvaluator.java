package me.itdog.rssthis.evaluate;

import net.sf.saxon.s9api.SaxonApiException;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;

public class HtmlXPathEvaluator extends XPathEvaluator {

    public HtmlXPathEvaluator() {
        super();
        compiler.declareNamespace("", "http://www.w3.org/1999/xhtml");
    }

    @Override
    Source buildSource(InputStream is) throws SaxonApiException {
        return new SAXSource(new Parser(), new InputSource(is));
    }
}
