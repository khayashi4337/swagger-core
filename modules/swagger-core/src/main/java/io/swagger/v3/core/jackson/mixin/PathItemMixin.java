package io.swagger.v3.core.jackson.mixin;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.models.Operation;

import java.util.Map;

/**
 * Mixin applied to {@link io.swagger.v3.oas.models.PathItem} for OpenAPI 3.0 and 3.1 mappers.
 *
 * <p>The {@code query} method and the {@code additionalOperations} map are Path Item
 * Object fields only as of OpenAPI 3.2, so they are ignored here to keep 3.0 and 3.1
 * documents conformant.
 */
public abstract class PathItemMixin {

    @JsonAnyGetter
    public abstract Map<String, Object> getExtensions();

    @JsonAnySetter
    public abstract void addExtension(String name, Object value);

    @JsonIgnore
    public abstract Operation getQuery();

    @JsonIgnore
    public abstract Map<String, Operation> getAdditionalOperations();
}
