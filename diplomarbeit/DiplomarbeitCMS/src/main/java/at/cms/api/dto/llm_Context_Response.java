package at.cms.api.dto;

import java.util.HashMap;
import java.util.Map;

public class llm_Context_Response {

    private Map<String, String> IDs;
    private String document_Chunks;

    public llm_Context_Response() {
        this.IDs = new HashMap<>();
    }

    public llm_Context_Response(Map<String, String> IDs, String response) {
        this.IDs = IDs;
        this.document_Chunks = response;
    }

    public llm_Context_Response(String id, String response) {
        this.document_Chunks = response;
    }

    public Map<String, String> getIDs() {
        return IDs;
    }

    public void setIDs(Map<String, String> IDs) {
        this.IDs = IDs;
    }

    public String getDocument_Chunks() {
        return document_Chunks;
    }

    public void setDocument_Chunks(String document_Chunks) {
        this.document_Chunks = document_Chunks;
    }
} 