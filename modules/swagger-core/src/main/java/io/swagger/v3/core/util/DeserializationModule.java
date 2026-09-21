package io.swagger.v3.core.util;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.EncodingProperty;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryStringParameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Jackson module of the OpenAPI 3.0 mapper. Despite the name it also registers
 * serializers: the version gates for values added by newer specs (3.2 'cookie'
 * parameter style, 3.2 {@code in: "querystring"}) have to live here so this
 * mapper keeps rejecting them on write as well as on read.
 */
public class DeserializationModule extends SimpleModule {

    public DeserializationModule() {

        this.addDeserializer(Schema.class, new ModelDeserializer());
        this.addDeserializer(Parameter.class, new ParameterDeserializer());
        this.addDeserializer(Header.StyleEnum.class, new HeaderStyleEnumDeserializer());
        this.addDeserializer(Encoding.StyleEnum.class, new EncodingStyleEnumDeserializer());
        this.addDeserializer(EncodingProperty.StyleEnum.class, new EncodingPropertyStyleEnumDeserializer());

        this.addDeserializer(SecurityScheme.class, new SecuritySchemeDeserializer());

        this.addDeserializer(ApiResponses.class, new ApiResponsesDeserializer());
        this.addDeserializer(Paths.class, new PathsDeserializer());
        this.addDeserializer(Callback.class, new CallbackDeserializer());

        this.setDeserializerModifier(new ValueDeserializerModifier()
        {
            @Override public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
                if (deserializer != null && Parameter.class.isAssignableFrom(beanDescRef.getBeanClass())) {
                    // a direct read into a concrete subclass must not bind
                    // in: querystring either, like the base Parameter path
                    return new QueryStringParameterRejectingDeserializer(deserializer);
                }
                return deserializer;
            }

            @Override public ValueDeserializer<?> modifyEnumDeserializer(DeserializationConfig config, JavaType enumType, BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
                if (beanDescRef.getBeanClass() == Parameter.StyleEnum.class) {
                    return new ParameterStyleEnumDeserializer(deserializer);
                }
                return deserializer;
            }
        });

        this.setSerializerModifier(new ValueSerializerModifier()
        {
            @Override public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier beanDescRef, ValueSerializer<?> serializer) {
                Class<?> beanClass = beanDescRef.getBeanClass();
                if (QueryStringParameter.class.isAssignableFrom(beanClass)) {
                    return new QueryStringParameterSerializer();
                }
                if (Parameter.class.isAssignableFrom(beanClass)) {
                    // subclasses and generic Parameter processing must not emit
                    // in: querystring on a mapper that cannot read it back
                    return new QueryStringRejectingParameterSerializer(serializer);
                }
                return serializer;
            }
        });
    }
}
