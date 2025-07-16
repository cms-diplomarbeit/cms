package at.cms.training.db;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.logging.Logger;
import java.io.File;
import java.util.Collections;
import java.util.ArrayList;

public class DocumentRepository {
    private static final Logger log = Logger.getLogger(DocumentRepository.class.getName());

    public boolean documentExists(String title) throws SQLException {
        try (Connection conn = Repository.getConnection()) {
            String checkSql = "SELECT COUNT(*) FROM documents WHERE title = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, title);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    public void insertDocument(String documentId, String title, String content, Path filePath, 
                             JSONObject metadata, File file, List<String> chunks, List<String> chunkIds) throws SQLException {
        try (Connection conn = Repository.getConnection()) {
            try {
                // Insert document
                String insertDocSql = """
                    INSERT INTO documents (
                        id, title, content, source, author, created_at, 
                        language, word_count, last_updated
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement docStmt = conn.prepareStatement(insertDocSql)) {
                    docStmt.setString(1, documentId);
                    docStmt.setString(2, title);
                    docStmt.setBytes(3, content.getBytes(StandardCharsets.UTF_8));
                    docStmt.setString(4, filePath.toString());
                    docStmt.setString(5, metadata.optString("author", "Unknown"));
                    String createdDate = metadata.optString("created", 
                            new Timestamp(file.lastModified()).toString());
                    docStmt.setTimestamp(6, Timestamp.valueOf(createdDate));
                    docStmt.setString(7, metadata.optString("language", "Unknown"));
                    docStmt.setInt(8, content.length());
                    docStmt.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
                    docStmt.executeUpdate();
                }

                // Insert chunks
                if (chunks != null && chunkIds != null && chunks.size() == chunkIds.size()) {
                    String insertChunkSql = """
                        INSERT INTO chunks (id, document_id, content, chunk_index)
                        VALUES (?, ?, ?, ?)
                        """;

                    try (PreparedStatement chunkStmt = conn.prepareStatement(insertChunkSql)) {
                        for (int i = 0; i < chunks.size(); i++) {
                            chunkStmt.setString(1, chunkIds.get(i));
                            chunkStmt.setString(2, documentId);
                            chunkStmt.setString(3, chunks.get(i));
                            chunkStmt.setInt(4, i);
                            chunkStmt.addBatch();
                        }
                        chunkStmt.executeBatch();
                    }
                }

                conn.commit();
                log.info("Document and chunks inserted successfully: " + title);
            } catch (Exception e) {
                conn.rollback();
                throw new SQLException("Failed to insert document and chunks", e);
            }
        }
    }

    public void updateDocument(Path filePath, String content, JSONObject metadata, File file) throws SQLException {
        try (Connection conn = Repository.getConnection()) {
            try {
                String updateDocSql = """
                    UPDATE documents SET 
                        title = ?, content = ?, author = ?, created_at = ?,
                        language = ?, word_count = ?, last_updated = ?
                    WHERE source = ?
                    """;
                
                try (PreparedStatement docStmt = conn.prepareStatement(updateDocSql)) {
                    String filename = filePath.getFileName().toString();
                    docStmt.setString(1, metadata.optString("title", filename));
                    docStmt.setBytes(2, content.getBytes(StandardCharsets.UTF_8));
                    docStmt.setString(3, metadata.optString("author", "Unknown"));
                    String createdDate = metadata.optString("created",
                            new Timestamp(file.lastModified()).toString());
                    docStmt.setTimestamp(4, Timestamp.valueOf(createdDate));
                    docStmt.setString(5, metadata.optString("language", "Unknown"));
                    docStmt.setInt(6, content.length());
                    docStmt.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                    docStmt.setString(8, filePath.toString());
                    docStmt.executeUpdate();
                }

                conn.commit();
                log.info("Document updated successfully: " + filePath.getFileName());
            } catch (Exception e) {
                conn.rollback();
                throw new SQLException("Failed to update document", e);
            }
        }
    }

    public void deleteDocuments(List<String> filePaths) throws SQLException {
        if (filePaths.isEmpty()) {
            return;
        }

        try (Connection conn = Repository.getConnection()) {
            try {
                // First get the document IDs and collection names (filenames) before deletion
                List<String> documentIds = new ArrayList<>();
                List<String> collectionNames = new ArrayList<>();
                
                String selectSql = "SELECT id, source FROM documents WHERE source IN (" +
                        String.join(",", Collections.nCopies(filePaths.size(), "?")) + ")";
                
                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    for (int i = 0; i < filePaths.size(); i++) {
                        selectStmt.setString(i + 1, filePaths.get(i));
                    }
                    
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        while (rs.next()) {
                            documentIds.add(rs.getString("id"));
                            // Extract filename from source path to use as collection name
                            String source = rs.getString("source");
                            String collectionName = new File(source).getName();
                            collectionNames.add(collectionName);
                        }
                    }
                }

                // Delete from documents (chunks will be deleted via CASCADE)
                String deleteDocsSql = "DELETE FROM documents WHERE source IN (" +
                        String.join(",", Collections.nCopies(filePaths.size(), "?")) + ")";
                
                try (PreparedStatement stmt = conn.prepareStatement(deleteDocsSql)) {
                    for (int i = 0; i < filePaths.size(); i++) {
                        stmt.setString(i + 1, filePaths.get(i));
                    }
                    stmt.executeUpdate();
                }

                conn.commit();
                log.info("Documents deleted successfully: " + filePaths.size() + " documents");
            } catch (Exception e) {
                conn.rollback();
                throw new SQLException("Failed to delete documents", e);
            }
        }
    }

    public String getDocumentIdBySource(String sourcePath) throws SQLException {
        try (Connection conn = Repository.getConnection()) {
            String sql = "SELECT id FROM documents WHERE source = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sourcePath);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("id");
                    }
                    throw new SQLException("No document found for source: " + sourcePath);
                }
            }
        }
    }
} 