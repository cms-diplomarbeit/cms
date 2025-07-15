package at.cms.training.objects;

import java.io.File;
import java.util.UUID;

public class FileInfo {
    public final String id;
    public final String filePath;
    public final String fileName;
    public long createdAt;
    public long lastModified;

    public FileInfo(String filePath, File file) {
        // TODO: Check if the ID is correct 
        this.id = UUID.randomUUID().toString();
        this.filePath = filePath;
        this.fileName = file.getName();
        this.createdAt = file.lastModified();
        this.lastModified = file.lastModified();
    }

    public FileInfo(String id, String filePath, String fileName, long createdAt, long lastModified) {
        this.id = id;
        this.filePath = filePath;
        this.fileName = fileName;
        this.createdAt = createdAt;
        this.lastModified = lastModified;
    }

    public void updateLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
}
