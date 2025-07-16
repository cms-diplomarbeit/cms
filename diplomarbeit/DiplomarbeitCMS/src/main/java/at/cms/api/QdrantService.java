package at.cms.api;

import at.cms.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.*;

public class QdrantService {
    private final String qdrant_Server_URL;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public QdrantService() {
        this.qdrant_Server_URL = AppConfig.getQdrantUrl();
    }

    public List<String> search(float[] vector) throws IOException, InterruptedException {
        var requestBody = Map.of("vector", vector, "top", 3);
        var req = HttpRequest.newBuilder()
                .uri(URI.create(qdrant_Server_URL + "/collections/knowledge/points/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        var response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        var json = mapper.readTree(response.body());

        List<String> results = new ArrayList<>();
        for (JsonNode hit : json.get("result")) {
            results.add(hit.get("payload").get("text").asText());
        }
        return results;
    }

    public void upsertVector(String collectionName, String pointId, float[] vector, Map<String, Object> payload) throws IOException, InterruptedException {
        var point = Map.of("id", pointId, "vector", vector, "payload", payload);
        var body = Map.of("points", List.of(point));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(qdrant_Server_URL + "/collections/" + collectionName + "/points?wait=true"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Qdrant upsert failed: " + response.statusCode() + " - " + response.body());
        }
    }

    public void deleteByPayloadValue(String collectionName, String key, String value) {
        try {
            var filter = Map.of("must", List.of(Map.of("key", key, "match", Map.of("value", value))));
            var deleteRequest = Map.of("filter", filter);

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(qdrant_Server_URL + "/collections/" + collectionName + "/points/delete"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(deleteRequest)))
                    .build();

            var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IOException("Qdrant delete failed: " + res.statusCode() + " - " + res.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}