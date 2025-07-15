package at.cms.api;

import at.cms.training.dto.EmbeddingDto;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class EmbeddingService {
    private final HttpClient client;
    private final String embeddingServerAddress = "http://file1.lan.elite-zettl.at:11434";

    public EmbeddingService() {
        this.client = HttpClient.newHttpClient();
    }

    public EmbeddingDto getEmbeddings(List<String> chunks) throws IOException, InterruptedException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "mxbai-embed-large");
        requestBody.put("input", new JSONArray(chunks));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingServerAddress + "/api/embed"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JSONObject jsonResponse = new JSONObject(response.body());
            JSONArray embeddingsArray = jsonResponse.getJSONArray("embeddings");
            
            // Convert JSON array to float[][] directly
            float[][] embeddings = new float[embeddingsArray.length()][];
            for (int i = 0; i < embeddingsArray.length(); i++) {
                JSONArray embeddingArray = embeddingsArray.getJSONArray(i);
                embeddings[i] = new float[embeddingArray.length()];
                for (int j = 0; j < embeddingArray.length(); j++) {
                    embeddings[i][j] = (float) embeddingArray.getDouble(j);
                }
            }

            EmbeddingDto result = new EmbeddingDto();
            result.setModel("mxbai-embed-large");
            result.setEmbeddings(embeddings);
            result.setChunk_index(chunks.size());
            
            return result;
        } else {
            throw new IOException("Embedding API Error: " + response.statusCode() + " - " + response.body());
        }
    }
}