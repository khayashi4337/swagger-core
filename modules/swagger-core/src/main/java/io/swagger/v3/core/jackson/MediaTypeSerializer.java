package io.swagger.v3.core.jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import io.swagger.v3.core.util.StringUtils;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.MediaType;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class MediaTypeSerializer extends ValueSerializer<MediaType> {

    private ValueSerializer<Object> defaultSerializer;
    private SpecVersion specVersion;

    public MediaTypeSerializer(ValueSerializer<Object> serializer) {
        this(serializer, SpecVersion.V30);
    }

    public MediaTypeSerializer(ValueSerializer<Object> serializer, SpecVersion specVersion) {
        defaultSerializer = serializer;
        this.specVersion = specVersion;
    }

    @Override
    public void resolve(SerializationContext serializerProvider) throws DatabindException {
        defaultSerializer.resolve(serializerProvider);
    }

    @Override
    public void serialize(
            MediaType value, JsonGenerator jgen, SerializationContext provider)
            throws JacksonException {

        if (SpecVersion.V32.equals(specVersion) && StringUtils.isNotBlank(value.get$ref())) {
            // a $ref value in a content/mediaTypes map is a Reference Object:
            // sibling media type fields are ignored per spec
            jgen.writeStartObject();
            jgen.writeStringProperty("$ref", value.get$ref());
            jgen.writeEndObject();
            return;
        }
        if (value.getExampleSetFlag() && value.getExample() == null) {
            jgen.writeStartObject();
            defaultSerializer.unwrappingSerializer(null).serialize(value, jgen, provider);
            jgen.writeNullProperty("example");
            jgen.writeEndObject();
        } else {
            defaultSerializer.serialize(value, jgen, provider);
        }
    }
}
