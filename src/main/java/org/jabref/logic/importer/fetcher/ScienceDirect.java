package org.jabref.logic.importer.fetcher;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import org.jabref.logic.importer.FulltextFetcher;
import org.jabref.logic.identifier.DOI;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.FieldName;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * FulltextFetcher implementation that attempts to find a PDF URL at ScienceDirect.
 *
 * @see http://dev.elsevier.com/
 */
public class ScienceDirect implements FulltextFetcher {
    private static final Log LOGGER = LogFactory.getLog(ScienceDirect.class);

    private static final String API_URL = "http://api.elsevier.com/content/article/doi/";
    private static final String API_KEY = "fb82f2e692b3c72dafe5f4f1fa0ac00b";
    @Override
    public Optional<URL> findFullText(BibEntry entry) throws IOException {
        Objects.requireNonNull(entry);

        // Try unique DOI first
        Optional<DOI> doi = entry.getField(FieldName.DOI).flatMap(DOI::build);

        if(doi.isPresent()) {
            // Available in catalog?
            try {
                String sciLink = getUrlByDoi(doi.get().getDOI());

                // scrape the web page not as mobile client!
                if (!sciLink.isEmpty()) {
                    Document html = Jsoup.connect(sciLink)
                            .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                            .referrer("http://www.google.com")
                            .ignoreHttpErrors(true).get();

                    // Retrieve PDF link (old page)
                    Element link = html.getElementById("pdfLink");

                    if (link != null) {
                        LOGGER.info("Fulltext PDF found @ ScienceDirect (old page).");
                        Optional<URL> pdfLink = Optional.of(new URL(link.attr("pdfurl")));
                        return pdfLink;
                    }
                    // Retrieve PDF link (new page)
                    String url = html.getElementsByClass("pdf-download-btn-link").attr("href");

                    if (url != null) {
                        LOGGER.info("Fulltext PDF found @ ScienceDirect (new page).");
                        Optional<URL> pdfLink = Optional.of(new URL("http://www.sciencedirect.com" + url));
                        return pdfLink;
                    }
                }
            } catch(UnirestException e) {
                LOGGER.warn("ScienceDirect API request failed", e);
            }
        }
        return Optional.empty();
    }

    private String getUrlByDoi(String doi) throws UnirestException {
        String sciLink = "";
        try {
            String request = API_URL + doi;
            HttpResponse<JsonNode> jsonResponse = Unirest.get(request)
                    .header("X-ELS-APIKey", API_KEY)
                    .queryString("httpAccept", "application/json")
                    .asJson();

            JSONObject json = jsonResponse.getBody().getObject();
            JSONArray links = json.getJSONObject("full-text-retrieval-response").getJSONObject("coredata").getJSONArray("link");

            for (int i=0; i < links.length(); i++) {
                JSONObject link = links.getJSONObject(i);
                if (link.getString("@rel").equals("scidir")) {
                    sciLink = link.getString("@href");
                }
            }
            return sciLink;
        } catch(JSONException e) {
            LOGGER.debug("No ScienceDirect link found in API request", e);
            return sciLink;
        }
    }
}
