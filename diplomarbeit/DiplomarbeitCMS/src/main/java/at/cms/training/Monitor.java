package at.cms.training;

import at.cms.api.EmbeddingService;
import at.cms.api.QdrantService;
import at.cms.api.TikaService;
import at.cms.ingestion.SampleTextSplitter;
import at.cms.training.db.Repository;
import at.cms.training.objects.FileInfo;
import at.cms.training.dto.EmbeddingDto;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class Monitor {
    private static final Logger log = Logger.getLogger(Monitor.class.getName());
    private final String watchDir;
    private final Map<String, FileInfo> trackedFiles;
    private final TikaService tikaService;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;

    public Monitor(String watchDir) {
        this.watchDir = watchDir;
        this.trackedFiles = new ConcurrentHashMap<>();
        this.tikaService = new TikaService("http://dev1.lan.elite-zettl.at:9998");
        this.embeddingService = new EmbeddingService("http://file1.lan.elite-zettl.at:11434");
        this.qdrantService = new QdrantService();

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                checkFiles();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in monitor check loop", e);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private final List<String> supportedExtensions = List.of(".docx", ".pdf", ".xlsx", ".pptx", ".txt", ".md", ".csv");

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

        trackedFiles.keySet().forEach(path -> {
            if (!currentFiles.contains(path)) {
                filesToDelete.add(path);
            }
        });

        if (filesToDelete.isEmpty()) return;

        log.info("Cleaning up " + filesToDelete.size() + " deleted files");

        try (Connection conn = Repository.getConnection()) {
            try {
                String deleteDocsSql = "DELETE FROM documents WHERE source IN (" +
                        String.join(",", Collections.nCopies(filesToDelete.size(), "?")) + ")";
                try (PreparedStatement stmt = conn.prepareStatement(deleteDocsSql)) {
                    for (int i = 0; i < filesToDelete.size(); i++) {
                        stmt.setString(i + 1, filesToDelete.get(i));
                    }
                    stmt.executeUpdate();
                }

                filesToDelete.forEach(path -> {
                    trackedFiles.remove(path);
                    qdrantService.deleteByPayloadValue("knowledge", "source", path);
                    log.info("Removed tracking and Qdrant vector for: " + path);
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
            docStmt.setBytes(3, extractedText.getBytes(StandardCharsets.UTF_8));
            docStmt.setString(4, filePath.toString());
            docStmt.setString(5, metadata.optString("author", "Unknown"));
            String createdDate = metadata.optString("created", new Timestamp(file.lastModified()).toString());
            docStmt.setTimestamp(6, Timestamp.valueOf(createdDate));
            docStmt.setString(7, metadata.optString("language", "Unknown"));
            docStmt.setInt(8, extractedText.length());
            docStmt.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
            docStmt.executeUpdate();
        }

        SampleTextSplitter splitter = new SampleTextSplitter();
        List<String> chunks = splitter.split(extractedText);

        String insertChunkSql = "INSERT INTO chunks (id, document_id, content, chunk_index) VALUES (?, ?, ?, ?)";
        try (PreparedStatement chunkStmt = conn.prepareStatement(insertChunkSql)) {
            for (int i = 0; i < chunks.size(); i++) {
                chunkStmt.setString(1, UUID.randomUUID().toString());
                chunkStmt.setString(2, documentId);
                chunkStmt.setString(3, chunks.get(i));
                chunkStmt.setInt(4, i);
                chunkStmt.executeUpdate();
            }
        }

        EmbeddingDto embeddings = embeddingService.getEmbeddings(chunks);
        float[][] vectors = embeddings.getEmbeddings();

        for (int i = 0; i < chunks.size(); i++) {
            qdrantService.upsertVector(
                    "knowledge",
                    UUID.randomUUID().toString(),
                    vectors[i],
                    Map.of(
                            "documentId", documentId,
                            "chunkIndex", i,
                            "text", chunks.get(i),
                            "source", filePath.toString()
                    )
            );
        }

        log.info("New document processed: " + filePath.getFileName());
    }

    private boolean documentExists(Connection conn, String title) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM documents WHERE title = ?")) {
            stmt.setString(1, title);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
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
            docStmt.setBytes(2, extractedText.getBytes(StandardCharsets.UTF_8));
            docStmt.setString(3, metadata.optString("author", "Unknown"));
            String createdDate = metadata.optString("created", new Timestamp(file.lastModified()).toString());
            docStmt.setTimestamp(4, Timestamp.valueOf(createdDate));
            docStmt.setString(5, metadata.optString("language", "Unknown"));
            docStmt.setInt(6, extractedText.length());
            docStmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            docStmt.setString(8, filePath.toString());
            docStmt.executeUpdate();
        }
    }
}
