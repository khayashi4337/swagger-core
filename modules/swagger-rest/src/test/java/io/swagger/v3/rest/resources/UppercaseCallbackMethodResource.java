package io.swagger.v3.rest.resources;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.callbacks.Callback;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/uppercase-callback")
public class UppercaseCallbackMethodResource {
    @GET
    @Produces("application/json")
    @Callback(
            callbackUrlExpression = "http://callback.url/events",
            name = "events",
            operation = {
                    @Operation(method = "POST", description = "uppercase method binding"),
                    @Operation(method = "get", description = "lowercase method binding")
            })
    @Operation(operationId = "getEvents", summary = "Callback target")
    public Response getEvents() {
        return Response.ok().build();
    }
}
