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
    private final String qdrant_Server_URL;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public QdrantService(String qdrant_Server_URL) {
        this.qdrant_Server_URL = qdrant_Server_URL;
    }

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
        System.out.println("Upserting " + embeddings.getEmbeddings().length + " vectors for document: " + documentId + " in collection: " + collectionName);
        List<Map<String, Object>> points = new ArrayList<>();
        float[][] vectors = embeddings.getEmbeddings();
        List<String> chunks = embeddings.getTexts();
        
        if (vectors.length != chunkIds.size()) {
            throw new IllegalArgumentException("Number of embeddings (" + vectors.length + ") does not match number of chunk IDs (" + chunkIds.size() + ")");
        }
        
        for (int i = 0; i < vectors.length; i++) {
            Map<String, Object> point = Map.of(
                "id", UUID.randomUUID().toString(),
                "vector", vectors[i],
                "payload", Map.of(
                    "document_id", documentId,
                    "chunk_id", chunkIds.get(i),
                    "chunk_index", i,
                    "collection_name", collectionName,
                    "text", chunks.get(i)
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
        System.out.println("Successfully upserted " + points.size() + " vectors");
    }

    public List<String> searchDocumentChunks(float[] vector) throws IOException, InterruptedException {
        List<String> allResults = new ArrayList<>();
        
        // Get list of all collections
        var collectionsRequest = HttpRequest.newBuilder()
                .uri(URI.create(qdrant_Server_URL + "/collections"))
                .GET()
                .build();
                
        var collectionsResponse = httpClient.send(collectionsRequest, HttpResponse.BodyHandlers.ofString());
        var collectionsJson = mapper.readTree(collectionsResponse.body());
        
        // Search in each collection
        for (JsonNode collection : collectionsJson.get("result").get("collections")) {
            String collectionName = collection.get("name").asText();
            System.out.println("Searching in collection: " + collectionName);
            
            var requestBody = Map.of(
                "vector", vector,
                "top", 5,
                "score_threshold", 0.7, 
                "with_payload", true 
            );

            var searchRequest = HttpRequest.newBuilder()
                    .uri(URI.create(qdrant_Server_URL + "/collections/" + collectionName + "/points/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();

            var response = httpClient.send(searchRequest, HttpResponse.BodyHandlers.ofString());
            var json = mapper.readTree(response.body());
            
            if (json.has("result")) {
                for (JsonNode hit : json.get("result")) {
                    try {
                        JsonNode payload = hit.get("payload");
                        if (payload == null || payload.isNull()) {
                            System.out.println("Warning: Null payload found in collection " + collectionName);
                            continue;
                        }
                        
                        String text = payload.has("text") ? payload.get("text").asText("No text available") : "No text available";
                        
                        String result = String.format(text);
                        allResults.add(result);
                    } catch (Exception e) {
                        System.out.println("Warning: Error processing hit in collection " + collectionName + ": " + e.getMessage());
                    }
                }
            }
        }
        
        if (allResults.isEmpty()) {
            return List.of("No matching results found in any collection.");
        }
        
        // Sort results by score (higher scores first)
        allResults.sort((a, b) -> {
            try {
                float scoreA = Float.parseFloat(a.substring(7, a.indexOf('\n')));
                float scoreB = Float.parseFloat(b.substring(7, b.indexOf('\n')));
                return Float.compare(scoreB, scoreA);
            } catch (Exception e) {
                return 0; // Keep original order if parsing fails
            }
        });
        
        return allResults.stream()
                        .limit(10)
                        .toList();
    }

    public void deleteCollection(String collectionName) throws IOException, InterruptedException {
        System.out.println("Deleting collection: " + collectionName);
        
        var request = HttpRequest.newBuilder()
                .uri(URI.create(qdrant_Server_URL + "/collections/" + collectionName))
                .DELETE()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to delete collection: " + response.statusCode() + " - " + response.body());
        }
        System.out.println("Successfully deleted collection: " + collectionName);
    }
}
