package at.cms.ingestion;

import at.cms.interfaces.Contracts;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SampleTextSplitter implements Contracts.TextSplitter {
    // Increased chunk size for better performance with modern embedding models
    private final int chunkSize = 512;
    private final int overlap = 50;
    
    // Pattern for finding sentence boundaries
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.!?]\\s+");
    
    // Pattern for normalizing whitespace
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public List<String> split(String text) {
        // Normalize whitespace only
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        
        List<String> chunks = new ArrayList<>();
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            // Calculate the potential end position
            int end = Math.min(text.length(), start + chunkSize);
            
            // If we're not at the end of text, try to find a sentence boundary
            if (end < text.length()) {
                // Look for sentence boundary within the last 100 characters of the chunk
                int searchStart = Math.max(start + chunkSize - 100, start);
                int searchEnd = Math.min(end + 100, text.length());
                String searchText = text.substring(searchStart, searchEnd);
                
                // Find the last sentence boundary in our search window
                var matcher = SENTENCE_BOUNDARY.matcher(searchText);
                int lastBoundary = -1;
                while (matcher.find()) {
                    lastBoundary = matcher.end();
                }
                
                if (lastBoundary != -1) {
                    // Adjust end to the sentence boundary
                    end = searchStart + lastBoundary;
                }
            }
            
            // Extract the chunk
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            // Move start position, accounting for overlap
            start = end - overlap;
        }
        
        return chunks;
    }
}
