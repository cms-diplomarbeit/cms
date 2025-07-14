package at.cms.api;

import at.cms.training.dto.EmbeddingDto;
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
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

public class QdrantService {
    private final String qdrant_Server_URL = "http://file1.lan.elite-zettl.at";
    QdrantClient client = new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrant_Server_URL, 6334, false).build());

    public List<String> search_Document_Chunks(float[] vector) throws IOException, InterruptedException {
        try {
            var requestBody = Map.of(
                    "vector", vector,
                    "top", 3
            );

            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(qdrant_Server_URL + "/collections/knowledge/points/search"))
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
        } catch (IOException | InterruptedException e) {
            throw new IOException("Error during search in Qdrant: " + e.getMessage(), e);
        }
    }



    public void createCollectionAndAddVectors(String id, String documentID, float[] vectors, EmbeddingDto metaData) throws IOException {
        try {
            // Qdrant doku anschauen
            // die collection soll wie die datei heißen und tiefe soll von der länge der Vektoren abhängen
            //client.createCollectionAsync(metaData)
        } catch (Exception e) {
            throw new IOException("Error during upsert in Qdrant: " + e.getMessage(), e);
        }
    }
}
