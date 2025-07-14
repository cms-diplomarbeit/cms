package at.cms.api.controllers;

import at.cms.api.dto.PromptRequest;
import at.cms.api.dto.llm_Context_Response;
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
        this.embeddingService = new EmbeddingService("http://file1.lan.elite-zettl.at:11434");
    }

    @PostMapping
    public ResponseEntity<llm_Context_Response> processPrompt(@RequestBody PromptRequest request) {
        try {
            // Convert to chunks
            List<String> promptChunks = List.of(request.getPrompt());

            // Convert prompt to vector
            EmbeddingDto embeddings = embeddingService.getEmbeddings(promptChunks);
            
            // Search the chunks in Qdrant
            List<String> results = qdrantService.search_Document_Chunks(embeddings.getEmbeddings()[0]);
            
            
            
            //Integer[] ids = qdrantService.search_Document_Chunks(request.getPrompt());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
} 