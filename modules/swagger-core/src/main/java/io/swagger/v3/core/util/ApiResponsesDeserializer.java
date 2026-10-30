package io.swagger.v3.core.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import tools.jackson.databind.ValueDeserializer;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiResponsesDeserializer extends ValueDeserializer<ApiResponses> {

    /**
     * @deprecated kept for subclasses compiled against it; the version is read through
     * {@link #specVersion()} so that subclasses can also express versions newer than 3.1.
     */
    @Deprecated
    protected boolean openapi31;

    @Override
    public ApiResponses deserialize(JsonParser jp, DeserializationContext ctxt)
            throws JacksonException {

        final ObjectMapper mapper = mapper();
        ApiResponses result = new ApiResponses();
        JsonNode node = ctxt.readTree(jp);
        ObjectNode objectNode = (ObjectNode) node;
        Map<String, Object> extensions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
            String childName = entry.getKey();
            JsonNode child = entry.getValue();
            // if name start with `x-` consider it an extension
            if (childName.startsWith("x-")) {
                extensions.put(childName, mapper.convertValue(child, Object.class));
            } else {
                result.put(childName, mapper.convertValue(child, ApiResponse.class));
            }
        }
        if (!extensions.isEmpty()) {
            result.setExtensions(extensions);
        }
        return result;
    }

    /**
     * Returns the spec version this deserializer targets. "3.1 or later" semantics are
     * shared by 3.2; subclasses override this to support newer versions.
     */
    protected SpecVersion specVersion() {
        return openapi31 ? SpecVersion.V31 : SpecVersion.V30;
    }

    protected ObjectMapper mapper() {
        return SpecVersionMappers.mapper(specVersion());
    }
}
