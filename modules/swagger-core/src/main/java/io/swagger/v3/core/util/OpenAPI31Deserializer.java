package io.swagger.v3.core.util;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.DelegatingDeserializer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ValueDeserializer;

public class OpenAPI31Deserializer extends DelegatingDeserializer {

    private final SpecVersion specVersion;

    public OpenAPI31Deserializer(ValueDeserializer<?> defaultDeserializer)
    {
        this(defaultDeserializer, SpecVersion.V31);
    }

    protected OpenAPI31Deserializer(ValueDeserializer<?> defaultDeserializer, SpecVersion specVersion)
    {
        super(defaultDeserializer);
        this.specVersion = specVersion;
    }

    @Override
    protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
        return new OpenAPI31Deserializer(newDelegatee, specVersion);
    }

    @Override
    public OpenAPI deserialize(JsonParser jp, DeserializationContext ctxt)
            throws JacksonException {
        OpenAPI openAPI = (OpenAPI) super.deserialize(jp, ctxt);
        openAPI.setSpecVersion(specVersion);
        return openAPI;
    }
}

