package at.cms.training;

import at.cms.api.EmbeddingService;
import at.cms.api.QdrantService;
import at.cms.api.TikaService;
import at.cms.config.AppConfig;
import at.cms.ingestion.SampleTextSplitter;
import at.cms.training.db.DocumentRepository;
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
    private final QdrantService qdrantService;
    private final DocumentRepository documentRepository;

    public Monitor(String watchDir) {
        this.watchDir = watchDir;
        this.qdrantService = new QdrantService(AppConfig.getQdrantUrl());
        this.trackedFiles = new ConcurrentHashMap<>();
        this.tikaService = new TikaService(AppConfig.getTikaUrl());
        this.embeddingService = new EmbeddingService(AppConfig.getEmbeddingUrl());
        this.documentRepository = new DocumentRepository();

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                checkFiles();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in monitor check loop", e);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

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
                log.info("New file detected: " + filePath.getFileName());
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
        File file = filePath.toFile();
        String filename = filePath.getFileName().toString();
        JSONObject metadata = tikaService.getMetadataWithTika(content);
        String title = metadata.optString("title", filename);

        if (documentRepository.documentExists(title)) {
            log.info("Document '" + title + "' already exists.");
            return;
        }

        String documentId = UUID.randomUUID().toString();
        String extractedText = tikaService.extractContent(content);
        
        // Create and store chunks using extracted text        
        SampleTextSplitter splitter = new SampleTextSplitter();
        List<String> chunks = splitter.split(extractedText);
        List<String> chunkIds = new ArrayList<>();
        
        // Generate chunk IDs
        for (int i = 0; i < chunks.size(); i++) {
            chunkIds.add(UUID.randomUUID().toString());
        }

        // Insert document and chunks into database
        documentRepository.insertDocument(documentId, title, extractedText, filePath, metadata, file, chunks, chunkIds);

        log.info("Creating embeddings for all " + chunks.size() + " chunks of: " + filename);

        // Create embeddings for all chunks at once
        EmbeddingDto embeddings = embeddingService.getEmbeddings(chunks);

        // Update Qdrant with all vectors at once
        qdrantService.createCollectionAndInsertVectors(filename, embeddings, documentId, chunkIds);

        log.info("Document processing completed: " + filePath.getFileName());
    }

    private void updateFileMetadata(Path filePath, byte[] content) throws Exception {
        String extractedText = tikaService.extractContent(content);
        JSONObject metadata = tikaService.getMetadataWithTika(content);
        File file = filePath.toFile();
        String filename = filePath.getFileName().toString();
        
        // Update document in database
        documentRepository.updateDocument(filePath, extractedText, metadata, file);

        // Create and store new chunks
        SampleTextSplitter splitter = new SampleTextSplitter();
        List<String> chunks = splitter.split(extractedText);
        List<String> chunkIds = new ArrayList<>();
        
        // Generate new chunk IDs
        for (int i = 0; i < chunks.size(); i++) {
            chunkIds.add(UUID.randomUUID().toString());
        }

        log.info("Creating new embeddings for all " + chunks.size() + " chunks of: " + filename);

        // Create embeddings for all chunks at once
        EmbeddingDto embeddings = embeddingService.getEmbeddings(chunks);

        try {
            // Delete existing collection and create new one with updated vectors
            qdrantService.deleteCollection(filename);
            qdrantService.createCollectionAndInsertVectors(filename, embeddings, documentRepository.getDocumentIdBySource(filePath.toString()), chunkIds);
            log.info("Updated vector data for: " + filename);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to update vector data for: " + filename, e);
            throw e;
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

        try {
            documentRepository.deleteDocuments(filesToDelete);

            for (String path : filesToDelete) {
                try {
                    String filename = Paths.get(path).getFileName().toString();
                    qdrantService.deleteCollection(filename);
                    log.info("Deleted Qdrant collection for: " + filename);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Error deleting Qdrant collection for file: " + path, e);
                }
            }

            filesToDelete.forEach(path -> {
                trackedFiles.remove(path);
                log.info("Removed tracking for deleted file: " + path);
            });
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Database error during cleanup", e);
        }
    }
}