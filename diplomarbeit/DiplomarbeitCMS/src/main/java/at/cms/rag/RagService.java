package at.cms.rag;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.List;

@ApplicationScoped
public class RagService {
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final OllamaService ollamaService;

    public RagService() {
        this.embeddingService = new EmbeddingService();
        this.qdrantService = new QdrantService();
        this.ollamaService = new OllamaService();
    }

    public String ask(String query) throws IOException, InterruptedException {
        // Generate embedding for the query
        float[] vector = embeddingService.embed(query);
        
        // Search for relevant document chunks
        List<String> results = qdrantService.search(vector);
        
        if (results.isEmpty()) {
            return "I couldn't find any relevant information to answer your question.";
        }
        
        // Build context from search results
        String context = String.join("\n---\n", results);
        
        // Generate answer using Ollama
        return ollamaService.generateAnswer(context, query);
    }
}

