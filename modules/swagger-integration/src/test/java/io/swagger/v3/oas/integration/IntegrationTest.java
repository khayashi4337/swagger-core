package io.swagger.v3.oas.integration;

import io.swagger.v3.oas.integration.api.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.QueryStringParameter;
import org.testng.annotations.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class IntegrationTest {

    @Test(description = "initialize a context and read")
    public void shouldInitialize() throws Exception {

        OpenAPIConfiguration config = new SwaggerConfiguration()
                .resourcePackages(Stream.of("com.my.project.resources", "org.my.project.resources").collect(Collectors.toSet()))
                .openAPI(new OpenAPI().info(new Info().description("TEST INFO DESC")));

        OpenApiContext ctx = new GenericOpenApiContext()
                .openApiConfiguration(config)
                .init();
        OpenAPI openApi = ctx.read();

        assertNotNull(openApi);
    }

    @Test(description = "processJsonObjectMapper default returns same mapper")
    public void testProcessJsonObjectMapperDefaultReturnsSameMapper() {
        ObjectMapperProcessor processor = new ObjectMapperProcessor() {
        };
        ObjectMapper input = new ObjectMapper();
        ObjectMapper result = processor.processJsonObjectMapper(input);
        assertSame(result, input);
    }

    @Test(description = "processJsonObjectMapper custom impl can rebuild mapper with different config")
    public void testProcessJsonObjectMapperCustomImplReturnValueHonored() {
        ObjectMapperProcessor processor = new ObjectMapperProcessor() {
            @Override
            public ObjectMapper processJsonObjectMapper(ObjectMapper mapper) {
                return mapper.rebuild()
                        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                        .build();
            }
        };
        ObjectMapper input = new ObjectMapper();
        ObjectMapper result = processor.processJsonObjectMapper(input);
        assertNotSame(result, input, "rebuild produces new instance");
        assertTrue(result.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY),
                "returned mapper must carry the custom config");
    }

    private static OpenAPI docWithQueryStringParameter() {
        PathItem pathItem = new PathItem().get(new Operation()
                .addParametersItem(new QueryStringParameter().name("q")));
        return new OpenAPI()
                .openapi("3.2.0")
                .info(new Info().title("t").version("1"))
                .paths(new Paths().addPathItem("/search", pathItem));
    }

    @Test(description = "a 3.2 context selects the 3.2 output mappers and serves in: querystring")
    public void testOutputMappersFollowSpecVersion32() throws Exception {
        // serving a 3.2 document through the 3.1 mapper used to throw
        // DatabindException when the document carried in: querystring
        OpenAPIConfiguration config32 = new SwaggerConfiguration()
                .openAPI31(true)
                .openAPIVersion("3.2.0")
                .openAPI(new OpenAPI().info(new Info().title("t").version("1")));
        OpenApiContext ctx = new GenericOpenApiContext()
                .openApiConfiguration(config32)
                .init();

        String json = ctx.getOutputJsonMapper().writeValueAsString(docWithQueryStringParameter());
        assertTrue(json.contains("\"in\":\"querystring\""),
                "3.2 context must emit in: querystring via the 3.2 JSON mapper: " + json);
        String yaml = ctx.getOutputYamlMapper().writeValueAsString(docWithQueryStringParameter());
        assertTrue(yaml.contains("in: querystring") || yaml.contains("in: \"querystring\""),
                "3.2 context must emit in: querystring via the 3.2 YAML mapper: " + yaml);
    }

    @Test(description = "3.0 and 3.1 contexts keep selecting the 3.0/3.1 output mappers")
    public void testOutputMappersKeep30And31Selection() {
        OpenAPI doc = docWithQueryStringParameter();
        for (OpenAPIConfiguration config : new OpenAPIConfiguration[]{
                new SwaggerConfiguration().openAPI(new OpenAPI().info(new Info().title("t").version("1"))),
                new SwaggerConfiguration().openAPI31(true)
                        .openAPI(new OpenAPI().info(new Info().title("t").version("1")))}) {
            try {
                OpenApiContext ctx = new GenericOpenApiContext()
                        .openApiConfiguration(config)
                        .init();
                // the 3.0/3.1 output mappers must keep rejecting the 3.2-only
                // in: querystring value, proving they were selected as before
                DatabindException e = expectThrows(DatabindException.class,
                        () -> ctx.getOutputJsonMapper().writeValueAsString(doc));
                assertTrue(e.getMessage() != null && e.getMessage().contains("querystring"),
                        "pre-3.2 output mapper must reject in: querystring: " + e.getMessage());
            } catch (OpenApiConfigurationException e) {
                throw new AssertionError(e);
            }
        }
    }

}
