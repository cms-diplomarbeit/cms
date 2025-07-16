package at.cms.processing;

import at.cms.api.EmbeddingService;
import at.cms.api.QdrantService;
import at.cms.config.AppConfig;
import at.cms.training.db.Repository;
import at.cms.training.dto.EmbeddingDto;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.sql.*;

/**
 * Vectorizer Service - Handles embedding generation and vector storage
 * Processes chunks from the database that haven't been vectorized yet
 */
public class Vectorizer {
    private static final Logger log = Logger.getLogger(Vectorizer.class.getName());
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;

    public Vectorizer() {
        this.embeddingService = new EmbeddingService(AppConfig.getOllamaUrl());
        this.qdrantService = new QdrantService(AppConfig.getQdrantUrl());

        log.info("Vectorizer started");
        log.info("Using Ollama server: " + AppConfig.getOllamaUrl());
        log.info("Using Qdrant server: " + AppConfig.getQdrantUrl());

        // Start vectorization processing
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                processUnvectorizedChunks();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in vectorizer loop", e);
            }
        }, 0, 2, TimeUnit.MINUTES);
    }

    private void processUnvectorizedChunks() {
        try (Connection conn = Repository.getConnection()) {
            List<ChunkInfo> unvectorizedChunks = getUnvectorizedChunks(conn);
            
            if (unvectorizedChunks.isEmpty()) {
                log.fine("No unvectorized chunks found");
                return;
            }

            log.info("Processing " + unvectorizedChunks.size() + " unvectorized chunks");

            // Group chunks by document for batch processing
            Map<String, List<ChunkInfo>> chunksByDocument = new java.util.HashMap<>();
            for (ChunkInfo chunk : unvectorizedChunks) {
                chunksByDocument.computeIfAbsent(chunk.documentId, comedy_gold -> new ArrayList<>()).add(chunk);
            }

            for (Map.Entry<String, List<ChunkInfo>> entry : chunksByDocument.entrySet()) {
                processDocumentChunks(conn, entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error processing unvectorized chunks", e);
        }
    }

    private List<ChunkInfo> getUnvectorizedChunks(Connection conn) throws SQLException {
        String sql = """
            SELECT c.id, c.document_id, c.content, c.chunk_index, d.source, d.title
            FROM chunks c
            JOIN documents d ON c.document_id = d.id
            WHERE c.vectorized = false
            ORDER BY d.last_updated DESC, c.chunk_index ASC
            LIMIT 50
        """;

        List<ChunkInfo> chunks = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                chunks.add(new ChunkInfo(
                    rs.getString("id"),
                    rs.getString("document_id"),
                    rs.getString("content"),
                    rs.getInt("chunk_index"),
                    rs.getString("source"),
                    rs.getString("title")
                ));
            }
        }
        return chunks;
    }

    private void processDocumentChunks(Connection conn, String documentId, List<ChunkInfo> chunks) {
        try {
            log.info("Vectorizing " + chunks.size() + " chunks for document: " + chunks.get(0).title);

            // Extract text content for embedding
            List<String> textChunks = chunks.stream()
                    .map(chunk -> chunk.content)
                    .toList();

            // Generate embeddings
            EmbeddingDto embeddings = embeddingService.getEmbeddings(textChunks);
            float[][] vectors = embeddings.getEmbeddings();

            conn.setAutoCommit(false);

            // Store vectors in Qdrant and mark chunks as vectorized
            for (int i = 0; i < chunks.size(); i++) {
                ChunkInfo chunk = chunks.get(i);
                
                // Store vector in Qdrant
                qdrantService.upsertVector(
                    "knowledge",
                    chunk.id,
                    vectors[i],
                    Map.of(
                        "documentId", chunk.documentId,
                        "chunkIndex", chunk.chunkIndex,
                        "text", chunk.content,
                        "source", chunk.source,
                        "title", chunk.title
                    )
                );

                // Mark chunk as vectorized in database
                String updateSql = "UPDATE chunks SET vectorized = true WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, chunk.id);
                    stmt.executeUpdate();
                }
            }

            conn.commit();
            log.info("Successfully vectorized " + chunks.size() + " chunks for: " + chunks.get(0).title);

        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                log.log(Level.SEVERE, "Failed to rollback transaction", rollbackEx);
            }
            log.log(Level.SEVERE, "Error vectorizing chunks for document: " + documentId, e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                log.log(Level.WARNING, "Failed to reset auto-commit", e);
            }
        }
    }

    private static class ChunkInfo {
        final String id;
        final String documentId;
        final String content;
        final int chunkIndex;
        final String source;
        final String title;

        ChunkInfo(String id, String documentId, String content, int chunkIndex, String source, String title) {
            this.id = id;
            this.documentId = documentId;
            this.content = content;
            this.chunkIndex = chunkIndex;
            this.source = source;
            this.title = title;
        }
    }
}
