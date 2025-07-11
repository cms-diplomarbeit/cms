package at.cms.training;

import at.cms.api.EmbeddingService;
import at.cms.api.QdrantService;
import at.cms.api.TikaService;
import at.cms.ingestion.SampleTextSplitter;
import at.cms.training.db.Repository;
import at.cms.training.objects.FileInfo;
// Filewathcher, Tika & Embedding
import at.cms.training.dto.EmbeddingDto;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.json.JSONObject;
import java.io.File;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.*;
import java.util.Set;
import java.util.Collections;
import java.nio.charset.StandardCharsets;

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
    private final Map<String, FileInfo> trackedFiles;
    private final TikaService tikaService;
    private final EmbeddingService embeddingService;

    // Observer
    public Monitor(String watchDir) {
        this.watchDir = watchDir;
        this.trackedFiles = new ConcurrentHashMap<>();
        this.tikaService = new TikaService("http://dev1.lan.elite-zettl.at:9998");
        this.embeddingService = new EmbeddingService("http://file1.lan.elite-zettl.at:11434");



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
            Set<String> currentFiles = new java.util.HashSet<>();
            
            Files.list(Paths.get(watchDir))
                .filter(path -> supportedExtensions.stream()
                    .anyMatch(ext -> path.toString().toLowerCase().endsWith(ext)))
                .forEach(path -> {
                    currentFiles.add(path.toString());
                    processFile(path);
                });
                
            cleanupDeletedFiles(currentFiles);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Error checking directory: " + e.getMessage(), e);
        }
    }

    private final List<String> supportedExtensions = List.of(".docx", ".pdf", ".xlsx", ".pptx", ".txt", ".md", ".csv");

    private void processFile(Path filePath) {
        try {
            File file = filePath.toFile();
            String pathStr = filePath.toString();
            
            FileInfo existingFile = trackedFiles.get(pathStr);
            if (existingFile != null) {
                if (file.lastModified() > existingFile.lastModified) {
                    byte[] content = Files.readAllBytes(filePath);
                    updateFileMetadata(filePath, content);
                    existingFile.updateLastModified(file.lastModified());
                }
            } else {
                byte[] byteFile = Files.readAllBytes(filePath);
                FileInfo newFile = new FileInfo(pathStr, file);
                trackedFiles.put(pathStr, newFile);
                processNewFile(filePath, byteFile);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error processing file: " + filePath, e);
        }
    }

    private void processNewFile(Path filePath, byte[] content) throws Exception {
        try (Connection conn = Repository.getConnection()) {
            try {
                insertDocumentMetadata(conn, filePath, content);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void updateFileMetadata(Path filePath, byte[] content) throws Exception {
        try (Connection conn = Repository.getConnection()) {
            try {
                // Update document metadata
                updateDocumentMetadata(conn, filePath, content);
                conn.commit();
                log.info("Document updated: " + filePath.getFileName());
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void cleanupDeletedFiles(Set<String> currentFiles) {
        List<String> filesToDelete = new ArrayList<>();
        
        // Check which tracked files no longer exist in currentFiles
        trackedFiles.keySet().forEach(path -> {
            if (!currentFiles.contains(path)) {
                filesToDelete.add(path);
            }
        });

        if (filesToDelete.isEmpty()) {
            return;
        }

        log.info("Cleaning up " + filesToDelete.size() + " deleted files");

        try (Connection conn = Repository.getConnection()) {
            try {
                // Delete from documents table
                String deleteDocsSql = "DELETE FROM documents WHERE source IN (" +
                                     String.join(",", Collections.nCopies(filesToDelete.size(), "?")) + ")";
                try (PreparedStatement stmt = conn.prepareStatement(deleteDocsSql)) {
                    for (int i = 0; i < filesToDelete.size(); i++) {
                        stmt.setString(i + 1, filesToDelete.get(i));
                    }
                    stmt.executeUpdate();
                }

                // Remove from tracking
                filesToDelete.forEach(path -> {
                    trackedFiles.remove(path);
                    log.info("Removed tracking for deleted file: " + path);
                });

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                log.log(Level.SEVERE, "Error cleaning up deleted files", e);
            }
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Database error during cleanup", e);
        }
    }

    private void insertDocumentMetadata(Connection conn, Path filePath, byte[] byteFile) throws Exception {
        File file = filePath.toFile();
        String filename = filePath.getFileName().toString();
        JSONObject metadata = tikaService.getMetadataWithTika(byteFile);
        String title = metadata.optString("title", filename);

        if (documentExists(conn, title)) {
            log.info("Document '" + title + "' already exists.");
            return;
        }

        String documentId = UUID.randomUUID().toString();
        String extractedText = tikaService.extractContent(byteFile);
        
        String insertDocSql = """
            INSERT INTO documents (
                id, title, content, source, author, created_at, 
                language, word_count, last_updated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement docStmt = conn.prepareStatement(insertDocSql)) {
            docStmt.setString(1, documentId);
            docStmt.setString(2, title);
            docStmt.setBytes(3, extractedText.getBytes(StandardCharsets.UTF_8));  // Store extracted text as BLOB
            docStmt.setString(4, filePath.toString());
            docStmt.setString(5, metadata.optString("author", "Unknown"));
            String createdDate = metadata.optString("created", new java.sql.Timestamp(file.lastModified()).toString());
            docStmt.setTimestamp(6, java.sql.Timestamp.valueOf(createdDate));
            docStmt.setString(7, metadata.optString("language", "Unknown"));
            docStmt.setInt(8, extractedText.length());
            docStmt.setTimestamp(9, new java.sql.Timestamp(System.currentTimeMillis()));
            docStmt.executeUpdate();
        }

        // Create and store chunks using extracted text
        SampleTextSplitter splitter = new SampleTextSplitter();
        List<String> chunks = splitter.split(extractedText);
        
        String insertChunkSql = """
            INSERT INTO chunks (id, document_id, content, chunk_index)
            VALUES (?, ?, ?, ?)
        """;
        
        try (PreparedStatement chunkStmt = conn.prepareStatement(insertChunkSql)) {
            for (int i = 0; i < chunks.size(); i++) {
                chunkStmt.setString(1, UUID.randomUUID().toString());
                chunkStmt.setString(2, documentId);
                chunkStmt.setString(3, chunks.get(i));
                chunkStmt.setInt(4, i);
                chunkStmt.executeUpdate();
            }
        }

        // Create embeddings for the chunks
        EmbeddingDto embeddings = embeddingService.getEmbeddings(chunks);

        // TODO: Update qdrant Storage with vectorised chunks

        
        log.info("New document processed: " + filePath.getFileName());
    }

    private boolean documentExists(Connection conn, String title) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM documents WHERE title = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setString(1, title);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private void updateDocumentMetadata(Connection conn, Path filePath, byte[] content) throws Exception {
        File file = filePath.toFile();
        String filename = filePath.getFileName().toString();
        JSONObject metadata = tikaService.getMetadataWithTika(content);
        String extractedText = tikaService.extractContent(content);
        
        String updateDocSql = """
            UPDATE documents SET 
                title = ?, content = ?, author = ?, created_at = ?,
                language = ?, word_count = ?, last_updated = ?
            WHERE source = ?
        """;
        
        try (PreparedStatement docStmt = conn.prepareStatement(updateDocSql)) {
            docStmt.setString(1, metadata.optString("title", filename));
            docStmt.setBytes(2, extractedText.getBytes(StandardCharsets.UTF_8)); // Store extracted text as BLOB
            docStmt.setString(3, metadata.optString("author", "Unknown"));
            String createdDate = metadata.optString("created", 
                new java.sql.Timestamp(file.lastModified()).toString());
            docStmt.setTimestamp(4, java.sql.Timestamp.valueOf(createdDate));
            docStmt.setString(5, metadata.optString("language", "Unknown"));
            docStmt.setInt(6, extractedText.length());
            docStmt.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
            docStmt.setString(8, filePath.toString());
            docStmt.executeUpdate();
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
}