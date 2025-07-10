package at.cms.rag;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.List;

@Service
public class RagService {
    private final EmbeddingService embedder = new EmbeddingService();
    private final QdrantService qdrant = new QdrantService();
    private final OllamaService ollama = new OllamaService();

    public String ask(String query) throws IOException, InterruptedException {
        float[] vector = embedder.embed(query);
        List<String> results = qdrant.search(vector);
        String context = String.join("\n---\n", results);
        return ollama.generateAnswer(context, query);
    }
}

