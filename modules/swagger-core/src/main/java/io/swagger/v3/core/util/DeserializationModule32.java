package io.swagger.v3.core.util;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Jackson module of the OpenAPI 3.2 mapper. Inherits the 3.1 registrations, then
 * un-gates the 3.2 additions: the 'cookie' parameter style serializer is
 * re-registered without the version check, the serializer modifier rejecting
 * {@code in: "querystring"} is cleared, and the deserializer modifier is
 * replaced so {@link Parameter.StyleEnum} accepts 'cookie' again.
 */
public class DeserializationModule32 extends DeserializationModule31 {

    public DeserializationModule32() {

        this.addDeserializer(Schema.class, new Model32Deserializer());
        this.addDeserializer(Parameter.class, new Parameter32Deserializer());
        this.addDeserializer(SecurityScheme.class, new SecurityScheme32Deserializer());
        this.addDeserializer(ApiResponses.class, new ApiResponses32Deserializer());
        this.addDeserializer(Paths.class, new Paths32Deserializer());
        this.addDeserializer(Callback.class, new Callback32Deserializer());

        // re-register ungated: the inherited 3.1 module registers a cookie-gated serializer
        this.addSerializer(Parameter.StyleEnum.class, new ParameterStyleSerializer(true));

        // clear the serializer modifier inherited from the 3.1 module, which
        // rejects QueryStringParameter; the 3.2 mapper must serialize it
        this.setSerializerModifier(null);

        this.setDeserializerModifier(new ValueDeserializerModifier()
        {
            @Override public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
                if (beanDescRef.getBeanClass() == OpenAPI.class) {
                    return new OpenAPI32Deserializer(deserializer);
                }
                return deserializer;
            }
        });
    }
}
