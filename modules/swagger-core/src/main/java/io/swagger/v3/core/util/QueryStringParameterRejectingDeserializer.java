package io.swagger.v3.core.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.std.StdDeserializer;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * Delegating deserializer that refuses to bind {@code in: "querystring"} on
 * mappers targeting spec versions earlier than OpenAPI 3.2. The version gate
 * in {@code ParameterDeserializer} only applies to {@code Parameter.class}
 * reads, so a direct read into a concrete subclass could otherwise bind the
 * 3.2-only value. Mirroring the base-type path, the value is not bound at all.
 *
 * <p>Extends {@link StdDeserializer} rather than the delegating base class:
 * the delegate may be a registered deserializer without a declared handled
 * type, which the delegating base would reject.
 */
public class QueryStringParameterRejectingDeserializer extends StdDeserializer<Parameter> {

    private final ValueDeserializer<Object> delegate;

    @SuppressWarnings("unchecked")
    public QueryStringParameterRejectingDeserializer(ValueDeserializer<?> delegate) {
        super(Parameter.class);
        this.delegate = (ValueDeserializer<Object>) delegate;
    }

    @Override
    public void resolve(DeserializationContext ctxt) {
        // the modifier may wrap a bean deserializer before it is resolved;
        // forward resolution so the delegate's property matcher is initialized
        delegate.resolve(ctxt);
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        ValueDeserializer<?> resolved = delegate.createContextual(ctxt, property);
        return resolved == delegate ? this : new QueryStringParameterRejectingDeserializer(resolved);
    }

    @Override
    public Parameter deserialize(JsonParser jp, DeserializationContext ctxt)
            throws JacksonException {
        Object value = delegate.deserialize(jp, ctxt);
        if (value instanceof Parameter && "querystring".equals(((Parameter) value).getIn())) {
            return null;
        }
        return (Parameter) value;
    }
}
