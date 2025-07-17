package at.cms.rag;

import jakarta.enterprise.context.ApplicationScoped;
import at.cms.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OllamaService {
    private final String ollamaUrl;

    public OllamaService() {
        this.ollamaUrl = AppConfig.getOllamaUrl();
    }

    public String generateAnswer(String context, String question) throws IOException, InterruptedException {
        String prompt = String.format("""
            You are a helpful assistant. Answer the question using the following context:

            Context:
            %s

            Question:
            %s

            Answer:
            """, context, question);

        var request = Map.of(
            "model", "mistral",
            "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl + "/api/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(request)))
            .build();

        var response = client.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode jsonNode = new ObjectMapper().readTree(response.body());
        return jsonNode.get("message").get("content").asText();
    }
}
