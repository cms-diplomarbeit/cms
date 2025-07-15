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

@RestController
@RequestMapping("/api/prompt")
public class PromptController {
    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;

    public PromptController() {
        this.qdrantService = new QdrantService();
        this.embeddingService = new EmbeddingService();
    }

    @PostMapping
    public ResponseEntity<List<String>> processPrompt(@RequestBody PromptRequest request) {
        try {
            // Convert prompt into chunks and then to vector
            List<String> promptChunks = List.of();
            EmbeddingDto embeddings = embeddingService.getEmbeddings(promptChunks);
            
            // Search the chunks in Qdrant
            List<String> results = qdrantService.searchDocumentChunks(embeddings.getEmbeddings()[0]);
            
            return ResponseEntity.ok(results);
            
            //Integer[] ids = qdrantService.search_Document_Chunks(request.getPrompt());
        } catch (Exception e) {
            return null;
        }
    }
} 