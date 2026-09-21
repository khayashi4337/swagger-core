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
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.core.util.Yaml32;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.XML;
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.QueryStringParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
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

    // ---- simple fixed-field additions ----

    private OpenAPI buildFixedFields32Doc() {
        return new OpenAPI()
                .openapi("3.2.0")
                .info(new Info().title("t").version("1"))
                .addServersItem(new Server().url("https://api.example.com").name("production"))
                .addTagsItem(new Tag().name("pets").summary("Pet operations").parent("animals").kind("nav"))
                .paths(new Paths().addPathItem("/pets", new PathItem().get(
                        new Operation().operationId("getPets").responses(
                                new ApiResponses().addApiResponse("200",
                                        new ApiResponse().description("ok").summary("pet list")
                                                .content(new Content().addMediaType("application/json",
                                                        new MediaType().itemSchema(new Schema().typesItem("string")))))))))
                .components(new Components()
                        .addExamples("dataEx", new Example().summary("s").dataValue(java.util.Collections.singletonMap("a", 1)))
                        .addExamples("serEx", new Example().serializedValue("{\"a\":1}"))
                        .addSecuritySchemes("oauth", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .deprecated(true)
                                .oauth2MetadataUrl("https://auth.example.com/.well-known/oauth-authorization-server")
                                .flows(new OAuthFlows()
                                        .deviceAuthorization(
                                                new OAuthFlow().deviceAuthorizationUrl("https://auth.example.com/device")
                                                        .tokenUrl("https://auth.example.com/token"))
                                        // authorizationCode binds in all versions; it exercises the
                                        // OAuthFlow mixin hiding deviceAuthorizationUrl on its own
                                        .authorizationCode(
                                                new OAuthFlow().authorizationUrl("https://auth.example.com/authorize")
                                                        .tokenUrl("https://auth.example.com/token")
                                                        .deviceAuthorizationUrl("https://auth.example.com/device"))))
                        .addSchemas("Pet", new Schema()
                                .type("object")
                                .discriminator(new Discriminator().propertyName("petType").defaultMapping("Dog"))
                                .xml(new XML().nodeType("element"))));
    }

    @Test
    public void newFields32SerializeAndRoundTrip() throws Exception {
        String out = Json32.mapper().writeValueAsString(buildFixedFields32Doc());
        for (String token : new String[]{
                "\"name\":\"production\"",
                "\"summary\":\"Pet operations\"",
                "\"parent\":\"animals\"",
                "\"kind\":\"nav\"",
                "\"summary\":\"pet list\"",
                "\"itemSchema\":",
                "\"dataValue\":",
                "\"serializedValue\":\"{\\\"a\\\":1}\"",
                "\"deprecated\":true",
                "\"oauth2MetadataUrl\":",
                "\"deviceAuthorization\":",
                "\"deviceAuthorizationUrl\":",
                "\"defaultMapping\":\"Dog\"",
                "\"nodeType\":\"element\""}) {
            assertTrue(out.contains(token), "3.2 JSON output must contain " + token + " : " + out);
        }

        OpenAPI readBack = Json32.mapper().readValue(out, OpenAPI.class);
        assertEquals(readBack.getServers().get(0).getName(), "production");
        Tag tag = readBack.getTags().get(0);
        assertEquals(tag.getSummary(), "Pet operations");
        assertEquals(tag.getParent(), "animals");
        assertEquals(tag.getKind(), "nav");
        ApiResponse resp = readBack.getPaths().get("/pets").getGet().getResponses().get("200");
        assertEquals(resp.getSummary(), "pet list");
        // 3.1-family schema deserialization normalizes `type` into the `types` set
        assertTrue(resp.getContent().get("application/json").getItemSchema().getTypes().contains("string"));
        assertNotNull(readBack.getComponents().getExamples().get("dataEx").getDataValue());
        assertEquals(readBack.getComponents().getExamples().get("serEx").getSerializedValue(), "{\"a\":1}");
        SecurityScheme scheme = readBack.getComponents().getSecuritySchemes().get("oauth");
        assertEquals(scheme.getDeprecated(), Boolean.TRUE);
        assertNotNull(scheme.getOauth2MetadataUrl());
        assertEquals(scheme.getFlows().getDeviceAuthorization().getDeviceAuthorizationUrl(),
                "https://auth.example.com/device");
        assertEquals(scheme.getFlows().getAuthorizationCode().getDeviceAuthorizationUrl(),
                "https://auth.example.com/device");
        Schema pet = readBack.getComponents().getSchemas().get("Pet");
        assertEquals(pet.getDiscriminator().getDefaultMapping(), "Dog");
        assertEquals(pet.getXml().getNodeType(), "element");
    }

    @Test
    public void newFields32HiddenIn30And31() throws Exception {
        OpenAPI doc = buildFixedFields32Doc();
        for (tools.jackson.databind.ObjectMapper m :
                new tools.jackson.databind.ObjectMapper[]{Json.mapper(), Json31.mapper()}) {
            String out = m.writeValueAsString(doc);
            assertFalse(out.contains("production"), "must not emit Server.name: " + out);
            assertFalse(out.contains("Pet operations"), "must not emit Tag.summary: " + out);
            assertFalse(out.contains("animals"), "must not emit Tag.parent: " + out);
            assertFalse(out.contains("nav"), "must not emit Tag.kind: " + out);
            assertFalse(out.contains("pet list"), "must not emit Response.summary: " + out);
            assertFalse(out.contains("itemSchema"), "must not emit MediaType.itemSchema: " + out);
            assertFalse(out.contains("dataValue"), "must not emit Example.dataValue: " + out);
            assertFalse(out.contains("serializedValue"), "must not emit Example.serializedValue: " + out);
            assertFalse(out.contains("\"deprecated\":true"), "must not emit SecurityScheme.deprecated: " + out);
            assertFalse(out.contains("oauth2MetadataUrl"), "must not emit SecurityScheme.oauth2MetadataUrl: " + out);
            assertFalse(out.contains("deviceAuthorization"), "must not emit OAuthFlows.deviceAuthorization: " + out);
            assertFalse(out.contains("defaultMapping"), "must not emit Discriminator.defaultMapping: " + out);
            assertFalse(out.contains("nodeType"), "must not emit XML.nodeType: " + out);
        }
    }

    @Test
    public void newFields32IgnoredIn31Deserialization() throws Exception {
        String doc = "openapi: 3.1.0\n" +
                "info:\n  title: t\n  version: '1'\n" +
                "servers:\n  - url: https://api.example.com\n    name: production\n" +
                "tags:\n  - name: pets\n    summary: s\n    parent: animals\n    kind: nav\n" +
                "paths: {}\n" +
                "components:\n" +
                "  securitySchemes:\n" +
                "    oauth:\n" +
                "      type: oauth2\n" +
                "      deprecated: true\n" +
                "      oauth2MetadataUrl: https://auth.example.com/meta\n" +
                "      flows:\n" +
                "        deviceAuthorization:\n" +
                "          deviceAuthorizationUrl: https://auth.example.com/device\n" +
                "          tokenUrl: https://auth.example.com/token\n" +
                "        authorizationCode:\n" +
                "          authorizationUrl: https://auth.example.com/authorize\n" +
                "          tokenUrl: https://auth.example.com/token\n" +
                "          deviceAuthorizationUrl: https://auth.example.com/device\n";
        OpenAPI readBack = Yaml31.mapper().readValue(doc, OpenAPI.class);
        assertNull(readBack.getServers().get(0).getName(), "3.1 mapper must not bind Server.name");
        assertNull(readBack.getTags().get(0).getSummary(), "3.1 mapper must not bind Tag.summary");
        SecurityScheme scheme = readBack.getComponents().getSecuritySchemes().get("oauth");
        assertNull(scheme.getDeprecated(), "3.1 mapper must not bind SecurityScheme.deprecated");
        assertNull(scheme.getOauth2MetadataUrl(), "3.1 mapper must not bind SecurityScheme.oauth2MetadataUrl");
        assertNull(scheme.getFlows().getDeviceAuthorization(), "3.1 mapper must not bind flows.deviceAuthorization");
        assertNotNull(scheme.getFlows().getAuthorizationCode(), "authorizationCode itself must bind in 3.1");
        assertNull(scheme.getFlows().getAuthorizationCode().getDeviceAuthorizationUrl(),
                "3.1 mapper must not bind OAuthFlow.deviceAuthorizationUrl");
    }

    @Test
    public void newFields32IgnoredIn30Deserialization() throws Exception {
        // mirror of the 3.1 test: the 3.0 mapper must not bind the 3.2 fixed
        // fields either, including the Encoding Object additions
        String doc = "openapi: 3.0.4\n" +
                "info:\n  title: t\n  version: '1'\n" +
                "servers:\n  - url: https://api.example.com\n    name: production\n" +
                "tags:\n  - name: pets\n    summary: s\n    parent: animals\n    kind: nav\n" +
                "paths:\n  /pets:\n    post:\n" +
                "      requestBody:\n        content:\n" +
                "          multipart/form-data:\n            encoding:\n" +
                "              profile:\n                contentType: image/png\n" +
                "                encoding: identity\n                prefixEncoding: raw\n" +
                "                itemEncoding: chunked\n" +
                "      responses:\n        '200':\n          description: ok\n          summary: pet list\n" +
                "          content:\n            application/json:\n              schema:\n                type: object\n" +
                "components:\n" +
                "  securitySchemes:\n    oauth:\n      type: oauth2\n      deprecated: true\n" +
                "      oauth2MetadataUrl: https://auth.example.com/meta\n" +
                "      flows:\n        deviceAuthorization:\n" +
                "          deviceAuthorizationUrl: https://auth.example.com/device\n" +
                "          tokenUrl: https://auth.example.com/token\n";
        OpenAPI readBack = Yaml.mapper().readValue(doc, OpenAPI.class);
        assertNull(readBack.getServers().get(0).getName(), "3.0 mapper must not bind Server.name");
        assertNull(readBack.getTags().get(0).getSummary(), "3.0 mapper must not bind Tag.summary");
        assertNull(readBack.getTags().get(0).getParent(), "3.0 mapper must not bind Tag.parent");
        assertNull(readBack.getTags().get(0).getKind(), "3.0 mapper must not bind Tag.kind");

        Encoding encoding = readBack.getPaths().get("/pets").getPost().getRequestBody()
                .getContent().get("multipart/form-data").getEncoding().get("profile");
        assertNotNull(encoding, "encoding entry must bind");
        assertEquals(encoding.getContentType(), "image/png", "pre-3.2 Encoding fields still bind");
        assertNull(encoding.getEncoding(), "3.0 mapper must not bind Encoding.encoding");
        assertNull(encoding.getPrefixEncoding(), "3.0 mapper must not bind Encoding.prefixEncoding");
        assertNull(encoding.getItemEncoding(), "3.0 mapper must not bind Encoding.itemEncoding");

        ApiResponse response = readBack.getPaths().get("/pets").getPost().getResponses().get("200");
        assertNull(response.getSummary(), "3.0 mapper must not bind Response.summary");

        SecurityScheme scheme = readBack.getComponents().getSecuritySchemes().get("oauth");
        assertNull(scheme.getDeprecated(), "3.0 mapper must not bind SecurityScheme.deprecated");
        assertNull(scheme.getOauth2MetadataUrl(), "3.0 mapper must not bind SecurityScheme.oauth2MetadataUrl");
        assertNull(scheme.getFlows().getDeviceAuthorization(),
                "3.0 mapper must not bind flows.deviceAuthorization");
    }

    @Test
    public void exampleNullDataValueSerializes() throws Exception {
        // dataValue of type 'any' may legitimately be null: the set-flag mirrors valueSetFlag
        OpenAPI doc = buildDoc();
        doc.setComponents(new Components().addExamples("nullEx", new Example().dataValue(null)));
        String out = Json32.mapper().writeValueAsString(doc);
        assertTrue(out.contains("\"dataValue\":null"), "explicit null dataValue must serialize: " + out);

        // the explicit-null write path must not leak the field to earlier versions either
        String out30 = Json.mapper().writeValueAsString(doc);
        assertFalse(out30.contains("dataValue"), "3.0 must not emit dataValue even when set-flag is on: " + out30);
        String out31 = Json31.mapper().writeValueAsString(doc);
        assertFalse(out31.contains("dataValue"), "3.1 must not emit dataValue even when set-flag is on: " + out31);
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
            QueryStringParameter.class,
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
    public void querystringParameterRouteUsesJson32() throws Exception {
        // like parameterRouteUsesJson32, but through the V32-only 'querystring' branch:
        // if Parameter31Deserializer handled this input the 'in' dispatch would fall
        // through and return null, and binding on any other ambient mapper would not
        // be recorded by the Json32 marker
        ObjectMapperFactory.addCustomizer(json32OnlyMarker());
        Parameter parameter = Json32.mapper().readValue(
                "{\"name\":\"queryString\",\"in\":\"querystring\"," +
                        "\"content\":{\"application/x-www-form-urlencoded\":{\"schema\":{\"type\":\"object\"}}}}",
                Parameter.class);
        assertTrue(parameter instanceof QueryStringParameter);
        assertTrue(JSON32_ROUTED.contains(parameter),
                "querystring subtype binding must run on the Json32 mapper (Parameter32Deserializer)");
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

    @Test
    public void discriminatorExtensionsSurviveConverter() {
        // the converter mapper feeds the 3.0 -> 3.1 conversion; 3.1 allows
        // Discriminator extensions, so x- keys must survive into the converted map
        Schema<?> schema = new Schema<>()
                .type("object")
                .discriminator(new Discriminator().propertyName("petType").defaultMapping("Dog"));
        schema.getDiscriminator().addExtension("x-pet-kind", "animal");
        Map<String, Object> converted = io.swagger.v3.core.util.ObjectMapperFactory.createJsonConverter()
                .convertValue(schema, Map.class);
        Object discriminator = converted.get("discriminator");
        assertTrue(discriminator instanceof Map, "converted schema must contain a discriminator map: " + converted);
        Map<?, ?> discriminatorMap = (Map<?, ?>) discriminator;
        assertEquals(discriminatorMap.get("x-pet-kind"), "animal",
                "converter must keep discriminator x- extensions as flattened keys");
        assertFalse(discriminatorMap.containsKey("defaultMapping"),
                "converter must not emit the 3.2 defaultMapping field");
        assertFalse(discriminatorMap.containsKey("extensions"),
                "extensions must be flattened, not nested under an 'extensions' key");
    }

    @Test
    public void selfSerializesIn32() throws Exception {
        OpenAPI doc = buildDoc().$self("https://example.com/openapi.yaml");
        String out = Json32.mapper().writeValueAsString(doc);
        assertTrue(out.contains("\"$self\":\"https://example.com/openapi.yaml\""),
                "3.2 JSON output should contain '$self': " + out);
        String yaml = Yaml32.mapper().writeValueAsString(doc);
        assertTrue(yaml.contains("$self: \"https://example.com/openapi.yaml\"") ||
                        yaml.contains("$self: https://example.com/openapi.yaml"),
                "3.2 YAML output should contain '$self': " + yaml);
    }

    @Test
    public void selfHiddenIn30And31() throws Exception {
        OpenAPI doc = buildDoc().$self("https://example.com/openapi.yaml");
        String out30 = Json.mapper().writeValueAsString(doc);
        assertFalse(out30.contains("$self"), "3.0 output must not contain '$self': " + out30);
        String out31 = Json31.mapper().writeValueAsString(doc);
        assertFalse(out31.contains("$self"), "3.1 output must not contain '$self': " + out31);
        String out30Yaml = io.swagger.v3.core.util.Yaml.mapper().writeValueAsString(doc);
        assertFalse(out30Yaml.contains("$self"), "3.0 YAML output must not contain '$self': " + out30Yaml);
    }

    @Test
    public void selfRoundTrip32() throws Exception {
        OpenAPI doc = buildDoc().$self("https://example.com/openapi.yaml");
        String out = Json32.mapper().writeValueAsString(doc);
        OpenAPI readBack = Json32.mapper().readValue(out, OpenAPI.class);
        assertEquals(readBack.get$self(), "https://example.com/openapi.yaml");

        String yaml = Yaml32.mapper().writeValueAsString(doc);
        OpenAPI readBackYaml = Yaml32.mapper().readValue(yaml, OpenAPI.class);
        assertEquals(readBackYaml.get$self(), "https://example.com/openapi.yaml");
    }

    @Test
    public void selfIgnoredIn30And31Deserialization() throws Exception {
        String doc = "openapi: 3.1.0\n" +
                "$self: https://example.com/openapi.yaml\n" +
                "info:\n" +
                "  title: t\n" +
                "  version: '1'\n" +
                "paths: {}\n";
        OpenAPI read31 = Yaml31.mapper().readValue(doc, OpenAPI.class);
        assertNull(read31.get$self(), "3.1 mapper must not bind $self");

        String doc30 = doc.replace("3.1.0", "3.0.3");
        OpenAPI read30 = io.swagger.v3.core.util.Yaml.mapper().readValue(doc30, OpenAPI.class);
        assertNull(read30.get$self(), "3.0 mapper must not bind $self");
    }

    @Test
    public void selfHiddenInConverterMapper() throws Exception {
        // the V30-style converter mapper shares OpenAPIMixin and must not emit $self either
        OpenAPI doc = buildDoc().$self("https://example.com/openapi.yaml");
        String out = io.swagger.v3.core.util.ObjectMapperFactory.createJsonConverter()
                .writeValueAsString(doc);
        assertFalse(out.contains("$self"), "converter mapper must not emit '$self': " + out);
    }

    @Test
    public void selfSurvivesSpecFilter() {
        // SpecFilter copies root fields onto a fresh OpenAPI; $self must be carried over
        OpenAPI doc = buildDoc().$self("https://example.com/openapi.yaml");
        OpenAPI filtered = new SpecFilter().filter(doc, new NoOpOperationsFilter(), null, null, null);
        assertEquals(filtered.get$self(), "https://example.com/openapi.yaml",
                "SpecFilter must preserve $self");
    }

    @Test
    public void querystringParameterBinds32() throws Exception {
        String doc = "openapi: 3.2.0\n" +
                "info:\n" +
                "  title: t\n" +
                "  version: '1'\n" +
                "paths:\n" +
                "  /pets:\n" +
                "    get:\n" +
                "      parameters:\n" +
                "        - name: queryString\n" +
                "          in: querystring\n" +
                "          content:\n" +
                "            application/x-www-form-urlencoded:\n" +
                "              schema:\n" +
                "                type: object\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: ok\n";
        OpenAPI readBack = Yaml32.mapper().readValue(doc, OpenAPI.class);
        Parameter p = readBack.getPaths().get("/pets").getGet().getParameters().get(0);
        assertTrue(p instanceof io.swagger.v3.oas.models.parameters.QueryStringParameter,
                "in: querystring must bind to QueryStringParameter, got " + p.getClass());
        assertEquals(p.getIn(), "querystring");
        assertEquals(p.getName(), "queryString");
        assertNotNull(p.getContent(), "querystring parameter must keep its content map");
        MediaType mt = p.getContent().get("application/x-www-form-urlencoded");
        assertNotNull(mt);
        assertNotNull(mt.getSchema(), "media type schema must bind");
        assertTrue(mt.getSchema().getTypes().contains("object"),
                "schema types must contain 'object': " + mt.getSchema().getTypes());
    }

    @Test
    public void querystringParameterRoundTrip32() throws Exception {
        Parameter qs = new io.swagger.v3.oas.models.parameters.QueryStringParameter()
                .name("queryString")
                .content(new Content().addMediaType("application/x-www-form-urlencoded",
                        new MediaType().schema(new Schema())));
        String out = Json32.mapper().writeValueAsString(qs);
        assertTrue(out.contains("\"in\":\"querystring\""), "must serialize in: querystring: " + out);
        Parameter readBack = Json32.mapper().readValue(out, Parameter.class);
        assertTrue(readBack instanceof io.swagger.v3.oas.models.parameters.QueryStringParameter);
        assertEquals(readBack.getIn(), "querystring");
        assertEquals(readBack.getName(), "queryString");
        assertNotNull(readBack.getContent(), "round-trip must keep content");
        assertNotNull(readBack.getContent().get("application/x-www-form-urlencoded").getSchema());
    }

    @Test
    public void querystringNotBoundIn30And31() throws Exception {
        String json = "{\"name\":\"queryString\",\"in\":\"querystring\"," +
                "\"content\":{\"application/x-www-form-urlencoded\":{\"schema\":{\"type\":\"object\"}}}}";
        assertNull(Yaml31.mapper().readValue(
                "name: queryString\nin: querystring\ncontent:\n  application/x-www-form-urlencoded:\n    schema:\n      type: object\n",
                Parameter.class), "3.1 must not bind in: querystring");
        assertNull(Json.mapper().readValue(json, Parameter.class),
                "3.0 must not bind in: querystring");
    }

    @Test
    public void cookieStyleRoundTrip32() throws Exception {
        Parameter cookie = new io.swagger.v3.oas.models.parameters.CookieParameter()
                .name("sessionId")
                .style(Parameter.StyleEnum.COOKIE);
        String out = Json32.mapper().writeValueAsString(cookie);
        assertTrue(out.contains("\"style\":\"cookie\""), "must serialize style: cookie: " + out);
        Parameter readBack = Json32.mapper().readValue(out, Parameter.class);
        assertEquals(readBack.getStyle(), Parameter.StyleEnum.COOKIE);
    }

    @Test
    public void querystringAnnotationEnum() {
        assertEquals(io.swagger.v3.oas.annotations.enums.ParameterIn.QUERYSTRING.toString(),
                "querystring");
    }

    @Test
    public void cookieStyleRejectedIn30And31() {
        // style: cookie is a 3.2 addition; earlier versions must fail as they did
        // when 'cookie' was an unknown enum value
        String json = "{\"name\":\"sessionId\",\"in\":\"cookie\",\"style\":\"cookie\"}";
        try {
            Json.mapper().readValue(json, Parameter.class);
            org.testng.Assert.fail("3.0 must reject style: cookie");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("cookie"), expected.getMessage());
        }
        try {
            Json31.mapper().readValue(json, Parameter.class);
            org.testng.Assert.fail("3.1 must reject style: cookie");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("cookie"), expected.getMessage());
        }
    }

    @Test
    public void cookieStyleRejectedOnDirectTypeAndEnumReads() throws Exception {
        // reading a concrete parameter type or the style enum directly must not
        // bypass the version gate that the Parameter.class deserializer applies
        String json = "{\"name\":\"sessionId\",\"in\":\"cookie\",\"style\":\"COOKIE\"}";
        try {
            Json.mapper().readValue(json, io.swagger.v3.oas.models.parameters.CookieParameter.class);
            org.testng.Assert.fail("3.0 must reject style: cookie on a concrete parameter type");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("cookie"), expected.getMessage());
        }
        try {
            Json31.mapper().readValue(json, io.swagger.v3.oas.models.parameters.CookieParameter.class);
            org.testng.Assert.fail("3.1 must reject style: cookie on a concrete parameter type");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("cookie"), expected.getMessage());
        }
        // a direct enum read is rejected too, including via the wire value and
        // READ_ENUMS_USING_TO_STRING used by the Parameter deserializer
        for (String styleJson : new String[]{"\"COOKIE\"", "\"cookie\""}) {
            try {
                Json.mapper().readValue(styleJson, Parameter.StyleEnum.class);
                org.testng.Assert.fail("3.0 must reject cookie style on a direct enum read");
            } catch (Exception expected) {
                assertTrue(expected.getMessage().contains("cookie"), expected.getMessage());
            }
            try {
                Json31.mapper().readerFor(Parameter.StyleEnum.class)
                        .with(tools.jackson.databind.cfg.EnumFeature.READ_ENUMS_USING_TO_STRING)
                        .readValue(styleJson);
                org.testng.Assert.fail("3.1 must reject cookie style on a direct enum read");
            } catch (Exception expected) {
                assertTrue(expected.getMessage().contains("cookie"), expected.getMessage());
            }
        }
        // non-cookie styles still bind on the old mappers
        assertEquals(Json.mapper().readValue("\"FORM\"", Parameter.StyleEnum.class), Parameter.StyleEnum.FORM);
        assertEquals(Json.mapper().readValue("{\"name\":\"sessionId\",\"in\":\"cookie\",\"style\":\"FORM\"}",
                        io.swagger.v3.oas.models.parameters.CookieParameter.class).getStyle(),
                Parameter.StyleEnum.FORM);
        // the 3.2 mapper accepts all of them
        assertEquals(Json32.mapper().readValue(json, io.swagger.v3.oas.models.parameters.CookieParameter.class)
                .getStyle(), Parameter.StyleEnum.COOKIE);
        assertEquals(Json32.mapper().readValue("\"COOKIE\"", Parameter.StyleEnum.class), Parameter.StyleEnum.COOKIE);
        assertEquals(Json32.mapper().readerFor(Parameter.StyleEnum.class)
                        .with(tools.jackson.databind.cfg.EnumFeature.READ_ENUMS_USING_TO_STRING)
                        .readValue("\"cookie\""),
                Parameter.StyleEnum.COOKIE);
    }

    @Test
    public void querystringParameterNotSerializedIn30And31() throws Exception {
        // the same mapper must not emit an in: querystring document it would
        // refuse to read back, like the existing cookie-style write gate
        Parameter qs = new io.swagger.v3.oas.models.parameters.QueryStringParameter()
                .name("queryString")
                .content(new Content().addMediaType("application/x-www-form-urlencoded",
                        new MediaType().schema(new Schema())));
        try {
            Json.mapper().writeValueAsString(qs);
            org.testng.Assert.fail("3.0 must not serialize in: querystring");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("querystring"), expected.getMessage());
        }
        try {
            Json31.mapper().writeValueAsString(qs);
            org.testng.Assert.fail("3.1 must not serialize in: querystring");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("querystring"), expected.getMessage());
        }
        try {
            Yaml31.mapper().writeValueAsString(qs);
            org.testng.Assert.fail("3.1 YAML must not serialize in: querystring");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("querystring"), expected.getMessage());
        }
        String out = Json32.mapper().writeValueAsString(qs);
        assertTrue(out.contains("\"in\":\"querystring\""), "3.2 must serialize it: " + out);
    }

    public static class CustomQueryStringParameter extends QueryStringParameter {
    }

    @Test
    public void querystringRejectedThroughSubclassesAndGenericParameter() throws Exception {
        // the type-based gate alone only fires for the exact QueryStringParameter
        // class; subclasses and any other Parameter carrying in: querystring must
        // be refused on pre-3.2 mappers as well, and accepted on 3.2
        Parameter sub = new CustomQueryStringParameter()
                .name("queryString")
                .content(new Content().addMediaType("application/x-www-form-urlencoded",
                        new MediaType().schema(new Schema())));
        Parameter generic = new QueryParameter()
                .name("queryString")
                .in("querystring")
                .schema(new Schema());
        for (Parameter value : new Parameter[]{sub, generic}) {
            for (tools.jackson.databind.ObjectMapper mapper : new tools.jackson.databind.ObjectMapper[]{
                    Json.mapper(), Json31.mapper(), Yaml.mapper(), Yaml31.mapper()}) {
                tools.jackson.databind.DatabindException expected = org.testng.Assert.expectThrows(
                        tools.jackson.databind.DatabindException.class,
                        () -> mapper.writeValueAsString(value));
                assertTrue(expected.getMessage().contains("querystring"), expected.getMessage());
            }
            // 3.2 mappers accept both routes
            assertTrue(Json32.mapper().writeValueAsString(value).contains("\"in\":\"querystring\""));
            assertTrue(Yaml32.mapper().writeValueAsString(value).contains("querystring"));
        }

        // a direct read into a concrete subclass must not bind in: querystring
        // either; the base-type path leaves it unbound, so the subclass read
        // must come back null on pre-3.2 mappers and bound on 3.2
        String json = "{\"name\":\"queryString\",\"in\":\"querystring\","
                + "\"content\":{\"application/x-www-form-urlencoded\":{\"schema\":{\"type\":\"object\"}}}}";
        assertNull(Json.mapper().readValue(json, QueryParameter.class),
                "3.0 must not bind in: querystring into a subclass read");
        assertNull(Json31.mapper().readValue(json, CustomQueryStringParameter.class),
                "3.1 must not bind in: querystring into a subclass read");
        Parameter bound = Json32.mapper().readValue(json, QueryParameter.class);
        assertEquals(bound.getIn(), "querystring", "3.2 must bind it");
    }

    private MediaType buildMultipartMediaType() {
        Encoding inner = new Encoding().contentType("text/plain");
        Encoding nested = new Encoding()
                .contentType("application/json")
                .addEncoding("prop", new Encoding().contentType("text/plain"))
                .addPrefixEncoding(new Encoding().contentType("image/png"))
                .itemEncoding(new Encoding().contentType("text/csv"));
        return new MediaType()
                .schema(new Schema())
                .addPrefixEncoding(inner)
                .itemEncoding(new Encoding().contentType("application/octet-stream"))
                .addEncoding("named", nested);
    }

    @Test
    public void positionalEncodingSerializes32() throws Exception {
        MediaType mt = buildMultipartMediaType();
        String out = Json32.mapper().writeValueAsString(mt);
        assertTrue(out.contains("\"prefixEncoding\":"), "3.2 must emit prefixEncoding: " + out);
        assertTrue(out.contains("\"itemEncoding\":"), "3.2 must emit itemEncoding: " + out);
        // pin the nested Encoding.encoding property name at /encoding/named/encoding/prop
        tools.jackson.databind.JsonNode tree = Json32.mapper().readTree(out);
        assertEquals(tree.at("/encoding/named/encoding/prop/contentType").asText(), "text/plain",
                "nested Encoding.encoding must serialize as 'encoding': " + out);
        assertEquals(tree.at("/encoding/named/prefixEncoding/0/contentType").asText(), "image/png",
                "nested Encoding.prefixEncoding must serialize: " + out);
        assertEquals(tree.at("/encoding/named/itemEncoding/contentType").asText(), "text/csv",
                "nested Encoding.itemEncoding must serialize: " + out);
        String yaml = Yaml32.mapper().writeValueAsString(mt);
        assertTrue(yaml.contains("prefixEncoding:"), "3.2 YAML must emit prefixEncoding: " + yaml);
        assertTrue(yaml.contains("itemEncoding:"), "3.2 YAML must emit itemEncoding: " + yaml);
    }

    @Test
    public void positionalEncodingRoundTrip32() throws Exception {
        MediaType mt = buildMultipartMediaType();
        String out = Json32.mapper().writeValueAsString(mt);
        MediaType readBack = Json32.mapper().readValue(out, MediaType.class);
        assertNotNull(readBack.getPrefixEncoding());
        assertEquals(readBack.getPrefixEncoding().size(), 1);
        assertEquals(readBack.getPrefixEncoding().get(0).getContentType(), "text/plain");
        assertNotNull(readBack.getItemEncoding());
        assertEquals(readBack.getItemEncoding().getContentType(), "application/octet-stream");
        Encoding named = readBack.getEncoding().get("named");
        assertNotNull(named.getEncoding(), "nested Encoding.encoding map must bind");
        assertEquals(named.getEncoding().get("prop").getContentType(), "text/plain");
        assertEquals(named.getPrefixEncoding().get(0).getContentType(), "image/png",
                "nested Encoding.prefixEncoding must bind");
        assertEquals(named.getItemEncoding().getContentType(), "text/csv",
                "nested Encoding.itemEncoding must bind");
    }

    @Test
    public void positionalEncodingBindsFromDocument32() throws Exception {
        // fixed input pins the wire property names, independent of the serializer
        String doc = "encoding:\n" +
                "  named:\n" +
                "    contentType: application/json\n" +
                "    encoding:\n" +
                "      prop:\n" +
                "        contentType: text/plain\n" +
                "    prefixEncoding:\n" +
                "      - contentType: image/png\n" +
                "    itemEncoding:\n" +
                "      contentType: text/csv\n";
        MediaType readBack = Yaml32.mapper().readValue(doc, MediaType.class);
        Encoding named = readBack.getEncoding().get("named");
        assertNotNull(named);
        assertEquals(named.getEncoding().get("prop").getContentType(), "text/plain");
        assertEquals(named.getPrefixEncoding().get(0).getContentType(), "image/png");
        assertEquals(named.getItemEncoding().getContentType(), "text/csv");
    }

    @Test
    public void positionalEncodingHiddenIn30And31() throws Exception {
        MediaType mt = buildMultipartMediaType();
        String out30 = Json.mapper().writeValueAsString(mt);
        assertFalse(out30.contains("prefixEncoding"), "3.0 must not emit prefixEncoding: " + out30);
        assertFalse(out30.contains("itemEncoding"), "3.0 must not emit itemEncoding: " + out30);
        // nested Encoding.encoding is a 3.2 field too, so a 3.0 write drops it
        assertFalse(out30.contains("\"prop\""), "3.0 must not emit nested encoding: " + out30);
        // the pre-existing MediaType.encoding map itself must still serialize
        assertTrue(out30.contains("\"named\""), "3.0 must keep MediaType.encoding: " + out30);
        assertTrue(out30.contains("\"contentType\":\"application/json\""),
                "3.0 must keep non-3.2 Encoding fields: " + out30);
        String out31 = Json31.mapper().writeValueAsString(mt);
        assertFalse(out31.contains("prefixEncoding"), "3.1 must not emit prefixEncoding: " + out31);
        assertFalse(out31.contains("itemEncoding"), "3.1 must not emit itemEncoding: " + out31);
        assertFalse(out31.contains("\"prop\""), "3.1 must not emit nested encoding: " + out31);
        assertTrue(out31.contains("\"named\""), "3.1 must keep MediaType.encoding: " + out31);
    }

    @Test
    public void positionalEncodingNotBoundIn31() throws Exception {
        String doc = "schema:\n" +
                "  type: object\n" +
                "prefixEncoding:\n" +
                "  - contentType: text/plain\n" +
                "itemEncoding:\n" +
                "  contentType: application/octet-stream\n" +
                "encoding:\n" +
                "  named:\n" +
                "    contentType: application/json\n" +
                "    encoding:\n" +
                "      prop:\n" +
                "        contentType: text/plain\n" +
                "    prefixEncoding:\n" +
                "      - contentType: image/png\n" +
                "    itemEncoding:\n" +
                "      contentType: text/csv\n";
        MediaType read31 = Yaml31.mapper().readValue(doc, MediaType.class);
        assertNull(read31.getPrefixEncoding(), "3.1 must not bind prefixEncoding");
        assertNull(read31.getItemEncoding(), "3.1 must not bind itemEncoding");
        Encoding named31 = read31.getEncoding().get("named");
        assertNotNull(named31, "3.1 must still bind the MediaType.encoding entry itself");
        assertEquals(named31.getContentType(), "application/json");
        assertNull(named31.getEncoding(), "3.1 must not bind nested Encoding.encoding");
        assertNull(named31.getPrefixEncoding(), "3.1 must not bind nested Encoding.prefixEncoding");
        assertNull(named31.getItemEncoding(), "3.1 must not bind nested Encoding.itemEncoding");
    }

    @Test
    public void positionalEncodingHiddenInConverter() throws Exception {
        MediaType mt = buildMultipartMediaType();
        String out = io.swagger.v3.core.util.ObjectMapperFactory.createJsonConverter()
                .writeValueAsString(mt);
        assertFalse(out.contains("prefixEncoding"), "converter must not emit prefixEncoding: " + out);
        assertFalse(out.contains("itemEncoding"), "converter must not emit itemEncoding: " + out);
        // the V30 encoding map itself stays, but the nested 3.2 fields must be gone
        tools.jackson.databind.JsonNode tree =
                io.swagger.v3.core.util.ObjectMapperFactory.createJsonConverter().readTree(out);
        assertTrue(tree.at("/encoding/named").isContainer() || !tree.at("/encoding/named").isMissingNode(),
                "converter must keep the MediaType.encoding entry: " + out);
        assertTrue(tree.at("/encoding/named/encoding").isMissingNode(),
                "converter must drop nested Encoding.encoding: " + out);
    }
}
