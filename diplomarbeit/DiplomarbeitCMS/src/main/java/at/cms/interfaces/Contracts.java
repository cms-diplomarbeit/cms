package at.cms.interfaces;

import java.util.List;

public class Contracts {

    public interface TextSplitter {
        List<String> split(String text);
    }
}
