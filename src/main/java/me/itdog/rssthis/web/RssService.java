package me.itdog.rssthis.web;

import com.google.gson.*;
import me.itdog.rssthis.evaluate.HtmlXPathEvaluator;
import me.itdog.rssthis.evaluate.XPathEvaluator;
import me.itdog.rssthis.rarbg.RarbgApi;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static spark.Spark.get;
import static spark.Spark.port;

public class RssService {

    XPathEvaluator evaluator = new HtmlXPathEvaluator();
    RarbgApi rarbgApi = new RarbgApi();
    Logger logger = LoggerFactory.getLogger(getClass());

    public RssService() {
        port(80);

        // html xpath evaluate
        get("/xeva", (req, resp) -> {
            // params
            String src = req.queryParams("src");
            List<String> queries = Arrays.asList(req.queryParamsValues("xpath"));

            if (src == null || src.isEmpty() || queries.isEmpty()) {
                resp.status(400);
                return "Invalid parameters.";
            }

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

            resp.type("application/json");
            return respObj;
        });

        // html -> rss
        get("/rssthis", (req, resp) -> {
            // params
            String src = req.queryParams("src");
            String titleXPath = req.queryParams("title_xpath");
            String linkXPath = req.queryParams("link_xpath");
            String descXPath = req.queryParams("desc_xpath");

            if (isBlank(src) || isBlank(titleXPath) || isBlank(linkXPath) || isBlank(descXPath)) {
                resp.status(400);
                return "Invalid parameters.";
            }

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

            resp.type("application/xml");
            return buildXml(titleStrValues, linkStrValues, descStrValues);
        });

        // rarbg api wrap
        get("/rarbg", (req, resp) -> {
            // params
            String searchKeywords = req.queryParams("search_string");
            String searchImdb = req.queryParams("search_imdb");
            Integer limit = tryParse(req.queryParams("limit"));
            String regex = req.queryParams("regex");

            String searchResult = null;

            if (!StringUtils.isBlank(searchKeywords)) {
                searchResult = rarbgApi.searchKeywords(searchKeywords, limit);
            } else if (!StringUtils.isBlank(searchImdb)) {
                searchResult = rarbgApi.searchImdb(searchImdb, limit);
            } else {
                resp.status(400);
                return "Invalid parameters.";
            }

            Optional<JsonArray> arrOpt = Optional.ofNullable(new JsonParser().parse(searchResult)
                    .getAsJsonObject()
                    .getAsJsonArray("torrent_results"));

            logger.info("search_result=" + searchResult);

            if (!arrOpt.isPresent()) {
                resp.status(500);
                return "Failed to parse search result";
            }

            if (!isBlank(regex)) {
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

            resp.type("application/xml");
            return buildXml(titleStrValues, linkStrValues, descStrValues);
        });
    }

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

    static Integer tryParse(String text) {
        return tryParse(text, null);
    }

    static Integer tryParse(String text, Integer defValue) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defValue;
        }
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
