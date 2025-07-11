package at.cms.training.dto;

public class EmbeddingDto {
    private String model;
    private float[][] embeddings;
    private long total_duration;
    private long load_duration;
    private int prompt_eval_count;
    private int chunk_index;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public float[][] getEmbeddings() { return embeddings; }
    public void setEmbeddings(float[][] embeddings) { this.embeddings = embeddings; }

    public long getTotal_duration() { return total_duration; }
    public void setTotal_duration(long duration) { this.total_duration = duration; }

    public long getLoad_duration() { return load_duration; }
    public void setLoad_duration(long loadDuration) { this.load_duration = loadDuration; }

    public int getPrompt_eval_count() { return prompt_eval_count; }
    public void setPrompt_eval_count(int promptEvalCount) { this.prompt_eval_count = promptEvalCount; }

    public int getChunk_index() { return chunk_index; }
    public void setChunk_index(int chunk_index) { this.chunk_index = chunk_index; }
} 