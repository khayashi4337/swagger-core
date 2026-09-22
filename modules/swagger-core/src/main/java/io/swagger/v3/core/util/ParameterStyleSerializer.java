package io.swagger.v3.core.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * Serializes {@link Parameter.StyleEnum} using its wire value, rejecting the
 * OpenAPI 3.2 'cookie' style on mappers targeting earlier spec versions so a
 * document cannot emit what the same mapper would refuse to read back.
 * It lives in {@code util} rather than the mixin package because it is a
 * version gate registered by {@link DeserializationModule},
 * {@link DeserializationModule31} and {@link DeserializationModule32}, not a
 * property-level annotation mixin.
 */
public class ParameterStyleSerializer extends StdSerializer<Parameter.StyleEnum> {

    private final boolean allowCookie;

    public ParameterStyleSerializer() {
        this(false);
    }

    public ParameterStyleSerializer(boolean allowCookie) {
        super(Parameter.StyleEnum.class);
        this.allowCookie = allowCookie;
    }

    @Override
    public void serialize(Parameter.StyleEnum value, JsonGenerator gen, SerializationContext provider)
            throws JacksonException {
        if (value == Parameter.StyleEnum.COOKIE && !allowCookie) {
            throw DatabindException.from(gen, "Parameter style 'cookie' requires OpenAPI 3.2");
        }
        gen.writeString(value.toString());
    }
}
