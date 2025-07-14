package at.cms.api;

import at.cms.training.dto.EmbeddingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EmbeddingService {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String embeddingServerAddress;

    public EmbeddingService(String embeddingServerAddress) {
        this.embeddingServerAddress = embeddingServerAddress;
        this.client = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public EmbeddingDto getEmbeddings(List<String> chunks) throws IOException, InterruptedException {
        List<float[]> allEmbeddings = new ArrayList<>();
        int chunk_index = 0;

        for (String chunk : chunks) {
            chunk_index++;
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "mxbai-embed-large");
            requestBody.put("input", chunk);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingServerAddress + "/api/embed"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<EmbeddingDto> response = client.send(request, jsonBodyHandler());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                float[][] responseEmbeddings = response.body().getEmbeddings();
                if (responseEmbeddings != null && responseEmbeddings.length > 0) {
                    allEmbeddings.add(responseEmbeddings[0]);
                }
            } else {
                throw new IOException("Embedding API Error: " + response.statusCode());
            }
        }

        EmbeddingDto result = new EmbeddingDto();
        result.setEmbeddings(allEmbeddings.toArray(new float[allEmbeddings.size()][]));
        result.setChunk_index(chunk_index);
        return result;
    }

    // JSON Body Handler von den Embeddings
    private HttpResponse.BodyHandler<EmbeddingDto> jsonBodyHandler() {
        return comedy_gold -> HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                body -> {
                    try {
                        return objectMapper.readValue(body, EmbeddingDto.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Error parsing JSON", e);
                    }
                }
        );
    }
}