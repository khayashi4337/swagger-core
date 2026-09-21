package io.swagger.v3.core.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.DelegatingDeserializer;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * Deserializes {@link Parameter.StyleEnum} through the standard enum deserializer,
 * then rejects the OpenAPI 3.2 'cookie' style on mappers targeting earlier spec
 * versions, restoring the failure the unknown enum value used to produce. This
 * mirrors {@link ParameterStyleSerializer}, which rejects the same value on write.
 */
public class ParameterStyleEnumDeserializer extends DelegatingDeserializer {

    public ParameterStyleEnumDeserializer(ValueDeserializer<?> defaultDeserializer) {
        super(defaultDeserializer);
    }

    @Override
    protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegatee) {
        return new ParameterStyleEnumDeserializer(newDelegatee);
    }

    @Override
    public Object deserialize(JsonParser jp, DeserializationContext ctxt)
            throws JacksonException {
        Object value = super.deserialize(jp, ctxt);
        if (value == Parameter.StyleEnum.COOKIE) {
            throw DatabindException.from(jp, "Parameter style 'cookie' requires OpenAPI 3.2");
        }
        return value;
    }
}
