package at.cms.ingestion;

import at.cms.interfaces.Contracts;

import java.util.ArrayList;
import java.util.List;

public class SampleTextSplitter implements Contracts.TextSplitter {
    private final int chunkSize = 100;
    private final int overlap = 200;


    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        for(int start = 0; start < text.length(); start += (chunkSize - overlap)) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(text.substring(start, end));
        }
        return chunks;
    }
}
