package io.swagger.v3.rest.resources;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/cache")
public class PurgeMethodResource {

    @PURGE
    @Path("/entries")
    @Produces("application/json")
    @Operation(operationId = "purgeCache", summary = "Purge cache entries")
    public Response purgeCache() {
        return Response.ok().build();
    }
}
