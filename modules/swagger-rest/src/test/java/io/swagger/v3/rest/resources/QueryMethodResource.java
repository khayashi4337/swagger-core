package io.swagger.v3.rest.resources;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/pets")
public class QueryMethodResource {

    @QUERY
    @Path("/search")
    @Produces("application/json")
    @Operation(operationId = "searchPets", summary = "Search pets")
    public Response searchPets() {
        return Response.ok().build();
    }
}
