package at.cms.training;

import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;
import java.nio.file.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.Vector;
import java.util.HashMap;
import java.util.Map;

public class Monitor {
    private final String watchDir;
    private final String outputDir;
    private final ExecutorService executor;
    private WatchService watcher;
    private boolean isRunning = false;
    private final HttpClient httpClient;
    private final Gson gson;

    public Monitor(String watchDir) {
        this.watchDir = watchDir;
        this.outputDir = watchDir + "/extracted_text";
        this.executor = Executors.newFixedThreadPool(4);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        this.gson = new Gson();
        
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            System.err.println("Error creating output directory: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        Path dir = Paths.get(watchDir);
        dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        isRunning = true;
        executor.submit(this::processEvents);
    }

    public void stop() throws IOException {
        isRunning = false;
        if (watcher != null) watcher.close();
        executor.shutdown();
    }

    private final List<String> supportedExtensions = List.of(".pdf", ".xlsx", ".pptx");

    private void processEvents() {
        while (isRunning) {
            try {
                WatchKey key = watcher.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path filename = (Path) event.context();
                    String lowercaseName = filename.toString().toLowerCase();

                    if (supportedExtensions.stream().anyMatch(lowercaseName::endsWith)) {
                        Path filePath = Paths.get(watchDir, filename.toString());
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            extractAndSaveText(filePath);
                        }
                    }
                }
                key.reset();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void extractAndSaveText(Path filePath) {
        try {
            byte[] content = Files.readAllBytes(filePath);
            String text = callTikaWithRestClient(content);

            String filename = filePath.getFileName().toString();

            getEmbeddings(text);

            System.out.println("--- File processed: " + filename + " ---");
            System.out.println("Text has been extracted, embedded, and stored in Qdrant.");
            System.out.println("------------------------------");
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

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

    private float[] getEmbeddings(String text) throws IOException, InterruptedException {
        String embeddingServerUrl = "http://file1.lan.elite-zettl.at:11434/api/embed";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "mxbai-embed-large");
        requestBody.addProperty("input", text);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingServerUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            // Die Embedding-API gibt die Vektoren direkt im "embedding" Feld zurück
            return gson.fromJson(jsonResponse.getAsJsonObject("embedding"), float[].class);
        } else {
            throw new IOException("Embedding API Error: " + response.statusCode() + " - " + response.body());
        }
    }
}