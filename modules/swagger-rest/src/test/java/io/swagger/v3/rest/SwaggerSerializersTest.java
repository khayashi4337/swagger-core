package io.swagger.v3.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.QueryStringParameter;
import jakarta.ws.rs.core.MediaType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import tools.jackson.databind.DatabindException;

import java.io.ByteArrayOutputStream;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Verifies {@link SwaggerSerializers} picks the output mapper matching the
 * document's own spec version: a 3.2 document must be written through the 3.2
 * mappers, while 3.0/3.1 output is unchanged.
 */
public class SwaggerSerializersTest {

    @AfterMethod
    public void resetStatics() {
        SwaggerSerializers.setOpenapi31(false);
        SwaggerSerializers.setPrettyPrint(false);
    }

    private static OpenAPI docWithQueryStringParameter(String version) {
        PathItem pathItem = new PathItem().get(new Operation()
                .addParametersItem(new QueryStringParameter().name("q")));
        return new OpenAPI()
                .openapi(version)
                .info(new Info().title("t").version("1"))
                .paths(new Paths().addPathItem("/search", pathItem));
    }

    private static String writeJson(SwaggerSerializers serializers, OpenAPI doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializers.writeTo(doc, OpenAPI.class, null, null,
                MediaType.APPLICATION_JSON_TYPE, new jakarta.ws.rs.core.MultivaluedHashMap<>(), out);
        return out.toString("UTF-8");
    }

    @Test(description = "a 3.2 document serializes in: querystring through the 3.2 mapper")
    public void testWriteToUses32MapperFor32Document() throws Exception {
        // writing a 3.2 document through the 3.1 mapper used to throw
        // DatabindException when the document carried in: querystring
        SwaggerSerializers serializers = new SwaggerSerializers();
        String json = writeJson(serializers, docWithQueryStringParameter("3.2.0"));
        assertTrue(json.contains("\"in\":\"querystring\""),
                "must emit in: querystring via the 3.2 mapper: " + json);
    }

    @Test(description = "a 3.2 document keeps serializing in: querystring with prettyPrint and openapi31 flags")
    public void testWriteTo32DocumentWithFlags() throws Exception {
        SwaggerSerializers.setPrettyPrint(true);
        // even with the legacy 3.1 flag set, a 3.2 document must go through the
        // 3.2 mapper: the document's own version wins
        SwaggerSerializers.setOpenapi31(true);
        String json = writeJson(new SwaggerSerializers(), docWithQueryStringParameter("3.2.0"));
        assertTrue(json.contains("querystring"),
                "must emit in: querystring via the 3.2 pretty mapper: " + json);
    }

    @Test(description = "pre-3.2 output keeps rejecting in: querystring as before")
    public void testWriteToKeeps30And31Rejection() {
        SwaggerSerializers.setOpenapi31(false);
        DatabindException e30 = expectThrows(DatabindException.class,
                () -> writeJson(new SwaggerSerializers(), docWithQueryStringParameter("3.0.3")));
        assertTrue(e30.getMessage() != null && e30.getMessage().contains("querystring"),
                "3.0 writer must reject in: querystring: " + e30.getMessage());

        SwaggerSerializers.setOpenapi31(true);
        DatabindException e31 = expectThrows(DatabindException.class,
                () -> writeJson(new SwaggerSerializers(), docWithQueryStringParameter("3.1.0")));
        assertTrue(e31.getMessage() != null && e31.getMessage().contains("querystring"),
                "3.1 writer must reject in: querystring: " + e31.getMessage());
    }
}
