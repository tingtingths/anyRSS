package me.itdog.rssthis.web;

import com.google.gson.*;
import me.itdog.rssthis.evaluate.HtmlXPathEvaluator;
import me.itdog.rssthis.evaluate.XPathEvaluator;
import me.itdog.rssthis.rarbg.RarbgApi;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class RssService {

    XPathEvaluator evaluator = new HtmlXPathEvaluator();
    RarbgApi rarbgApi = new RarbgApi();
    Logger logger = LoggerFactory.getLogger(getClass());

    private static String buildXml(List<String> titleStrValues, final List<String> linkStrValues, List<String> descStrValues) throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root elements
        Document doc = docBuilder.newDocument();
        Element rss = doc.createElement("rss");
        rss.setAttribute("version", "2.0");
        doc.appendChild(rss);

        Element channel = doc.createElement("channel");
        rss.appendChild(channel);

        class Zipped {
            String title;
            String link;
            String desc;

            Zipped(String title, String link, String desc) {
                this.title = title;
                this.link = link;
                this.desc = desc;
            }
        }

        // zip arrays
        List<Zipped> zipped = IntStream
                .range(0, Math.min(Math.min(titleStrValues.size(), linkStrValues.size()), descStrValues.size()))
                .mapToObj(i -> new Zipped(titleStrValues.get(i), linkStrValues.get(i), descStrValues.get(i)))
                .collect(Collectors.toList());


        // create item
        zipped.forEach((zip) -> {
            String title = zip.title;
            String link = zip.link;
            String desc = zip.desc;

            Element item = doc.createElement("item");
            channel.appendChild(item);

            Element titleEle = doc.createElement("title");
            titleEle.appendChild(doc.createTextNode(title));
            item.appendChild(titleEle);

            Element descEle = doc.createElement("description");
            descEle.appendChild(doc.createTextNode(desc));
            item.appendChild(descEle);

            Element linkEle = doc.createElement("link");
            linkEle.appendChild(doc.createTextNode(link));
            item.appendChild(linkEle);
        });

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);

        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);

        return writer.getBuffer().toString();
    }

    @GetMapping(value = "/xeva", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonElement xpathEva(
            @RequestParam(value = "src") String src,
            @RequestParam(value = "xpath") List<String> queries) throws IOException, SaxonApiException {
        if (Base64.isBase64(src))
            src = new String(Base64.decodeBase64(src));

        setEvaluatorSrc(src, evaluator);

        Map<String, List<String>> queriesResults = new HashMap<>();
        for (String xpath : queries) {
            List<String> items = new ArrayList<>();
            XdmValue result = evaluator.evaluate(xpath);
            result.iterator().forEachRemaining((item) -> {
                items.add(item.getStringValue());
            });
            queriesResults.put(xpath, items);
        }

        JsonObject respObj = new JsonObject();
        respObj.add("src", new JsonPrimitive(src));
        respObj.add("result", new Gson().toJsonTree(queriesResults));
        return respObj;
    }

    @GetMapping(value = "/rssthis", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> rssThis(
            @RequestParam(value = "src") String src,
            @RequestParam(value = "title_xpath") String titleXPath,
            @RequestParam(value = "link_xpath") String linkXPath,
            @RequestParam(value = "desc_xpath") String descXPath) throws IOException, SaxonApiException, TransformerException, ParserConfigurationException {
        if (Base64.isBase64(src))
            src = new String(Base64.decodeBase64(src));

        setEvaluatorSrc(src, evaluator);

        List<String> titleStrValues = evaluator.evaluate(titleXPath).stream()
                .map(XdmItem::getStringValue)
                .collect(Collectors.toList());

        List<String> linkStrValues = evaluator.evaluate(linkXPath).stream()
                .map(XdmItem::getStringValue)
                .collect(Collectors.toList());

        List<String> descStrValues = evaluator.evaluate(descXPath).stream()
                .map(XdmItem::getStringValue)
                .collect(Collectors.toList());

        return ResponseEntity.ok(buildXml(titleStrValues, linkStrValues, descStrValues));
    }

    @GetMapping(value = "/rarbg", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> rarbgRss(
            @RequestParam(value = "search_string", defaultValue = "") String searchKeywords,
            @RequestParam(value = "search_imdb", defaultValue = "") String searchImdb,
            @RequestParam(value = "regex", required = false) String regex) throws IOException, TransformerException, ParserConfigurationException {
        String searchResult = null;

        if (searchImdb.isEmpty() && searchKeywords.isEmpty()) {
            return ResponseEntity.badRequest().body("No search param provided!");
        }

        if (!searchKeywords.isEmpty()) {
            searchResult = rarbgApi.searchKeywords(searchKeywords);
        } else if (!searchImdb.isEmpty()) {
            searchResult = rarbgApi.searchImdb(searchImdb);
        }

        Optional<JsonArray> arrOpt = Optional.ofNullable(new JsonParser().parse(searchResult)
                .getAsJsonObject()
                .getAsJsonArray("torrent_results"));

        logger.info("search_result=" + searchResult);

        if (!arrOpt.isPresent()) {
            return ResponseEntity.status(500).body("Failed to parse search result");
        }

        if (regex != null && !regex.isEmpty()) {
            Pattern pattern = Pattern.compile(regex);

            Set<JsonElement> toRemove = new HashSet<>();

            arrOpt.get().forEach((ele) -> {
                JsonObject obj = ele.getAsJsonObject();
                String title = obj.getAsJsonPrimitive("filename").getAsString();
                if (!pattern.matcher(title).matches())
                    toRemove.add(ele);
            });

            toRemove.forEach(arrOpt.get()::remove);
        }

        List<String> titleStrValues = new ArrayList<>();
        List<String> linkStrValues = new ArrayList<>();
        List<String> descStrValues = new ArrayList<>();
        arrOpt.get().forEach((ele) -> {
            JsonObject obj = ele.getAsJsonObject();
            titleStrValues.add(obj.getAsJsonPrimitive("filename").getAsString());
            linkStrValues.add(obj.getAsJsonPrimitive("download").getAsString());
            descStrValues.add(obj.getAsJsonPrimitive("category").getAsString());
        });

        return ResponseEntity.ok(buildXml(titleStrValues, linkStrValues, descStrValues));
    }

    private void setEvaluatorSrc(String src, XPathEvaluator evaluator) throws SaxonApiException, IOException {
        URI uri = null;
        try {
            uri = new URI(src);
        } catch (URISyntaxException ignored) {
        }

        if (uri == null)
            evaluator.setSource(src);
        else
            evaluator.setSource(uri);
    }
}
