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

public class Monitor {
    private final String watchDir;
    private final String outputDir;
    private final ExecutorService executor;
    private WatchService watcher;
    private boolean isRunning = false;
    private final HttpClient httpClient;

    public Monitor(String watchDir) {
        this.watchDir = watchDir;
        this.outputDir = watchDir + "/extracted_text";
        this.executor = Executors.newFixedThreadPool(4);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

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

            // Save text to file
            saveTextToFile(filePath.getFileName().toString(), text);

            System.out.println("--- File processed: " + filePath.getFileName() + " ---");
            System.out.println("Text has been extracted and saved.");
            System.out.println("------------------------------");
        } catch (Exception e) {
            System.err.println("Error extracting from " + filePath + ": " + e.getMessage());
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

    private void saveTextToFile(String originalFilename, String text) throws IOException {
        // Create unique filename with timestamp
        String baseName = originalFilename.replaceFirst("\\.(pdf|xlsx|pptx)$", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String txtFilename = baseName + "_" + timestamp + ".txt";

        Path outputPath = Paths.get(outputDir, txtFilename);
        Files.writeString(outputPath, text);
    }
}