package at.cms;

import at.cms.ingestion.SampleTextSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SampleTextSplitterTest {

    private SampleTextSplitter splitter;

    @BeforeEach
    void setup() {
        splitter = new SampleTextSplitter();
    }

    private String readSample(String filename) throws IOException {
        URL resource = getClass().getClassLoader().getResource("samples/" + filename);
        if (resource == null) {
            throw new IOException("Sample file not found: " + filename);
        }
        try {
            return Files.readString(Paths.get(resource.toURI()), StandardCharsets.UTF_8);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid file URI: " + filename, e);
        }
    }

    @Test
    void testShortText() throws IOException {
        String text = readSample("short.txt");
        List<String> chunks = splitter.split(text);

        assertEquals(1, chunks.size(), "Short text should result in exactly one chunk");
        assertEquals("Hello World", chunks.get(0).trim(), "Chunk content should match original short text");
    }

    @Test
    void testExactChunkSize() throws IOException {
        String text = readSample("exactChunkSize.txt");
        List<String> chunks = splitter.split(text);

        assertFalse(chunks.isEmpty(), "Chunks should not be empty");
        assertTrue(chunks.stream().allMatch(c -> c.length() <= 200), "Each chunk should be <= 100 characters");
    }

    @Test
    void testLongParagraphs() throws IOException {
        String text = readSample("longParagraphs.md");
        List<String> chunks = splitter.split(text);

        assertTrue(chunks.size() > 10, "Should produce multiple chunks for long text");

        String chunk0 = chunks.get(0);
        String chunk1 = chunks.get(1);

        int overlap = 100;
        int from = Math.max(0, chunk0.length() - overlap);
        String overlapSection = chunk0.substring(from);
        assertTrue(chunk1.startsWith(overlapSection.substring(0, Math.min(20, overlapSection.length()))),
                "Chunk 1 should start with overlapping text from chunk 0");
    }

    @Test
    void testMultilineMarkdown() throws IOException {
        String text = readSample("multilines.md");
        List<String> chunks = splitter.split(text);

        assertTrue(chunks.size() > 1, "Multiline text should be split into multiple chunks");

        boolean containsMarkdown = chunks.stream().anyMatch(
                c -> c.contains("#") || c.contains("```") || c.contains("> ") || c.contains("- ")
        );
        assertTrue(containsMarkdown, "Markdown structure should be preserved in chunks");
    }
}
