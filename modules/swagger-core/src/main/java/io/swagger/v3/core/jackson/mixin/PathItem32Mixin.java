package io.swagger.v3.core.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.models.Operation;

import java.util.Map;

/**
 * Mixin applied to {@link io.swagger.v3.oas.models.PathItem} for the OpenAPI 3.2 mapper.
 *
 * <p>Identical to {@link PathItemMixin} except that {@code query} and
 * {@code additionalOperations} are not ignored: the {@code query} HTTP method and
 * named additional operations are Path Item Object fields as of OpenAPI 3.2.
 */
public abstract class PathItem32Mixin {

    @JsonAnyGetter
    public abstract Map<String, Object> getExtensions();

    @JsonAnySetter
    public abstract void addExtension(String name, Object value);

    public abstract Operation getQuery();
}
