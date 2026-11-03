package io.swagger.v3.core.serialization;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.core.filter.AbstractSpecFilter;
import io.swagger.v3.core.filter.SpecFilter;
import io.swagger.v3.core.filter.resources.NoOpOperationsFilter;
import io.swagger.v3.core.model.ApiDescription;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Json32;
import io.swagger.v3.core.util.MapperCustomizer;
import io.swagger.v3.core.util.MapperTarget;
import io.swagger.v3.core.util.ObjectMapperFactory;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.core.util.Yaml32;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.std.DelegatingDeserializer;
import tools.jackson.databind.module.SimpleModule;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class OpenAPI3_2SerializationTest {

    private OpenAPI buildDoc() {
        Operation queryOp = new Operation()
                .operationId("queryPets")
                .responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().description("query result")));
        PathItem item = new PathItem().query(queryOp);
        Paths paths = new Paths();
        paths.addPathItem("/pets", item);
        return new OpenAPI()
                .openapi("3.2.0")
                .info(new Info().title("t").version("1"))
                .paths(paths);
    }

    @Test
    public void querySerializesIn32Json() throws Exception {
        String out = Json32.mapper().writeValueAsString(buildDoc());
        assertTrue(out.contains("\"query\":"), "3.2 JSON output should contain 'query': " + out);
        assertTrue(out.contains("queryPets"), out);
    }

    @Test
    public void querySerializesIn32Yaml() throws Exception {
        String out = Yaml32.mapper().writeValueAsString(buildDoc());
        assertTrue(out.contains("query:"), "3.2 YAML output should contain 'query:': " + out);
        assertTrue(out.contains("queryPets"), out);
    }

    @Test
    public void queryHiddenIn30And31() throws Exception {
        String out30 = Json.mapper().writeValueAsString(buildDoc());
        assertFalse(out30.contains("\"query\""), "3.0 output must not contain 'query': " + out30);
        String out31 = Json31.mapper().writeValueAsString(buildDoc());
        assertFalse(out31.contains("\"query\""), "3.1 output must not contain 'query': " + out31);
    }

    @Test
    public void queryRoundTrip32() throws Exception {
        OpenAPI doc = buildDoc();
        String out = Json32.mapper().writeValueAsString(doc);
        OpenAPI readBack = Json32.mapper().readValue(out, OpenAPI.class);
        assertNotNull(readBack.getPaths().get("/pets").getQuery());
        assertEquals(readBack.getPaths().get("/pets").getQuery().getOperationId(), "queryPets");
    }

    @Test
    public void queryDeserializesFromDocument() throws Exception {
        String doc = "openapi: 3.2.0\n" +
                "info:\n" +
                "  title: t\n" +
                "  version: '1'\n" +
                "paths:\n" +
                "  /pets:\n" +
                "    query:\n" +
                "      operationId: queryPets\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: query result\n";
        OpenAPI readBack = Yaml32.mapper().readValue(doc, OpenAPI.class);
        PathItem item = readBack.getPaths().get("/pets");
        assertNotNull(item);
        assertNotNull(item.getQuery());
        assertEquals(item.getQuery().getOperationId(), "queryPets");
    }

    @Test
    public void queryIgnoredIn31Deserialization() throws Exception {
        // 3.1 mapper must not surface 'query' even if present in input
        String doc = "openapi: 3.1.0\n" +
                "info:\n" +
                "  title: t\n" +
                "  version: '1'\n" +
                "paths:\n" +
                "  /pets:\n" +
                "    query:\n" +
                "      operationId: queryPets\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: query result\n";
        OpenAPI readBack = Yaml31.mapper().readValue(doc, OpenAPI.class);
        PathItem item = readBack.getPaths().get("/pets");
        assertNotNull(item);
        assertNull(item.getQuery(), "3.1 mapper must not bind 'query'");
    }

    @Test
    public void queryBindsInNestedPathItems32() throws Exception {
        String doc = "openapi: 3.2.0\n" +
                "info:\n" +
                "  title: t\n" +
                "  version: '1'\n" +
                "webhooks:\n" +
                "  petEvent:\n" +
                "    query:\n" +
                "      operationId: webhookQuery\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: ok\n" +
                "components:\n" +
                "  pathItems:\n" +
                "    shared:\n" +
                "      query:\n" +
                "        operationId: sharedQuery\n" +
                "        responses:\n" +
                "          '200':\n" +
                "            description: ok\n";
        OpenAPI readBack = Yaml32.mapper().readValue(doc, OpenAPI.class);
        assertNotNull(readBack.getWebhooks().get("petEvent").getQuery(), "webhooks PathItem must bind 'query'");
        assertEquals(readBack.getWebhooks().get("petEvent").getQuery().getOperationId(), "webhookQuery");
        assertNotNull(readBack.getComponents().getPathItems().get("shared").getQuery(), "components.pathItems must bind 'query'");
        assertEquals(readBack.getComponents().getPathItems().get("shared").getQuery().getOperationId(), "sharedQuery");
    }

    @Test
    public void specVersionIsV32() throws Exception {
        String doc = "openapi: 3.2.0\ninfo:\n  title: t\n  version: '1'\npaths: {}\n";
        OpenAPI readBack = Yaml32.mapper().readValue(doc, OpenAPI.class);
        assertEquals(readBack.getSpecVersion(), io.swagger.v3.oas.models.SpecVersion.V32);
    }

    @Test
    public void queryBindsInsideCallback32() throws Exception {
        String doc = "openapi: 3.2.0\n" +
                "info:\n" +
                "  title: t\n" +
                "  version: '1'\n" +
                "paths:\n" +
                "  /pets:\n" +
                "    get:\n" +
                "      operationId: getPets\n" +
                "      callbacks:\n" +
                "        onData:\n" +
                "          '{$request.body#/cbUrl}':\n" +
                "            query:\n" +
                "              operationId: callbackQuery\n" +
                "              responses:\n" +
                "                '200':\n" +
                "                  description: ok\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: ok\n";
        OpenAPI readBack = Yaml32.mapper().readValue(doc, OpenAPI.class);
        PathItem callbackItem = readBack.getPaths().get("/pets").getGet()
                .getCallbacks().get("onData").get("{$request.body#/cbUrl}");
        assertNotNull(callbackItem);
        assertNotNull(callbackItem.getQuery(), "callback PathItem must bind 'query'");
        assertEquals(callbackItem.getQuery().getOperationId(), "callbackQuery");
    }

    @Test
    public void queryRoundTrip32Yaml() throws Exception {
        OpenAPI doc = buildDoc();
        String out = Yaml32.mapper().writeValueAsString(doc);
        OpenAPI readBack = Yaml32.mapper().readValue(out, OpenAPI.class);
        assertNotNull(readBack.getPaths().get("/pets").getQuery());
        assertEquals(readBack.getPaths().get("/pets").getQuery().getOperationId(), "queryPets");
        assertEquals(readBack.getSpecVersion(), SpecVersion.V32);
    }

    @Test
    public void v32DocumentBindsStandardModels() throws Exception {
        // Coverage: a 3.2 document parses end-to-end through the V32 pipeline and binds
        // standard models (parameters, responses with content, schemas, securitySchemes).
        // Note: these model shapes parse identically under the 3.1 and 3.2 mappers, so
        // this test does not prove V32-specific binding semantics on its own; the
        // V32-only observable behavior is covered by the 'query' PathItem tests above
        // and by specVersionIsV32.
        String doc = "openapi: 3.2.0\n" +
                "info:\n" +
                "  title: t\n" +
                "  version: '1'\n" +
                "paths:\n" +
                "  /pets:\n" +
                "    get:\n" +
                "      operationId: getPets\n" +
                "      parameters:\n" +
                "        - name: limit\n" +
                "          in: query\n" +
                "          schema:\n" +
                "            type: integer\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: pet list\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                type: array\n" +
                "                items:\n" +
                "                  type: string\n" +
                "components:\n" +
                "  schemas:\n" +
                "    Pet:\n" +
                "      type:\n" +
                "        - string\n" +
                "        - 'null'\n" +
                "  securitySchemes:\n" +
                "    bearerAuth:\n" +
                "      type: http\n" +
                "      scheme: bearer\n";
        OpenAPI readBack = Yaml32.mapper().readValue(doc, OpenAPI.class);
        assertNotNull(readBack);

        Parameter p = readBack.getPaths().get("/pets").getGet().getParameters().get(0);
        assertNotNull(p);
        assertEquals(p.getName(), "limit");
        assertEquals(p.getIn(), "query");

        ApiResponse response = readBack.getPaths().get("/pets").getGet().getResponses().get("200");
        assertNotNull(response);
        assertEquals(response.getDescription(), "pet list");
        assertNotNull(response.getContent());
        assertNotNull(response.getContent().get("application/json"));
        assertNotNull(response.getContent().get("application/json").getSchema());

        Schema pet = readBack.getComponents().getSchemas().get("Pet");
        assertNotNull(pet);
        assertNotNull(pet.getTypes(), "JSON-Schema-style types array must bind through the V32 model deserializer");
        assertTrue(pet.getTypes().contains("string") && pet.getTypes().contains("null"),
                "types must contain string and null: " + pet.getTypes());

        SecurityScheme scheme = readBack.getComponents().getSecuritySchemes().get("bearerAuth");
        assertNotNull(scheme);
        assertEquals(scheme.getType(), SecurityScheme.Type.HTTP);
        assertEquals(scheme.getScheme(), "bearer");
    }

    @Test
    public void toStringIncludesWebhooksAndDialectForV32() {
        OpenAPI doc = buildDoc();
        doc.setSpecVersion(SpecVersion.V32);
        doc.setJsonSchemaDialect("https://json-schema.org/draft/2020-12/schema");
        Map<String, PathItem> hooks = new LinkedHashMap<>();
        hooks.put("petEvent", new PathItem());
        doc.setWebhooks(hooks);

        String s = doc.toString();
        assertTrue(s.contains("webhooks:"), "V32 toString must print webhooks: " + s);
        assertTrue(s.contains("jsonSchemaDialect:"), "V32 toString must print jsonSchemaDialect: " + s);

        doc.setSpecVersion(SpecVersion.V30);
        String s30 = doc.toString();
        assertFalse(s30.contains("webhooks:"), "V30 toString must not print webhooks");
        assertFalse(s30.contains("jsonSchemaDialect:"), "V30 toString must not print jsonSchemaDialect");
    }

    @Test
    public void schemaToStringIncludes31KeywordsForV32() {
        // Regression for Schema.java toString fix: the 3.1-style keyword block must be
        // emitted for V32 too (was `== V31`, now `!= V30`).
        Schema schema = new Schema();
        schema.setSpecVersion(SpecVersion.V32);
        schema.setPatternProperties(java.util.Collections.singletonMap("^a", new Schema()));
        String out = schema.toString();
        assertTrue(out.contains("patternProperties:"), "V32 Schema.toString must print 3.1 keywords: " + out);

        schema.setSpecVersion(SpecVersion.V30);
        assertFalse(schema.toString().contains("patternProperties:"), "V30 must not print 3.1 keywords");
    }

    @Test
    public void specFilterClonePreserves32SchemaFields() {
        // bug: schema cloning went through a pre-3.2 mapper and dropped JSON Schema
        // keywords that the 3.0 view ignores (types/patternProperties/contains/$id);
        // the clone must run on the mapper of the document's own version
        OpenAPI doc = buildDoc();
        Schema pet = new Schema().typesItem("object").typesItem("null");
        pet.addPatternProperty("^a", new Schema().typesItem("string"));
        pet.setContains(new Schema().typesItem("string"));
        pet.set$id("https://example.com/pet.schema.json");
        doc.setComponents(new Components().addSchemas("Pet", pet));
        OpenAPI filtered = new SpecFilter().filter(doc, new NoOpOperationsFilter(), null, null, null);
        Schema filteredPet = filtered.getComponents().getSchemas().get("Pet");
        assertNotNull(filteredPet);
        assertNotNull(filteredPet.getTypes(), "types must survive SpecFilter cloning");
        assertTrue(filteredPet.getTypes().contains("object") && filteredPet.getTypes().contains("null"),
                "types must contain object and null: " + filteredPet.getTypes());
        assertNotNull(filteredPet.getPatternProperties(), "patternProperties must survive SpecFilter cloning");
        assertNotNull(filteredPet.getPatternProperties().get("^a"));
        assertNotNull(filteredPet.getContains(), "contains must survive SpecFilter cloning");
        assertEquals(filteredPet.get$id(), "https://example.com/pet.schema.json");
    }

    @Test
    public void filterComponentsSchemaOverrideStillInvoked() {
        // subclasses overriding the five-argument filterComponentsSchema must keep
        // participating after the version-aware clone was introduced
        final boolean[] called = {false};
        SpecFilter custom = new SpecFilter() {
            @Override
            protected java.util.Map<String, Schema> filterComponentsSchema(
                    io.swagger.v3.core.filter.OpenAPISpecFilter filter,
                    java.util.Map<String, Schema> schemasMap,
                    java.util.Map<String, List<String>> params,
                    java.util.Map<String, String> cookies,
                    java.util.Map<String, List<String>> headers) {
                called[0] = true;
                return super.filterComponentsSchema(filter, schemasMap, params, cookies, headers);
            }
        };
        OpenAPI doc = buildDoc();
        doc.setComponents(new Components().addSchemas("Pet", new Schema().typesItem("object")));
        OpenAPI filtered = custom.filter(doc, new NoOpOperationsFilter(), null, null, null);
        assertTrue(called[0], "overridden filterComponentsSchema must be invoked");
        assertNotNull(filtered.getComponents().getSchemas().get("Pet"));
    }

    @Test
    public void explicit30VersionClonesWith30Mapper() {
        // an explicit openapi: 3.0.x is authoritative even when specVersion was left
        // inconsistent; the schema must be cloned through the 3.0 mapper
        OpenAPI doc = new OpenAPI()
                .openapi("3.0.3")
                .specVersion(SpecVersion.V32)
                .info(new Info().title("t").version("1"))
                .paths(new Paths());
        // 'nullable' only survives a 3.0 clone; 3.1+/3.2 mappers drop it
        doc.setComponents(new Components()
                .addSchemas("S", new Schema().type("string").nullable(true)));
        OpenAPI filtered = new SpecFilter().filter(doc, new NoOpOperationsFilter(), null, null, null);
        Schema filteredSchema = filtered.getComponents().getSchemas().get("S");
        assertNotNull(filteredSchema);
        assertEquals(filteredSchema.getNullable(), Boolean.TRUE,
                "a 3.0 document must be cloned through the 3.0 mapper, keeping 'nullable'");
    }

    @Test
    public void filter31DocWithNormalFilterPreservesJsonSchemaKeywords() {
        // pins a deliberate behavior change: a 3.1 document filtered by a regular
        // (non-isOpenAPI31Filter) filter is now cloned through the 3.1 mapper, so
        // JSON Schema keywords survive; before, it went through the 3.0 mapper and
        // dropped types/$id/patternProperties
        OpenAPI doc = new OpenAPI()
                .openapi("3.1.0")
                .info(new Info().title("t").version("1"))
                .paths(new Paths());
        Schema pet = new Schema().typesItem("object").typesItem("null");
        pet.set$id("https://example.com/pet.schema.json");
        pet.addPatternProperty("^a", new Schema().typesItem("string"));
        doc.setComponents(new Components().addSchemas("Pet", pet));

        OpenAPI filtered = new SpecFilter().filter(doc, new NoOpOperationsFilter(), null, null, null);
        Schema filteredPet = filtered.getComponents().getSchemas().get("Pet");
        assertNotNull(filteredPet);
        assertEquals(filteredPet.get$id(), "https://example.com/pet.schema.json",
                "a 3.1 document must be cloned through the 3.1 mapper, keeping '$id'");
        assertNotNull(filteredPet.getTypes(), "types must survive 3.1 cloning");
        assertTrue(filteredPet.getTypes().contains("object") && filteredPet.getTypes().contains("null"),
                "types must contain object and null: " + filteredPet.getTypes());
        assertNotNull(filteredPet.getPatternProperties(), "patternProperties must survive 3.1 cloning");
        assertNotNull(filteredPet.getPatternProperties().get("^a"));
    }

    @Test
    public void nestedFilterCallOnSameInstanceKeepsOuterSpecVersion() {
        // regression: the resolved spec version used to live in a shared instance
        // field. A nested filter() call (here from inside a filterPathItem callback)
        // overwrote it, so the outer 3.2 document's schemas were cloned through the
        // nested document's 3.0 mapper and lost '$id'
        SpecFilter specFilter = new SpecFilter();
        OpenAPI inner30 = new OpenAPI()
                .openapi("3.0.3")
                .info(new Info().title("inner").version("1"));
        AbstractSpecFilter nestingFilter = new AbstractSpecFilter() {
            @Override
            public Optional<PathItem> filterPathItem(PathItem pathItem, ApiDescription api,
                    Map<String, List<String>> params, Map<String, String> cookies, Map<String, List<String>> headers) {
                specFilter.filter(inner30, new NoOpOperationsFilter(), params, cookies, headers);
                return super.filterPathItem(pathItem, api, params, cookies, headers);
            }
        };
        OpenAPI doc = buildDoc();
        Schema pet = new Schema().typesItem("object");
        pet.set$id("https://example.com/pet.schema.json");
        doc.setComponents(new Components().addSchemas("Pet", pet));

        OpenAPI filtered = specFilter.filter(doc, nestingFilter, null, null, null);
        Schema filteredPet = filtered.getComponents().getSchemas().get("Pet");
        assertNotNull(filteredPet);
        assertEquals(filteredPet.get$id(), "https://example.com/pet.schema.json",
                "outer 3.2 document must still clone through the 3.2 mapper after a nested 3.0 filter() call");
    }

    @Test
    public void concurrentFilterCallsOnSharedInstanceDoNotMixVersions() throws Exception {
        // same shared-state hazard between threads: the barrier in filterPathItem
        // guarantees both threads have stored their document's version before either
        // reaches the components clone, so a leaked version deterministically drops
        // one side's keyword
        SpecFilter specFilter = new SpecFilter();
        CyclicBarrier bothVersionsStored = new CyclicBarrier(2);
        AbstractSpecFilter syncingFilter = new AbstractSpecFilter() {
            @Override
            public Optional<PathItem> filterPathItem(PathItem pathItem, ApiDescription api,
                    Map<String, List<String>> params, Map<String, String> cookies, Map<String, List<String>> headers) {
                try {
                    bothVersionsStored.await(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return super.filterPathItem(pathItem, api, params, cookies, headers);
            }
        };

        OpenAPI doc30 = new OpenAPI()
                .openapi("3.0.3")
                .info(new Info().title("t").version("1"))
                .paths(new Paths().addPathItem("/pets", new PathItem()));
        doc30.setComponents(new Components()
                .addSchemas("S", new Schema().type("string").nullable(true)));

        OpenAPI doc32 = buildDoc();
        Schema pet = new Schema().typesItem("object");
        pet.set$id("https://example.com/pet.schema.json");
        doc32.setComponents(new Components().addSchemas("Pet", pet));

        OpenAPI[] results = new OpenAPI[2];
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable run30 = () -> {
            try {
                results[0] = specFilter.filter(doc30, syncingFilter, null, null, null);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
        Runnable run32 = () -> {
            try {
                results[1] = specFilter.filter(doc32, syncingFilter, null, null, null);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
        Thread t30 = new Thread(run30);
        Thread t32 = new Thread(run32);
        t30.start();
        t32.start();
        t30.join(30000);
        t32.join(30000);

        if (failure.get() != null) {
            fail("filter thread failed: " + failure.get());
        }
        assertEquals(results[0].getComponents().getSchemas().get("S").getNullable(), Boolean.TRUE,
                "3.0 document must keep 'nullable' — cloned through the 3.0 mapper");
        assertEquals(results[1].getComponents().getSchemas().get("Pet").get$id(),
                "https://example.com/pet.schema.json",
                "3.2 document must keep '$id' — cloned through the 3.2 mapper");
    }

    @Test
    public void updateReadStampsSpecVersionV32() throws Exception {
        // readerForUpdating routes through the three-argument deserialize, which used
        // to skip the V32 stamping in OpenAPI32Deserializer and leave the default V30
        OpenAPI updated = Json32.mapper()
                .readerForUpdating(new OpenAPI())
                .readValue("{\"openapi\":\"3.2.0\"}");
        assertEquals(updated.getSpecVersion(), SpecVersion.V32);
    }

    @Test
    public void updateReadLeaves31SpecVersionUnchanged() throws Exception {
        // OpenAPI31Deserializer has the same missing three-argument override; this pins
        // the unchanged 3.1 update-read result so the V32 fix is known not to alter it
        OpenAPI updated = Json31.mapper()
                .readerForUpdating(new OpenAPI())
                .readValue("{\"openapi\":\"3.1.0\"}");
        assertEquals(updated.getSpecVersion(), SpecVersion.V30);
    }

    @Test
    public void recontextualizedOpenAPIDeserializerKeepsV32() throws Exception {
        // @JsonUnwrapped forces the OpenAPI deserializer through unwrappingDeserializer
        // -> newDelegatingInstance; the 3.2 variant must keep stamping V32
        UnwrappedOpenAPI holder = Json32.mapper().readValue(
                "{\"openapi\":\"3.2.0\",\"info\":{\"title\":\"t\",\"version\":\"1\"}}",
                UnwrappedOpenAPI.class);
        assertNotNull(holder.api);
        assertEquals(holder.api.getSpecVersion(), SpecVersion.V32,
                "V32 must be preserved when the OpenAPI deserializer is re-contextualized");
    }

    public static class UnwrappedOpenAPI {
        @JsonUnwrapped
        public OpenAPI api;
    }

    // --- V32 routing observability --------------------------------------------
    // The *32Deserializer registrations look interchangeable with the *31 ones for
    // standard model shapes, so a plain round-trip cannot tell which one ran. The
    // observable difference is the ambient mapper each variant resolves through
    // specVersion()/mapper() for its inner conversions. A MapperCustomizer applied
    // only to MapperTarget.JSON32 installs a marker module that records every
    // instance produced by those inner conversions; a hit proves the 3.2 pipeline.

    private static final Set<Object> JSON32_ROUTED =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    /** Types produced only by inner conversions on the ambient mapper, never by the
     * registered deserializer itself. */
    private static final Set<Class<?>> INNER_CONVERSION_TYPES = Set.of(
            JsonSchema.class,
            QueryParameter.class, HeaderParameter.class, PathParameter.class, CookieParameter.class,
            ApiResponse.class, OAuthFlows.class, PathItem.class);

    private static final class Json32RoutingMarker extends DelegatingDeserializer {
        Json32RoutingMarker(ValueDeserializer<?> delegatee) {
            super(delegatee);
        }

        @Override
        protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
            return new Json32RoutingMarker(newDelegatee);
        }

        @Override
        public Object deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {
            Object result = super.deserialize(jp, ctxt);
            JSON32_ROUTED.add(result);
            return result;
        }
    }

    private static MapperCustomizer json32OnlyMarker() {
        return (builder, target) -> {
            if (target != MapperTarget.JSON32) {
                return;
            }
            SimpleModule marker = new SimpleModule("json32-routing-marker");
            marker.setDeserializerModifier(new ValueDeserializerModifier() {
                @Override
                public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                        BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
                    if (INNER_CONVERSION_TYPES.contains(beanDescRef.getBeanClass())) {
                        return new Json32RoutingMarker(deserializer);
                    }
                    return deserializer;
                }
            });
            builder.addModule(marker);
        };
    }

    @BeforeMethod
    @AfterMethod
    public void resetSharedMapperState() {
        ObjectMapperFactory.clearCustomizers();
        Json32.reset();
        Yaml32.reset();
        JSON32_ROUTED.clear();
    }

    @Test
    public void schemaRouteUsesJson32() throws Exception {
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        Schema schema = Json32.mapper().readValue(
                "{\"types\":[\"string\",\"null\"],\"$id\":\"https://example.com/s\"}", Schema.class);
        assertTrue(JSON32_ROUTED.contains(schema),
                "Schema inner conversion must run on the Json32 mapper (Model32Deserializer)");
    }

    @Test
    public void parameterRouteUsesJson32() throws Exception {
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        Parameter parameter = Json32.mapper().readValue(
                "{\"name\":\"limit\",\"in\":\"query\",\"schema\":{\"type\":\"string\"}}", Parameter.class);
        assertTrue(JSON32_ROUTED.contains(parameter),
                "parameter subtype binding must run on the Json32 mapper (Parameter32Deserializer)");
    }

    @Test
    public void apiResponsesRouteUsesJson32() throws Exception {
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        ApiResponses responses = Json32.mapper().readValue(
                "{\"200\":{\"description\":\"ok\"}}", ApiResponses.class);
        assertTrue(JSON32_ROUTED.contains(responses.get("200")),
                "inner ApiResponse conversion must run on the Json32 mapper (ApiResponses32Deserializer)");
    }

    @Test
    public void securitySchemeRouteUsesJson32() throws Exception {
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        SecurityScheme scheme = Json32.mapper().readValue(
                "{\"type\":\"oauth2\",\"flows\":{\"implicit\":{\"authorizationUrl\":\"https://example.com/auth\",\"scopes\":{}}}}",
                SecurityScheme.class);
        assertNotNull(scheme.getFlows());
        assertTrue(JSON32_ROUTED.contains(scheme.getFlows()),
                "OAuthFlows conversion must run on the Json32 mapper (SecurityScheme32Deserializer)");
    }

    @Test
    public void pathsRouteUsesJson32() throws Exception {
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        Paths paths = Json32.mapper().readValue(
                "{\"/pets\":{\"get\":{\"responses\":{\"200\":{\"description\":\"ok\"}}}}}", Paths.class);
        assertTrue(JSON32_ROUTED.contains(paths.get("/pets")),
                "inner PathItem conversion must run on the Json32 mapper (Paths32Deserializer)");
    }

    @Test
    public void callbackRouteUsesJson32() throws Exception {
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        Callback callback = Json32.mapper().readValue(
                "{\"onData\":{\"get\":{\"responses\":{\"200\":{\"description\":\"ok\"}}}}}", Callback.class);
        assertTrue(JSON32_ROUTED.contains(callback.get("onData")),
                "inner PathItem conversion must run on the Json32 mapper (Callback32Deserializer)");
    }

    @Test
    public void componentsSchemasRouteUsesJson32() throws Exception {
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        String doc = "{\"openapi\":\"3.2.0\",\"info\":{\"title\":\"t\",\"version\":\"1\"},"
                + "\"components\":{\"schemas\":{\"Pet\":{\"types\":[\"string\"]}}}}";
        OpenAPI readBack = Json32.mapper().readValue(doc, OpenAPI.class);
        Schema pet = readBack.getComponents().getSchemas().get("Pet");
        assertNotNull(pet);
        assertTrue(JSON32_ROUTED.contains(pet),
                "components.schemas values must be converted on the Json32 mapper (Model32Deserializer)");
    }
}
