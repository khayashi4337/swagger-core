package io.swagger.v3.rest.resources;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

// the "shared" op tag is declared on both operations; the matching top-level
// Tag entry is supplied by the scan configuration in the test
@Path("/cache")
public class PurgeTaggedResource {
    @GET
    @Path("/entries")
    @Produces("application/json")
    @Operation(operationId = "listCacheEntries", summary = "List cache entries", tags = "shared")
    public Response listEntries() {
        return Response.ok().build();
    }

    @PURGE
    @Path("/entries")
    @Produces("application/json")
    @Operation(operationId = "purgeCacheEntries", summary = "Purge cache entries", tags = "shared")
    public Response purgeEntries() {
        return Response.ok().build();
    }
}
