package io.swagger.v3.core.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;
import io.swagger.v3.oas.models.parameters.QueryStringParameter;

/**
 * Rejects {@link QueryStringParameter} on mappers targeting spec versions earlier
 * than OpenAPI 3.2: the 3.0/3.1 mappers must not emit an {@code in: "querystring"}
 * document they would refuse to read back. Same approach as
 * {@link ParameterStyleSerializer}, which rejects the 3.2 'cookie' style on write.
 */
public class QueryStringParameterSerializer extends StdSerializer<QueryStringParameter> {

    public QueryStringParameterSerializer() {
        super(QueryStringParameter.class);
    }

    @Override
    public void serialize(QueryStringParameter value, JsonGenerator gen, SerializationContext provider)
            throws JacksonException {
        throw DatabindException.from(gen, "Parameter 'querystring' requires OpenAPI 3.2");
    }
}
