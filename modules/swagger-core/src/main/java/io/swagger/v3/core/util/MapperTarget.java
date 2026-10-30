package io.swagger.v3.core.util;

import io.swagger.v3.oas.models.SpecVersion;

/**
 * Identifies which of the {@link tools.jackson.databind.ObjectMapper} instances built by
 * {@link ObjectMapperFactory} a {@link MapperCustomizer} is being applied to.
 *
 * @since 3.0.0
 */
public enum MapperTarget {
    /** OpenAPI 3.0 JSON mapper, see {@link Json#mapper()}. */
    JSON(false, SpecVersion.V30),
    /** OpenAPI 3.0 YAML mapper, see {@link Yaml#mapper()}. */
    YAML(true, SpecVersion.V30),
    /** OpenAPI 3.1 JSON mapper, see {@link Json31#mapper()}. */
    JSON31(false, SpecVersion.V31),
    /** OpenAPI 3.1 YAML mapper, see {@link Yaml31#mapper()}. */
    YAML31(true, SpecVersion.V31),
    /** OpenAPI 3.2 JSON mapper, see {@link Json32#mapper()}. */
    JSON32(false, SpecVersion.V32),
    /** OpenAPI 3.2 YAML mapper, see {@link Yaml32#mapper()}. */
    YAML32(true, SpecVersion.V32),
    /** JSON mapper used to convert between OpenAPI 3.0 and 3.1 schemas, see {@link Json31#converterMapper()}. */
    JSON_CONVERTER(false, SpecVersion.V30);

    private final boolean yaml;
    private final SpecVersion specVersion;

    MapperTarget(boolean yaml, SpecVersion specVersion) {
        this.yaml = yaml;
        this.specVersion = specVersion;
    }

    public boolean isYaml() {
        return yaml;
    }

    public boolean isJson() {
        return !yaml;
    }

    /**
     * Returns true for mappers using the OpenAPI 3.1-or-later dialect, i.e. 3.1 and 3.2 mappers.
     *
     * @return whether this target uses the 3.1-or-later dialect
     */
    public boolean isOpenapi31() {
        return specVersion != SpecVersion.V30;
    }

    /**
     * Returns the spec version this mapper targets.
     *
     * @return the spec version
     */
    public SpecVersion specVersion() {
        return specVersion;
    }
}
