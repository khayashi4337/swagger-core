package io.swagger.v3.core.jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.examples.Example;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class ExampleSerializer extends ValueSerializer<Example> {

    private ValueSerializer<Object> defaultSerializer;
    private final SpecVersion specVersion;

    public ExampleSerializer(ValueSerializer<Object> serializer) {
        this(serializer, SpecVersion.V30);
    }

    public ExampleSerializer(ValueSerializer<Object> serializer, SpecVersion specVersion) {
        defaultSerializer = serializer;
        this.specVersion = specVersion;
    }

    @Override
    public void resolve(SerializationContext serializerProvider) throws DatabindException {
        defaultSerializer.resolve(serializerProvider);
    }

    @Override
    public void serialize(
            Example example, JsonGenerator jgen, SerializationContext provider)
            throws JacksonException {

            if (example.getValueSetFlag() && example.getValue() == null) {
                jgen.writeStartObject();
                defaultSerializer.unwrappingSerializer(null).serialize(example, jgen, provider);
                jgen.writeNullProperty("value");
                jgen.writeEndObject();
            } else if (specVersion == SpecVersion.V32 && example.getDataValueSetFlag() && example.getDataValue() == null) {
                jgen.writeStartObject();
                defaultSerializer.unwrappingSerializer(null).serialize(example, jgen, provider);
                jgen.writeNullProperty("dataValue");
                jgen.writeEndObject();
            } else {
                defaultSerializer.serialize(example, jgen, provider);
            }
    }
}
