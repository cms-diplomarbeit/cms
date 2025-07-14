package at.cms.api;

import at.cms.training.dto.EmbeddingDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.*;

public class QdrantService {
    private final String qdrant_Server_URL = "http://file1.lan.elite-zettl.at:6333";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<String> search_Document_Chunks(float[] vector) throws IOException, InterruptedException {
        var requestBody = Map.of(
                "vector", vector,
                "top", 3
        );

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

    public void createCollectionAndAddVectors(int id, String documentID, float[] vectors, EmbeddingDto metaData) throws IOException, InterruptedException {
        createCollection(documentID, vectors.length);
        addVectorPoint(documentID, id, vectors, documentID, metaData.getChunk_index());
    }

    private void createCollection(String collectionName, int vectorSize) throws IOException, InterruptedException {
        var collectionConfig = Map.of(
                "vectors", Map.of(
                        "size", vectorSize,
                        "distance", "Dot"
                )
        );

        var request = HttpRequest.newBuilder()
                .uri(URI.create(qdrant_Server_URL + "/collections/" + collectionName))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(collectionConfig)))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 409) {
            throw new IOException("Failed to create collection: " + response.statusCode() + " - " + response.body());
        }
    }

    private void addVectorPoint(String collectionName, int chunkId, float[] vectors, String documentID, int chunkIndex) throws IOException, InterruptedException {
        var point = Map.of(
                "id", chunkId,
                "vector", vectors,
                "payload", Map.of(
                        "documentId", documentID,
                        "chunkIndex", chunkIndex
                )
        );

        var requestBody = Map.of("points", List.of(point));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(qdrant_Server_URL + "/collections/" + collectionName + "/points"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to add vector point: " + response.statusCode() + " - " + response.body());
        }
    }
}