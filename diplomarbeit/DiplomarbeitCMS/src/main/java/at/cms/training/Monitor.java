package at.cms.training;

import at.cms.ingestion.SampleTextSplitter;
import at.cms.training.db.Repository;
// Filewathcher, Tika & Embedding
import at.cms.training.dto.EmbeddingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.nio.file.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.json.JSONObject;
import java.io.File;

import java.util.ArrayList;
import java.sql.*;

/* 
// Initialize the Qdrant
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

// Create Collection
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

// Add Vectors
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.UpdateResult;
import static io.qdrant.client.ConditionFactory.matchKeyword;

// Run a Query
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.QueryPoints;
import static io.qdrant.client.QueryFactory.nearest;
*/

/**
 * Monitor class that watches a directory for document changes and processes them.
 * This class is responsible for:
 * 1. Detecting new, modified, or deleted documents in a watched directory
 * 2. Extracting text from documents using Tika
 * 3. Creating embeddings from the extracted text using Ollama
 * 4. Storing the embeddings in Qdrant vector database
 */
public class Monitor {
    private static final Logger log = Logger.getLogger(Monitor.class.getName());
    private final String watchDir;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    // QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder("localhost", 6334, false).build());

    // Observer
    public Monitor(String watchDir) {
        this.watchDir = watchDir;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.objectMapper = new ObjectMapper();

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                checkFiles();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in monitor check loop", e);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    // Directory Observer with Stream Processing
    private void checkFiles() {
        try {
            Files.list(Paths.get(watchDir))
                .filter(path -> supportedExtensions.stream()
                    .anyMatch(ext -> path.toString().toLowerCase().endsWith(ext)))
                .forEach(this::checkAndProcessFiles);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error checking directory: " + e.getMessage(), e);
        }
    }

    private final List<String> supportedExtensions = List.of(".docx", ".pdf", ".xlsx", ".pptx", ".txt", ".md", ".csv");

    // Check and Process Files
    private void checkAndProcessFiles(Path filePath) {
        try {
            File file = filePath.toFile();
            String filename = filePath.getFileName().toString();
            byte[] content = Files.readAllBytes(filePath);
            String fileHash = new String(java.security.MessageDigest.getInstance("SHA-256").digest(content), StandardCharsets.UTF_8);
            
            // Check if file hash exists in database
            String checkHashSql = "SELECT file_Hash FROM documentValidation WHERE file_Hash = ?";
            try (Connection conn = Repository.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(checkHashSql);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setString(1, fileHash);
                
                if (!rs.next()) {
                    // Hash doesn't exist, insert new record
                    String insertSql = "INSERT INTO documentValidation (file_Hash, file_Name) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, fileHash);
                        insertStmt.setString(2, filename);
                        insertStmt.executeUpdate();
                        
                        // Extract metadata and store in documents table
                        JSONObject metadata = getMetadataWithTika(content);
                        
                        String insertDocSql = """
                            INSERT INTO documents (
                                id, title, content, source, author, created_at, 
                                language, word_count, last_updated
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                        
                        try (PreparedStatement docStmt = conn.prepareStatement(insertDocSql)) {
                            docStmt.setString(1, UUID.randomUUID().toString());
                            docStmt.setString(2, metadata.optString("title", filename));
                            docStmt.setString(3, metadata.getString("content"));
                            docStmt.setString(4, filePath.toString());
                            docStmt.setString(5, metadata.optString("author", "Unknown"));
                            // Use meta:creation-date if available, otherwise file's last modified
                            String createdDate = metadata.optString("created", 
                                new java.sql.Timestamp(file.lastModified()).toString());
                            docStmt.setTimestamp(6, java.sql.Timestamp.valueOf(createdDate));
                            docStmt.setString(7, metadata.optString("language", "Unknown"));
                            docStmt.setInt(8, metadata.optInt("length", content.length));
                            docStmt.setTimestamp(9, new java.sql.Timestamp(System.currentTimeMillis()));
                            docStmt.executeUpdate();
                        }
                        
                        log.info("New document processed: " + filename);
                    }
                } else {
                    log.fine("Document already processed: " + filename);
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error processing file: " + e.getMessage(), e);
        }
    }

    private void updateDatabases(String filename, long timestamp, EmbeddingDto embeddings) throws IOException {
        // TODO: Update both SQLite metadata and Qdrant vectors
        // 1. Update/insert metadata in SQLite (filename, timestamp)
        // 2. Update/insert vectors in Qdrant
    }

    private void deleteFromSqlite(String filename) {
        // TODO: Delete file metadata from SQLite
    }

    private void deleteFromQdrant(String filename) {
        // TODO: Delete vectors from Qdrant
    }

    // TIKA 
    private JSONObject getMetadataWithTika(final byte[] content) throws IOException, InterruptedException {
        String tikaServerAddress = "http://dev1.lan.elite-zettl.at:9998/rmeta";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikaServerAddress))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // Parse the JSON array response
            JSONObject[] metadataArray = objectMapper.readValue(response.body(), JSONObject[].class);
            
            // Create a combined metadata object
            JSONObject combinedMetadata = new JSONObject();
            
            // Process each metadata object in the array
            for (JSONObject metadata : metadataArray) {
                // Get content and clean XML tags if present
                if (metadata.has("X-TIKA:content")) {
                    String rawContent = metadata.getString("X-TIKA:content");
                    // Remove XML tags
                    String cleanContent = rawContent.replaceAll("<[^>]+>", "").trim();
                    combinedMetadata.put("content", cleanContent);
                }
                
                // Get language if present
                if (metadata.has("dc:language")) {
                    combinedMetadata.put("language", metadata.getString("dc:language"));
                }
                
                // Get content length if present
                if (metadata.has("Content-Length")) {
                    combinedMetadata.put("length", metadata.getInt("Content-Length"));
                }
                
                // Copy other useful metadata
                if (metadata.has("dc:title")) {
                    combinedMetadata.put("title", metadata.getString("dc:title"));
                }
                if (metadata.has("dc:creator")) {
                    combinedMetadata.put("author", metadata.getString("dc:creator"));
                }
                if (metadata.has("meta:creation-date")) {
                    combinedMetadata.put("created", metadata.getString("meta:creation-date"));
                }
            }
            
            log.fine("Extracted metadata: " + combinedMetadata.toString());
            return combinedMetadata;
        } else {
            throw new IOException("HTTP Error: " + response.statusCode());
        }
    }

    private EmbeddingDto getEmbeddings(List<String> chunks) throws IOException, InterruptedException {
        String embeddingServerUrl = "http://file1.lan.elite-zettl.at:11434/api/embed";
        List<float[]> allEmbeddings = new ArrayList<>();
        int chunk_index = 0;
        
        for (String chunk : chunks) {
            chunk_index++;
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "mxbai-embed-large");
            requestBody.put("input", chunk);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingServerUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<EmbeddingDto> response = httpClient.send(request, jsonBodyHandler());

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
        return _ -> HttpResponse.BodySubscribers.mapping(
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