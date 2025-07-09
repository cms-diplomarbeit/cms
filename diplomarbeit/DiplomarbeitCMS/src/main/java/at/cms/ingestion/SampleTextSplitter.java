package at.cms.ingestion;

import at.cms.interfaces.Contracts;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class SampleTextSplitter implements Contracts.TextSplitter {
    private final int chunkSize = 200;
    private final int overlap = 100;

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        for(int start = 0; start < text.length(); start += (chunkSize - overlap)) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }

    public List<String> chunkText(String text, int maxWords, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i += maxWords - overlap) {
            int end = Math.min(i + maxWords, words.length);
            String chunk = String.join(" ", Arrays.copyOfRange(words, i, end));
            chunks.add(chunk);
        }
        return chunks;
    }
    
}
