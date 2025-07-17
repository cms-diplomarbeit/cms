package at.cms.rag;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RagController {
    
    @Inject
    RagService ragService;

    @GET
    @Path("/ask")
    @Produces(MediaType.TEXT_PLAIN)
    public Response ask(@QueryParam("q") String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Query parameter 'q' is required")
                    .build();
            }
            
            String answer = ragService.ask(query);
            return Response.ok(answer).build();
        } catch (IOException | InterruptedException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error processing query: " + e.getMessage())
                .build();
        }
    }
}