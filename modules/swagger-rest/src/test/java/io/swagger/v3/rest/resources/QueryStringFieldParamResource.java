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

@Path("/fieldsearch")
public class QueryStringFieldParamResource {
    @QueryParam("fq")
    @Parameter(in = ParameterIn.QUERYSTRING, name = "fq",
            content = @Content(schema = @Schema(implementation = String.class)))
    private String fieldQuery;

    @GET
    @Produces("application/json")
    @Operation(operationId = "fieldSearch", summary = "Field scoped query string search")
    public Response search() {
        return Response.ok().build();
    }
}
