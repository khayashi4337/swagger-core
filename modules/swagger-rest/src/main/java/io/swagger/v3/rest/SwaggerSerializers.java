package io.swagger.v3.rest;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Json32;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.core.util.Yaml32;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/yaml"})
public class SwaggerSerializers implements MessageBodyWriter<OpenAPI> {
    static boolean prettyPrint = false;
    static boolean openapi31 = false;

    public static void setPrettyPrint(boolean shouldPrettyPrint) {
        SwaggerSerializers.prettyPrint = shouldPrettyPrint;
    }

    public static void setOpenapi31(boolean openapi31) {
        SwaggerSerializers.openapi31 = openapi31;
    }

    /**
     * Whether the document being written is an OpenAPI 3.2 document, detected
     * from its {@code specVersion} or {@code openapi} string. 3.2 output must
     * go through the 3.2 mappers so 3.2-only values (e.g. {@code in: querystring})
     * serialize instead of failing.
     */
    private static boolean isOpenAPI32(OpenAPI data) {
        if (data == null) {
            return false;
        }
        if (SpecVersion.V32.equals(data.getSpecVersion())) {
            return true;
        }
        return data.getOpenapi() != null && data.getOpenapi().startsWith("3.2");
    }

    @Override
    public boolean isWriteable(Class type, Type genericType, Annotation[] annotations,
                               MediaType mediaType) {
        return OpenAPI.class.isAssignableFrom(type);
    }

    @Override
    public long getSize(OpenAPI data, Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1L;
    }

    @Override
    public void writeTo(OpenAPI data,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType,
                        MultivaluedMap<String, Object> headers,
                        OutputStream out) throws IOException {
        boolean openapi32 = isOpenAPI32(data);
        if (mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            if (prettyPrint) {
                if (openapi32) {
                    out.write(Json32.pretty().writeValueAsBytes(data));
                } else if (openapi31) {
                    out.write(Json31.pretty().writeValueAsBytes(data));
                } else {
                    out.write(Json.pretty().writeValueAsBytes(data));
                }
            } else {
                if (openapi32) {
                    out.write(Json32.mapper().writeValueAsBytes(data));
                } else if (openapi31) {
                    out.write(Json31.mapper().writeValueAsBytes(data));
                } else {
                    out.write(Json.mapper().writeValueAsBytes(data));
                }
            }
        } else if (mediaType.toString().startsWith("application/yaml")) {
            headers.remove("Content-Type");
            headers.add("Content-Type", "application/yaml");
            if (prettyPrint) {
                if (openapi32) {
                    out.write(Yaml32.pretty().writeValueAsBytes(data));
                } else if (openapi31) {
                    out.write(Yaml31.pretty().writeValueAsBytes(data));
                } else {
                    out.write(Yaml.pretty().writeValueAsBytes(data));
                }
            } else {
                if (openapi32) {
                    out.write(Yaml32.mapper().writeValueAsBytes(data));
                } else if (openapi31) {
                    out.write(Yaml31.mapper().writeValueAsBytes(data));
                } else {
                    out.write(Yaml.mapper().writeValueAsBytes(data));
                }
            }
        } else if (mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE)) {
            headers.remove("Content-Type");
            headers.add("Content-Type", MediaType.APPLICATION_JSON);
            if (prettyPrint) {
                if (openapi32) {
                    out.write(Json32.pretty().writeValueAsBytes(data));
                } else if (openapi31) {
                    out.write(Json31.pretty().writeValueAsBytes(data));
                } else {
                    out.write(Json.pretty().writeValueAsBytes(data));
                }
            } else {
                if (openapi32) {
                    out.write(Json32.mapper().writeValueAsBytes(data));
                } else if (openapi31) {
                    out.write(Json31.mapper().writeValueAsBytes(data));
                } else {
                    out.write(Json.mapper().writeValueAsBytes(data));
                }
            }
        }
    }
}
