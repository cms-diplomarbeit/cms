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
import java.util.UUID;

public class QdrantService {
    private final String qdrant_Server_URL = "http://file1.lan.elite-zettl.at:6333";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public void createCollectionAndInsertVectors(String filename, EmbeddingDto embeddings, String documentId, List<String> chunkIds) throws IOException, InterruptedException {
        createCollection(filename, embeddings);
        upsertVector(filename, embeddings, documentId, chunkIds);

        // Dokumentation: https://api.qdrant.tech/api-reference
    }

    public void createCollection(String filename, EmbeddingDto embeddings) throws IOException, InterruptedException {

        System.out.println("Creating collection for: " + filename);
        String collectionName = filename;
    
        int vectorSize = embeddings.getEmbeddings()[0].length;

        var collectionConfig = Map.of(
            "vectors", Map.of(
                "size", vectorSize,
                "distance", "Cosine",
                "on_disk", false
            ),
            "shard_number", 1,
            "replication_factor", 1,
            "write_consistency_factor", 1,
            "on_disk_payload", true
        );

        var request = HttpRequest.newBuilder()
                .uri(URI.create(qdrant_Server_URL + "/collections/" + collectionName))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(collectionConfig)))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to create collection: " + response.statusCode() + " - " + response.body());
        }
    }

    public void upsertVector(String collectionName, EmbeddingDto embeddings, String documentId, List<String> chunkIds) throws IOException, InterruptedException {
        System.out.println("Upserting vectors for: " + collectionName);
        List<Map<String, Object>> points = new ArrayList<>();
        float[][] vectors = embeddings.getEmbeddings();
        
        for (int i = 0; i < vectors.length; i++) {
            Map<String, Object> point = Map.of(
                "id", UUID.randomUUID().toString(),
                "vector", vectors[i],
                "payload", Map.of(
                    "document_id", documentId,
                    "chunk_id", chunkIds.get(i),
                    "chunk_index", i,
                    "collection_name", collectionName
                )
            );
            points.add(point);
        }

        var body = Map.of(
            "points", points,
            "wait", true
        );

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

    public List<String> searchDocumentChunks(float[] vector) throws IOException, InterruptedException {
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
