package at.cms.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QdrantService {
    private final String QDRANT_URL = "http://localhost:6333";

    public List<String> search(float[] vector) throws IOException, InterruptedException {
        var requestBody = Map.of(
            "vector", vector,
            "top", 3
        );

        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder()
            .uri(URI.create(QDRANT_URL + "/collections/knowledge/points/search"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(requestBody)))
            .build();

        var response = client.send(req, HttpResponse.BodyHandlers.ofString());
        var json = new ObjectMapper().readTree(response.body());
        List<String> results = new ArrayList<>();
        for (JsonNode hit : json.get("result")) {
            results.add(hit.get("payload").get("text").asText());
        }
        return results;
    }
}
