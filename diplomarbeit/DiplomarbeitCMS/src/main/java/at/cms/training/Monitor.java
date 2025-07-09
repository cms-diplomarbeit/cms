package at.cms.training;

import at.cms.ingestion.SampleTextSplitter;
// Filewathcher, Tika & Embedding
import at.cms.training.dto.EmbeddingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Date;
import java.util.ArrayList;

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

    // Überwachung
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

    // Überwachung
    private void checkAndProcessFiles(Path filePath) {
        try {
            String filename = filePath.getFileName().toString();

            File file = filePath.toFile();
            long ts = file.lastModified();
            Date date = new Date(ts);
            
            byte[] content = Files.readAllBytes(filePath);
            String text = callTikaWithRestClient(content);
            List<String> chunks = new SampleTextSplitter().split(text);
            EmbeddingDto embeddings = getEmbeddings(chunks);
            System.out.println(embeddings);
            log.info("--- File processed: " + filename + " - Text has been extracted and embedded");
            } catch (Exception e) {
            log.log(Level.SEVERE, "Error processing " + filePath + ": " + e.getMessage(), e);
        }
    }

    // TIKA 
    private String callTikaWithRestClient(final byte[] content) throws IOException, InterruptedException {
        String tikaServerAddress = "http://dev1.lan.elite-zettl.at:9998/tika";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikaServerAddress))
                .header("Content-Type", "application/octet-stream")
                .header("Accept", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            throw new IOException("HTTP Error: " + response.statusCode());
        }
    }

    // Vektorisierung
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
}