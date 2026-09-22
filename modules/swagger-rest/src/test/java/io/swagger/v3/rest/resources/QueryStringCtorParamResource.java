package io.swagger.v3.rest.resources;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/ctorsearch")
public class QueryStringCtorParamResource {
    public QueryStringCtorParamResource(
            @QueryParam("cq")
            @Parameter(in = ParameterIn.QUERYSTRING, name = "cq",
                    content = @Content(schema = @Schema(implementation = String.class)))
            String ctorQuery) {
    }

    @GET
    @Produces("application/json")
    @Operation(operationId = "ctorSearch", summary = "Constructor scoped query string search")
    public Response search() {
        return Response.ok().build();
    }
}
