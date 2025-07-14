package at.cms.api.dto;

public class PromptResponse {

    private String response;

    public PromptResponse(String id, String response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
} 