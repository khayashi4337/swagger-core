package io.swagger.v3.rest;

import io.swagger.v3.core.filter.AbstractSpecFilter;
import io.swagger.v3.core.filter.SpecFilter;
import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.rest.resources.BlankCallbackMethodResource;
import io.swagger.v3.rest.resources.PurgeTaggedResource;
import io.swagger.v3.rest.resources.QueryMethodResource;
import io.swagger.v3.rest.resources.UppercaseCallbackMethodResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that the reader maps a custom {@code @HttpMethod("QUERY")} annotation
 * (OpenAPI 3.2 query method) onto {@link PathItem#getQuery()}.
 */
public class QueryMethodTest {

    private static SwaggerConfiguration config32() {
        return new SwaggerConfiguration()
                .openAPI31(true)
                .openAPIVersion("3.2.0");
    }

    @Test(description = "read a resource declaring the OpenAPI 3.2 QUERY method")
    public void testQueryHttpMethod() {
        Reader reader = new Reader(config32());
        OpenAPI openAPI = reader.read(QueryMethodResource.class);

        PathItem pathItem = openAPI.getPaths().get("/pets/search");
        assertNotNull(pathItem);

        assertNotNull(pathItem.getQuery());
        assertEquals(pathItem.getQuery().getOperationId(), "searchPets");

        // the operation must land on query only, not leak onto other methods
        assertNull(pathItem.getGet());
        assertNull(pathItem.getPost());
    }

    @Test(description = "a QUERY operation must not reach OpenAPI 3.0 or 3.1 output")
    public void testQueryHttpMethodIsNotSerializedBeforeOpenAPI32() throws Exception {
        Reader reader = new Reader(config32());
        OpenAPI openAPI = reader.read(QueryMethodResource.class);

        // "query" is a Path Item fixed field only as of OpenAPI 3.2, so the operation the
        // reader resolved stays in the model but is gated out of pre-3.2 documents
        for (String serialized : new String[]{
                Yaml.mapper().writeValueAsString(openAPI),
                Yaml31.mapper().writeValueAsString(openAPI),
                Json.mapper().writeValueAsString(openAPI),
                Json31.mapper().writeValueAsString(openAPI)}) {
            assertFalse(serialized.contains("query"));
            assertFalse(serialized.contains("searchPets"));
        }
    }

    @Test(description = "the legacy setOpenAPI31 setter still drives 3.1 parameter schemas")
    public void testLegacyOpenAPI31SetterStillApplies() {
        // without an explicit configuration the dedicated setters must keep
        // affecting parameter generation, as they did before the scan
        // configuration object was introduced
        DefaultParameterExtension extension = new DefaultParameterExtension();
        extension.setOpenAPI31(true);
        ResolvedParameter resolved = extension.extractParameters(
                Collections.<java.lang.annotation.Annotation>emptyList(),
                String.class, new java.util.HashSet<java.lang.reflect.Type>(),
                new io.swagger.v3.oas.models.Components(), null, null, true, null,
                Collections.<io.swagger.v3.rest.ext.OpenAPIExtension>emptyList().iterator());
        assertNotNull(resolved.requestBody);
        io.swagger.v3.oas.models.media.Schema<?> schema = resolved.requestBody.getSchema();
        assertNotNull(schema, "request body parameter must carry a resolved schema");
        assertTrue(schema instanceof io.swagger.v3.oas.models.media.JsonSchema,
                "setOpenAPI31(true) must keep producing 3.1 JsonSchema, got " + schema.getClass());
    }

    @Test(description = "a QUERY operation is dropped by pre-3.2 scans, as before the 3.2 support")
    public void testQueryHttpMethodDroppedBeforeOpenAPI32() {
        for (OpenAPIConfiguration scan : new OpenAPIConfiguration[]{
                new SwaggerConfiguration(),
                new SwaggerConfiguration().openAPI31(true)}) {
            OpenAPI openAPI = new Reader(scan).read(QueryMethodResource.class);

            PathItem pathItem = openAPI.getPaths().get("/pets/search");
            assertNotNull(pathItem);
            assertTrue(pathItem.readOperations().isEmpty(),
                    "pre-3.2 scan must not bind the QUERY operation");
            assertNull(pathItem.getQuery());
            assertNull(pathItem.getAdditionalOperations(),
                    "pre-3.2 scan must not fill additionalOperations");
        }
    }

    @Test(description = "a tag shared by a filtered op and a custom-method op is removed as unused before 3.2")
    public void testSharedTagRemovedAsUnusedBeforeOpenAPI32() {
        // removes the visible GET operation; only the invisible PURGE op then still
        // carries the "shared" tag. If PURGE leaked into the document the tag would
        // be kept as allowed; with it dropped the tag must be removed as unused.
        AbstractSpecFilter removeGet = new AbstractSpecFilter() {
            @Override
            public Optional<Operation> filterOperation(Operation operation, ApiDescription api,
                                                       Map<String, List<String>> params, Map<String, String> cookies,
                                                       Map<String, List<String>> headers) {
                return "listCacheEntries".equals(operation.getOperationId())
                        ? Optional.empty() : Optional.of(operation);
            }
        };

        for (OpenAPIConfiguration scan : new OpenAPIConfiguration[]{
                new SwaggerConfiguration()
                        .openAPI(new OpenAPI().addTagsItem(new Tag().name("shared"))),
                new SwaggerConfiguration()
                        .openAPI31(true)
                        .openAPI(new OpenAPI().addTagsItem(new Tag().name("shared")))}) {
            OpenAPI openAPI = new Reader(scan).read(PurgeTaggedResource.class);
            assertNotNull(openAPI.getPaths().get("/cache/entries"));
            assertNull(openAPI.getPaths().get("/cache/entries").getAdditionalOperations(),
                    "pre-3.2 scan must not fill additionalOperations");

            OpenAPI filtered = new SpecFilter().filter(openAPI, removeGet, null, null, null);
            assertTrue(filtered.getTags() == null
                            || filtered.getTags().stream().noneMatch(t -> "shared".equals(t.getName())),
                    "top-level tag used only by removed/dropped operations must be removed as unused");
        }

        // the 3.2 scan keeps PURGE in additionalOperations, so the same filter run
        // must keep the shared tag as allowed
        OpenAPI filtered32 = new SpecFilter().filter(
                new Reader(config32()
                        .openAPI(new OpenAPI().addTagsItem(new Tag().name("shared"))))
                        .read(PurgeTaggedResource.class), removeGet, null, null, null);
        assertTrue(filtered32.getTags() != null
                        && filtered32.getTags().stream().anyMatch(t -> "shared".equals(t.getName())),
                "3.2 scan keeps the additional operation, so the shared tag stays allowed");
    }

    @Test(description = "uppercase @Callback method binds only for a 3.2 scan")
    public void testCallbackUppercaseMethodBoundOnlyIn32() {
        for (OpenAPIConfiguration scan : new OpenAPIConfiguration[]{
                new SwaggerConfiguration(),
                new SwaggerConfiguration().openAPI31(true)}) {
            OpenAPI openAPI = new Reader(scan).read(UppercaseCallbackMethodResource.class);
            PathItem callbackItem = openAPI.getPaths().get("/uppercase-callback")
                    .getGet().getCallbacks().get("events").get("http://callback.url/events");
            assertNotNull(callbackItem);
            assertNull(callbackItem.getPost(),
                    "pre-3.2 scan must drop the uppercase callback method as before");
            assertNotNull(callbackItem.getGet(), "lowercase callback method keeps binding");
        }

        OpenAPI openAPI32 = new Reader(config32()).read(UppercaseCallbackMethodResource.class);
        PathItem callbackItem32 = openAPI32.getPaths().get("/uppercase-callback")
                .getGet().getCallbacks().get("events").get("http://callback.url/events");
        assertNotNull(callbackItem32);
        assertNotNull(callbackItem32.getPost(),
                "3.2 scan must bind the uppercase callback method case-insensitively");
        assertNotNull(callbackItem32.getGet());
    }

    @Test(description = "a @Callback operation without a method is not filed under additionalOperations[\"\"]")
    public void testCallbackWithoutMethodIsSkipped() {
        for (OpenAPIConfiguration scan : new OpenAPIConfiguration[]{
                new SwaggerConfiguration(),
                new SwaggerConfiguration().openAPI31(true),
                config32()}) {
            OpenAPI openAPI = new Reader(scan).read(BlankCallbackMethodResource.class);
            PathItem callbackItem = openAPI.getPaths().get("/blank-callback")
                    .getGet().getCallbacks().get("events").get("http://callback.url/events");
            assertNotNull(callbackItem);
            assertTrue(callbackItem.getAdditionalOperations() == null
                            || !callbackItem.getAdditionalOperations().containsKey(""),
                    "an empty callback method must not create additionalOperations[\"\"]");
        }
        // the declared post callback still binds
        OpenAPI openAPI32 = new Reader(config32()).read(BlankCallbackMethodResource.class);
        PathItem callbackItem32 = openAPI32.getPaths().get("/blank-callback")
                .getGet().getCallbacks().get("events").get("http://callback.url/events");
        assertNotNull(callbackItem32.getPost(), "the declared post callback must bind");
    }

    @Test(description = "operationId dedup scans query/additionalOperations only for 3.2")
    public void testOperationIdDedupScope() {
        // a seeded query operation carrying the same operationId as the scanned
        // GET: only a 3.2 scan counts it when deduplicating
        OpenAPI seed = new OpenAPI().paths(new Paths().addPathItem("/seeded",
                new PathItem().query(new Operation().operationId("listCacheEntries"))));
        SwaggerConfiguration scan32 = new SwaggerConfiguration()
                .openAPI31(true)
                .openAPIVersion("3.2.0")
                .openAPI(seed);
        OpenAPI openAPI32 = new Reader(scan32).read(PurgeTaggedResource.class);
        assertNotEquals(openAPI32.getPaths().get("/cache/entries").getGet().getOperationId(),
                "listCacheEntries",
                "3.2 must deduplicate operationIds against the seeded query operation");

        // pre-3.2: the seeded query operation is not enumerated, like the baseline
        OpenAPI seed2 = new OpenAPI().paths(new Paths().addPathItem("/seeded",
                new PathItem().query(new Operation().operationId("listCacheEntries"))));
        SwaggerConfiguration scan30 = new SwaggerConfiguration().openAPI(seed2);
        OpenAPI openAPI30 = new Reader(scan30).read(PurgeTaggedResource.class);
        assertEquals(openAPI30.getPaths().get("/cache/entries").getGet().getOperationId(),
                "listCacheEntries",
                "pre-3.2 must keep the baseline enumeration scope");
    }
}
