package at.cms.api.controllers;

import at.cms.api.dto.PromptRequest;
import at.cms.config.AppConfig;
import at.cms.api.QdrantService;
import at.cms.api.EmbeddingService;
import at.cms.training.dto.EmbeddingDto;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Collections;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PromptController {
    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;

    public PromptController() {
        this.qdrantService = new QdrantService(AppConfig.getQdrantUrl());
        this.embeddingService = new EmbeddingService(AppConfig.getOllamaUrl());
    }

    @POST
    @Path("/prompt")
    public Response processPrompt(PromptRequest request) {
        try {
            if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Prompt cannot be empty")
                    .build();
            }

            // Convert prompt to vector
            List<String> promptChunks = Collections.singletonList(request.getPrompt());
            EmbeddingDto embeddings = embeddingService.getEmbeddings(promptChunks);
            
            // Search the chunks in Qdrant
            List<String> results = qdrantService.searchDocumentChunks(embeddings.getEmbeddings()[0]);
            
            if (results.isEmpty()) {
                return Response.ok("No relevant documents found for the given prompt.").build();
            }
            
            return Response.ok(results).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error processing prompt: " + e.getMessage())
                .build();
        }
    }
} 