package io.swagger.v3.rest;

import io.swagger.v3.core.filter.AbstractSpecFilter;
import io.swagger.v3.core.filter.SpecFilter;
import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.core.util.Configuration;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.rest.ext.AbstractOpenAPIExtension;
import io.swagger.v3.rest.ext.OpenAPIExtension;
import io.swagger.v3.rest.ext.OpenAPIExtensions;
import io.swagger.v3.rest.resources.BlankCallbackMethodResource;
import io.swagger.v3.rest.resources.PurgeMethodResource;
import io.swagger.v3.rest.resources.PurgeTaggedResource;
import io.swagger.v3.rest.resources.QueryMethodResource;
import io.swagger.v3.rest.resources.QueryStringCtorParamResource;
import io.swagger.v3.rest.resources.QueryStringFieldParamResource;
import io.swagger.v3.rest.resources.QueryStringParamResource;
import io.swagger.v3.rest.resources.UppercaseCallbackMethodResource;
import io.swagger.v3.rest.util.ReaderUtils;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
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

    @Test(description = "a custom HTTP method lands in additionalOperations with its declared case")
    public void testCustomHttpMethodGoesToAdditionalOperations() {
        Reader reader = new Reader(config32());
        OpenAPI openAPI = reader.read(PurgeMethodResource.class);

        PathItem pathItem = openAPI.getPaths().get("/cache/entries");
        assertNotNull(pathItem);

        assertNotNull(pathItem.getAdditionalOperations(), "custom method must be kept in additionalOperations");
        assertTrue(pathItem.getAdditionalOperations().containsKey("PURGE"),
                "additionalOperations key must preserve the declared case");
        assertEquals(pathItem.getAdditionalOperations().get("PURGE").getOperationId(), "purgeCache");

        // and it must not be silently folded into any fixed operation slot
        assertNull(pathItem.getGet());
        assertNull(pathItem.getPost());
        assertNull(pathItem.getDelete());
    }

    @Test(description = "custom-method operations must not reach OpenAPI 3.0 or 3.1 output")
    public void testCustomHttpMethodIsNotSerializedBeforeOpenAPI32() throws Exception {
        Reader reader = new Reader(config32());
        OpenAPI openAPI = reader.read(PurgeMethodResource.class);

        for (String serialized : new String[]{
                Yaml.mapper().writeValueAsString(openAPI),
                Yaml31.mapper().writeValueAsString(openAPI),
                Json.mapper().writeValueAsString(openAPI),
                Json31.mapper().writeValueAsString(openAPI)}) {
            assertFalse(serialized.contains("purgeCache"));
            assertFalse(serialized.contains("PURGE"));
        }
    }

    @Test(description = "in: querystring is emitted only for OpenAPI 3.2 scans")
    public void testQueryStringParameterIsVersionGated() {
        QueryStringParamResource resource = new QueryStringParamResource();

        // a 3.2 scan keeps the parameter
        SwaggerConfiguration config32 = new SwaggerConfiguration()
                .openAPI31(true)
                .openAPIVersion("3.2.0");
        OpenAPI openAPI32 = new Reader(config32).read(resource.getClass());
        PathItem pathItem32 = openAPI32.getPaths().get("/search");
        assertNotNull(pathItem32.getGet());
        assertNotNull(pathItem32.getGet().getParameters());
        assertEquals(pathItem32.getGet().getParameters().get(0).getIn(), "querystring");

        // a 3.1 scan drops it rather than emitting an unrepresentable 'in' value
        SwaggerConfiguration config31 = new SwaggerConfiguration().openAPI31(true);
        OpenAPI openAPI31 = new Reader(config31).read(resource.getClass());
        PathItem pathItem31 = openAPI31.getPaths().get("/search");
        assertNotNull(pathItem31.getGet());
        assertTrue(pathItem31.getGet().getParameters() == null
                        || pathItem31.getGet().getParameters().stream()
                        .noneMatch(p -> "querystring".equals(p.getIn())),
                "in: querystring must not be emitted for a non-3.2 document");
    }

    @Test(description = "constructor and field level in: querystring see the scan's spec version")
    public void testQueryStringParameterIsVersionGatedForCtorAndFieldParams() {
        // ctor-only and field-only resources: at most one in: querystring parameter
        // is legal per operation, so the two collection paths are verified separately
        // by parameter name
        for (String[][] caseDef : new String[][][]{
                {{"/ctorsearch", "cq"}},
                {{"/fieldsearch", "fq"}}}) {
            String path = caseDef[0][0];
            String expectedName = caseDef[0][1];
            Class<?> resource = "/ctorsearch".equals(path)
                    ? QueryStringCtorParamResource.class : QueryStringFieldParamResource.class;

            OpenAPI openAPI32 = new Reader(config32()).read(resource);
            PathItem pathItem32 = openAPI32.getPaths().get(path);
            assertNotNull(pathItem32.getGet());
            assertNotNull(pathItem32.getGet().getParameters());
            assertTrue(pathItem32.getGet().getParameters().stream()
                            .anyMatch(p -> "querystring".equals(p.getIn())
                                    && expectedName.equals(p.getName())),
                    "3.2 scan must keep the " + expectedName + " querystring parameter");

            SwaggerConfiguration config31 = new SwaggerConfiguration().openAPI31(true);
            OpenAPI openAPI31 = new Reader(config31).read(resource);
            PathItem pathItem31 = openAPI31.getPaths().get(path);
            assertNotNull(pathItem31.getGet());
            assertTrue(pathItem31.getGet().getParameters() == null
                            || pathItem31.getGet().getParameters().stream()
                            .noneMatch(p -> "querystring".equals(p.getIn())),
                    "non-3.2 scan must drop the " + expectedName + " querystring parameter");
        }
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

    @Test(description = "custom-method operations are dropped by pre-3.2 scans and filtered paths disappear")
    public void testCustomHttpMethodDroppedBeforeOpenAPI32() {
        for (OpenAPIConfiguration scan : new OpenAPIConfiguration[]{
                new SwaggerConfiguration(),
                new SwaggerConfiguration().openAPI31(true)}) {
            OpenAPI openAPI = new Reader(scan).read(PurgeMethodResource.class);

            PathItem pathItem = openAPI.getPaths().get("/cache/entries");
            assertNotNull(pathItem);
            assertTrue(pathItem.readOperations().isEmpty(),
                    "pre-3.2 scan must not keep the PURGE operation");
            assertNull(pathItem.getAdditionalOperations());

            OpenAPI filtered = new SpecFilter().filter(openAPI, new AbstractSpecFilter() {
            }, null, null, null);
            assertTrue(filtered.getPaths() == null
                            || !filtered.getPaths().containsKey("/cache/entries"),
                    "SpecFilter must drop the operation-less path instead of leaving an empty object");
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

    @Test(description = "scan configuration reaches parameter extensions behind delegating stubs")
    public void testDelegatingExtensionKeepsScanConfiguration() {
        // reset the shared parameter extensions so a configuration left behind by
        // an earlier scan cannot mask a missing propagation: the outcome must be
        // decided by this scan's own configuration, independent of test order
        for (OpenAPIExtension ext : OpenAPIExtensions.getExtensions()) {
            ext.setConfiguration(new Configuration());
        }
        // a delegation-only extension placed before DefaultParameterExtension must not
        // swallow the scan configuration: the 3.2 querystring parameter must survive
        AbstractOpenAPIExtension delegating = new AbstractOpenAPIExtension() {
        };
        OpenAPIExtensions.getExtensions().add(0, delegating);
        try {
            OpenAPI openAPI = new Reader(config32()).read(QueryStringParamResource.class);
            PathItem pathItem = openAPI.getPaths().get("/search");
            assertNotNull(pathItem.getGet());
            assertNotNull(pathItem.getGet().getParameters());
            assertTrue(pathItem.getGet().getParameters().stream()
                            .anyMatch(p -> "querystring".equals(p.getIn())),
                    "3.2 scan must keep the querystring parameter behind a delegating extension");
        } finally {
            OpenAPIExtensions.getExtensions().remove(delegating);
            for (OpenAPIExtension ext : OpenAPIExtensions.getExtensions()) {
                ext.setConfiguration(new Configuration());
            }
        }
    }

    private static class TestParameterExtension extends DefaultParameterExtension {
        Configuration effective() {
            return effectiveConfiguration();
        }
    }

    @Test(description = "the last of setConfiguration/setOpenAPI31/setSchemaResolution wins")
    public void testExtensionSetterPrecedence() {
        TestParameterExtension extension = new TestParameterExtension();

        Configuration scan = new Configuration();
        scan.setOpenAPI31(true);
        scan.setSchemaResolution(Schema.SchemaResolution.ALL_OF);
        extension.setConfiguration(scan);
        // a dedicated setter call after setConfiguration overrides the value
        // the configuration brought in
        extension.setOpenAPI31(false);
        assertEquals(extension.effective().isOpenAPI31(), Boolean.FALSE,
                "setOpenAPI31(false) after setConfiguration must win");
        extension.setSchemaResolution(Schema.SchemaResolution.DEFAULT);
        assertEquals(extension.effective().getSchemaResolution(),
                Schema.SchemaResolution.DEFAULT,
                "setSchemaResolution after setConfiguration must win");

        // and a later setConfiguration overrides the earlier setters
        Configuration later = new Configuration();
        later.setOpenAPI31(true);
        later.setSchemaResolution(Schema.SchemaResolution.ALL_OF);
        extension.setConfiguration(later);
        assertEquals(extension.effective().isOpenAPI31(), Boolean.TRUE,
                "setConfiguration after the setters must win");
        assertEquals(extension.effective().getSchemaResolution(),
                Schema.SchemaResolution.ALL_OF);

        // setConfiguration(null) falls back to the dedicated setters, like the
        // original signature-only contract
        extension.setConfiguration(null);
        extension.setOpenAPI31(true);
        assertEquals(extension.effective().isOpenAPI31(), Boolean.TRUE,
                "without a configuration the dedicated setters must apply");
    }

    @Test(description = "legacy parameter collection must not leak a previous scan's 3.2 configuration")
    public void testLegacyCollectDoesNotReuseScanConfiguration() {
        // a 3.2 scan pushes its configuration onto the shared extension
        new Reader(config32()).read(QueryStringFieldParamResource.class);
        // a legacy (no configuration) collection call must not see that leftover:
        // the field-level in: querystring parameter is dropped as on a 3.0/3.1 call
        List<Parameter> params = ReaderUtils.collectFieldParameters(
                QueryStringFieldParamResource.class, new Components(), null, null);
        assertTrue(params == null || params.stream().noneMatch(p -> "querystring".equals(p.getIn())),
                "in: querystring must not leak from a previous 3.2 scan into a legacy call");

        List<Parameter> ctorParams = ReaderUtils.collectConstructorParameters(
                QueryStringCtorParamResource.class, new Components(), null, null,
                Schema.SchemaResolution.DEFAULT, false);
        assertTrue(ctorParams == null || ctorParams.stream().noneMatch(p -> "querystring".equals(p.getIn())),
                "in: querystring must not leak into the legacy constructor-parameter path either");
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

    @Test(description = "legacy extractOperationMethod lowercases custom methods like the baseline")
    public void testExtractOperationMethodCaseContract() throws Exception {
        Method query = QueryMethodResource.class.getDeclaredMethod("searchPets");
        Method purge = PurgeMethodResource.class.getDeclaredMethod("purgeCache");

        // the original two-argument API must keep the baseline contract: custom
        // @HttpMethod values come back lowercased
        assertEquals(ReaderUtils.extractOperationMethod(query, null), "query",
                "legacy signature must lowercase the custom method");
        assertEquals(ReaderUtils.extractOperationMethod(purge, null), "purge",
                "legacy signature must lowercase the custom method");
        assertEquals(ReaderUtils.getHttpMethodFromCustomAnnotations(query), "query",
                "legacy signature must lowercase the custom method");
        assertEquals(ReaderUtils.getHttpMethodFromCustomAnnotations(purge), "purge",
                "legacy signature must lowercase the custom method");

        // the declared-case variant used by 3.2 scans keeps the declared case
        assertEquals(ReaderUtils.extractOperationMethod(query, null, true), "QUERY",
                "declaredCase must keep the declared case");
        assertEquals(ReaderUtils.extractOperationMethod(purge, null, true), "PURGE",
                "declaredCase must keep the declared case");
        assertEquals(ReaderUtils.getHttpMethodFromCustomAnnotations(query, true), "QUERY");
        assertEquals(ReaderUtils.getHttpMethodFromCustomAnnotations(purge, true), "PURGE");
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

    @Test(description = "a MediaType carrying itemSchema survives scan-time cloning on a 3.2 scan")
    public void testMediaTypeCloneKeepsItemSchema32() throws Exception {
        // resolveResponseSchemaFromReturnType clones each response media type on
        // a mapper; for a 3.2 scan that must be the 3.2 mapper or the 3.2-only
        // itemSchema field is silently dropped
        Method holder = ItemSchemaResponseHolder.class.getDeclaredMethod("get");
        io.swagger.v3.oas.annotations.responses.ApiResponse[] apiResponses =
                holder.getAnnotationsByType(io.swagger.v3.oas.annotations.responses.ApiResponse.class);
        MediaType carrying = new MediaType()
                .itemSchema(new Schema().typesItem("string"));
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("200",
                new ApiResponse().content(new Content().addMediaType("application/json", carrying))));

        new Reader(config32()).resolveResponseSchemaFromReturnType(
                operation, apiResponses, new Schema().typesItem("string"), null, null);
        MediaType cloned = operation.getResponses().get("200").getContent().get("application/json");
        assertNotNull(cloned.getItemSchema(),
                "itemSchema must survive scan-time MediaType cloning for a 3.2 scan");
        assertTrue(cloned.getItemSchema().getTypes().contains("string"),
                "itemSchema content must survive the 3.2 round-trip");
    }

    private static class ItemSchemaResponseHolder {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "ok", useReturnTypeSchema = true)
        void get() {
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
}
