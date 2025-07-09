package at.cms.training.dto;

import java.util.Date;
import java.util.List;

public class DocumentMetadata {
    private String id;
    private String title;
    private String content;
    private String source;
    private String author;
    private Date createdAt;
    private String category;
    private List<String> tags;
    private String language;
    private Integer wordCount;
    private String embeddingModel;
    private Date lastUpdated;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }
} 