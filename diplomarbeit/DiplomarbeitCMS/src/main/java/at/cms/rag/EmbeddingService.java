package at.cms.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class EmbeddingService {
    public float[] embed(String text) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var body = String.format("{\"texts\": [\"%s\"]}", text.replace("\"", "\\\""));

        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:5000/embed"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var node = new ObjectMapper().readTree(response.body());
        ArrayNode embeddingArray = (ArrayNode) node.get("embeddings").get(0);
        float[] embedding = new float[embeddingArray.size()];
        for (int i = 0; i < embeddingArray.size(); i++) {
            embedding[i] = (float) embeddingArray.get(i).asDouble();
        }
        return embedding;
    }
}