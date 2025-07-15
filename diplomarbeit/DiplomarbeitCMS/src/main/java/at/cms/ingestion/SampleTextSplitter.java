package at.cms.ingestion;

import at.cms.interfaces.Contracts;
import java.util.ArrayList;
import java.util.List;

public class SampleTextSplitter implements Contracts.TextSplitter {
    @Override
    public List<String> split(String text) {
        text = text.replaceAll("\\s+", " ").trim();

        List<String> chunks = new ArrayList<>();
        int chunkSize = 512;
        int overlap = 50;
        for (int start = 0; start < text.length(); start += (chunkSize - overlap)) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }
}
