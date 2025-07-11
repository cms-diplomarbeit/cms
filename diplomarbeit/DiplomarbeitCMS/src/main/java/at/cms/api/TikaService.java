package at.cms.api;

import at.cms.training.dto.EmbeddingDto;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TikaService {
    private static final Logger log = Logger.getLogger(TikaService.class.getName());
    private final String tikaServerAddress;
    private final HttpClient client;

    public TikaService(String tikaServerAddress) {
        this.tikaServerAddress = tikaServerAddress;
        this.client = HttpClient.newHttpClient();
    }

    public String getServerAddress() {
        return tikaServerAddress;
    }

    public String extractContent(byte[] byteFile) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikaServerAddress + "/tika"))
                .header("Accept", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(byteFile))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warning("Tika server returned status code: " + response.statusCode());
                throw new IOException("Failed to extract content. Status code: " + response.statusCode());
            }

            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Content extraction was interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to extract content", e);
        }
    }

    // TIKA 
    public JSONObject getMetadataWithTika(final byte[] content) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikaServerAddress + "/rmeta"))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            org.json.JSONArray metadataArray = new org.json.JSONArray(response.body());
            JSONObject combinedMetadata = new JSONObject();
            
            if (metadataArray.length() > 0) {
                JSONObject metadata = metadataArray.getJSONObject(0);
                
                // Title - prefer PDF specific title if available
                if (metadata.has("pdf:docinfo:title")) {
                    combinedMetadata.put("title", metadata.getString("pdf:docinfo:title"));
                } else if (metadata.has("dc:title")) {
                    combinedMetadata.put("title", metadata.getString("dc:title"));
                }
                
                // Language
                if (metadata.has("dc:language")) {
                    combinedMetadata.put("language", metadata.getString("dc:language"));
                }
                
                // Content Length
                if (metadata.has("Content-Length")) {
                    combinedMetadata.put("length", metadata.getInt("Content-Length"));
                }
                
                // Page Count
                if (metadata.has("xmpTPg:NPages")) {
                    combinedMetadata.put("pageCount", metadata.getInt("xmpTPg:NPages"));
                }
            }
            
            return combinedMetadata;
        } else {
            throw new IOException("HTTP Error: " + response.statusCode() + "\nResponse: " + response.body());
        }
    }
} 