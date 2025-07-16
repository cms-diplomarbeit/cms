package at.cms.training.dto;

import java.util.List;

public class EmbeddingDto {
    private float[][] embeddings;
    private int chunk_index;
    private List<String> texts;  // Store the original text chunks
    
    public float[][] getEmbeddings() { return embeddings; }
    public void setEmbeddings(float[][] embeddings) { this.embeddings = embeddings; }

    public int getChunk_index() { return chunk_index; }
    public void setChunk_index(int chunk_index) { this.chunk_index = chunk_index; }
    
    public List<String> getTexts() { return texts; }
    public void setTexts(List<String> texts) { this.texts = texts; }
} 