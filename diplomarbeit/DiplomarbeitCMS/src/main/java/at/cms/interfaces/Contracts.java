package at.cms.interfaces;

import javax.naming.directory.SearchResult;
import java.util.List;
import java.util.Map;

public class Contracts {

    public interface TextSplitter {
        List<String> split(String text);
    }
    public interface IEmbeddingService {
        float[] embed(String text);
    }
    public interface IVectorStore {
        void upsert(String id, float[] vector, Map<String, String> metaData);
        List<SearchResult> query(float[] vector, int topK);
    }
}
