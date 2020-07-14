package me.itdog.rssthis.rarbg;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RarbgApi {

    private static final long WAIT_MILLIS = 2000L;
    private static String APP_ID = "rss_this_rarbg";
    private static final String BASE_URL = "https://torrentapi.org/pubapi_v2.php?app_id=" + APP_ID;
    private OkHttpClient client;
    private String token = "";
    private long lastReq = 0;

    public RarbgApi() {
        APP_ID += "_" + UUID.randomUUID().toString();
        client = new OkHttpClient.Builder()
                .addInterceptor(new TokenRefreshInterceptor())
                .build();
    }

    private Response get(String urlStr) throws IOException {
        Request req = new Request.Builder()
                .get()
                .url(urlStr)
                .build();
        return client.newCall(req).execute();
    }

    public String searchKeywords(String keyword, Integer limit) throws IOException {
        return search(SearchType.KEYWORDS, keyword, limit);
    }

    public String searchImdb(String imdbId, Integer limit) throws IOException {
        return search(SearchType.IMDB, imdbId, limit);
    }

    public String search(SearchType searchType, String searchStr, Integer limit) throws IOException {
        limit = limit == null ? 25 : limit;
        Response resp = get(BASE_URL + "&mode=search&token=" + token + "&" + searchType.paramValue() + "=" + searchStr + "&limit=" + limit);
        return resp.body().string();
    }

    public enum SearchType {
        KEYWORDS("search_string"), IMDB("search_imdb");

        private String paramValue;

        SearchType(String paramValue) {
            this.paramValue = paramValue;
        }

        public String paramValue() {
            return paramValue;
        }
    }

    class TokenRefreshInterceptor implements Interceptor {

        private final Set<Integer> TOKEN_ERRORS = new HashSet<Integer>() {{
            add(2);
            add(4);
        }};

        private String getToken() throws IOException {
            String urlStr = BASE_URL + "&get_token=get_token";
            Request req = new Request.Builder()
                    .get()
                    .url(urlStr)
                    .build();

            OkHttpClient _client = new OkHttpClient.Builder()
                    .build();

            Response resp = _client.newCall(req).execute();
            lastReq = System.currentTimeMillis();

            JsonParser parser = new JsonParser();
            String s = resp.body().string();
            JsonObject obj = parser.parse(s).getAsJsonObject();
            return obj.getAsJsonPrimitive("token").getAsString();
        }

        private Request updateUrl(Request req) {
            String query = req.url().queryParameterNames().stream()
                    .map((k) -> {
                        if (k.equalsIgnoreCase("token"))
                            return k + "=" + token;

                        return req.url().queryParameterValues(k).stream()
                                .map((v) -> k + "=" + v)
                                .reduce((q, vAcc) -> vAcc + "&" + q)
                                .orElse("");
                    }).collect(Collectors.joining("&"));

            String url = String.format("%s://%s:%d%s?%s",
                    req.url().scheme(),
                    req.url().host(),
                    req.url().port(),
                    req.url().encodedPath(),
                    query);

            return new Request.Builder()
                    .url(url)
                    .headers(req.headers())
                    .build();
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request req = chain.request();

            long toSleep = WAIT_MILLIS - (System.currentTimeMillis() - lastReq);
            if (toSleep > WAIT_MILLIS) {
                try {
                    Thread.sleep(toSleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // if token not included
            if (String.join("", req.url().queryParameterValues("token")).isEmpty()) {
                token = getToken();
                req = updateUrl(req);
            }

            Response resp = chain.proceed(req);
            lastReq = System.currentTimeMillis();

            if (resp.isSuccessful() && resp.body() != null) {
                JsonParser parser = new JsonParser();

                ResponseBody responseBody = resp.body();
                BufferedSource source = responseBody.source();
                source.request(Long.MAX_VALUE); // request the entire body.
                Buffer buffer = source.buffer();
                // clone buffer before reading from it
                String respString = buffer.clone().readString(StandardCharsets.UTF_8);
                JsonObject result = parser.parse(respString).getAsJsonObject();


                if (result.has("error_code") && TOKEN_ERRORS.contains(result.get("error_code").getAsInt())) {
                    // refresh token
                    token = getToken();

                    resp = chain.proceed(updateUrl(req));
                    lastReq = System.currentTimeMillis();
                }
            }

            return resp;
        }
    }
}
