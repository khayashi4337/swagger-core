package io.swagger.v3.core.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * Delegating {@link Parameter} serializer that rejects the OpenAPI 3.2
 * {@code in: "querystring"} value on mappers targeting earlier spec versions,
 * complementing {@link QueryStringParameterSerializer}: the type-based gate
 * only fires for the exact {@code QueryStringParameter} bean class, so a
 * subclass, or any {@code Parameter} carrying {@code in: "querystring"}, could
 * otherwise emit a document the same mapper would refuse to read back.
 */
public class QueryStringRejectingParameterSerializer extends StdSerializer<Parameter> {

    private final ValueSerializer<Object> delegate;

    @SuppressWarnings("unchecked")
    public QueryStringRejectingParameterSerializer(ValueSerializer<?> delegate) {
        super(Parameter.class);
        this.delegate = (ValueSerializer<Object>) delegate;
    }

    @Override
    public void resolve(SerializationContext ctxt) {
        // the modifier may wrap a bean serializer before it is resolved;
        // forward resolution so the delegate's property serializers (e.g. the
        // map serializer behind "extensions") are initialized
        delegate.resolve(ctxt);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        ValueSerializer<?> resolved = delegate.createContextual(ctxt, property);
        return resolved == delegate ? this : new QueryStringRejectingParameterSerializer(resolved);
    }

    @Override
    public void serialize(Parameter value, JsonGenerator gen, SerializationContext provider)
            throws JacksonException {
        if ("querystring".equals(value.getIn())) {
            throw DatabindException.from(gen, "Parameter 'querystring' requires OpenAPI 3.2");
        }
        delegate.serialize(value, gen, provider);
    }
}
