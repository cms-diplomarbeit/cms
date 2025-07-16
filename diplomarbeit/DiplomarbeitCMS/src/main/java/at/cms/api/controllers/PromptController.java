package at.cms.api.controllers;

import at.cms.api.dto.PromptRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import at.cms.api.QdrantService;
import java.util.List;
import at.cms.api.EmbeddingService;
import at.cms.training.dto.EmbeddingDto;
import org.springframework.http.HttpStatus;
import java.util.Collections;

@RestController
@RequestMapping("/api")
public class PromptController {
    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;

    public PromptController() {
        this.qdrantService = new QdrantService();
        this.embeddingService = new EmbeddingService();
    }

    @PostMapping("/prompt")
    public ResponseEntity<?> processPrompt(@RequestBody PromptRequest request) {
        try {
            if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Prompt cannot be empty");
            }

            // Convert prompt to vector
            List<String> promptChunks = Collections.singletonList(request.getPrompt());
            EmbeddingDto embeddings = embeddingService.getEmbeddings(promptChunks);
            
            // Search the chunks in Qdrant
            List<String> results = qdrantService.searchDocumentChunks(embeddings.getEmbeddings()[0]);
            
            if (results.isEmpty()) {
                return ResponseEntity.ok("No relevant documents found for the given prompt.");
            }
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing prompt: " + e.getMessage());
        }
    }
} 