package io.swagger.v3.core.util;

import tools.jackson.databind.ValueDeserializer;
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
}
