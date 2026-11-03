package io.swagger.v3.core.util;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;

public class OpenAPI32Deserializer extends OpenAPI31Deserializer {

    public OpenAPI32Deserializer(ValueDeserializer<?> defaultDeserializer)
    {
        super(defaultDeserializer, SpecVersion.V32);
    }

    @Override
    protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
        return new OpenAPI32Deserializer(newDelegatee);
    }

    // readerForUpdating() reaches the delegate through this three-argument overload,
    // bypassing the two-argument deserialize() that stamps the spec version
    @Override
    public OpenAPI deserialize(JsonParser jp, DeserializationContext ctxt, Object intoValue)
            throws JacksonException {
        OpenAPI openAPI = (OpenAPI) super.deserialize(jp, ctxt, intoValue);
        openAPI.setSpecVersion(SpecVersion.V32);
        return openAPI;
    }
}
