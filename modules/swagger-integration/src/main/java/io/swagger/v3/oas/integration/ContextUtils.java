package io.swagger.v3.oas.integration;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json32;
import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextUtils {

    private static Logger LOGGER = LoggerFactory.getLogger(ContextUtils.class);

    // TODO implement proper clone see #2227
    public static OpenAPIConfiguration deepCopy(OpenAPIConfiguration config) {
        if (config == null) {
            return null;
        }
        try {
            if (ParameterProcessor.isOpenAPI32(config.getOpenAPIVersion(), config.getOpenAPI())) {
                // a 3.2 seed document carries 3.2-only members (query and
                // additionalOperations, in: querystring, itemSchema, ...) that a
                // 3.0 round-trip would drop; earlier versions keep the baseline
                // 3.0 mapper copy below
                return Json32.mapper().readValue(Json32.pretty(config), SwaggerConfiguration.class);
            }
            return Json.mapper().readValue(Json.pretty(config), SwaggerConfiguration.class);
        } catch (Exception e) {
            LOGGER.error("Exception cloning config: " + e.getMessage(), e);
            return config;
        }
    }

}
