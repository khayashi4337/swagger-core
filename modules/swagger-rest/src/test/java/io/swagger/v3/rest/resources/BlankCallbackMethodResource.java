package io.swagger.v3.rest.resources;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.callbacks.Callback;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/blank-callback")
public class BlankCallbackMethodResource {
    @GET
    @Produces("application/json")
    @Callback(
            callbackUrlExpression = "http://callback.url/events",
            name = "events",
            operation = {
                    @Operation(method = "", description = "no method declared"),
                    @Operation(description = "implicit empty method"),
                    @Operation(method = "post", description = "post callback")
            })
    @Operation(operationId = "blankCb", summary = "Callback target")
    public Response getEvents() {
        return Response.ok().build();
    }
}
