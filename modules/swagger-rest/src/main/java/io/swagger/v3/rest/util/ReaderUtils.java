package io.swagger.v3.rest.util;

import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.v3.core.util.Configuration;
import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.core.util.ReflectionUtils;
import io.swagger.v3.rest.ext.OpenAPIExtension;
import io.swagger.v3.rest.ext.OpenAPIExtensions;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.core.util.StringUtils;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Context;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class ReaderUtils {
    private static final String GET_METHOD = "get";
    private static final String POST_METHOD = "post";
    private static final String PUT_METHOD = "put";
    private static final String DELETE_METHOD = "delete";
    private static final String HEAD_METHOD = "head";
    private static final String OPTIONS_METHOD = "options";
    private static final String PATH_DELIMITER = "/";

    public static List<Parameter> collectConstructorParameters(Class<?> cls, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation) {
        return collectConstructorParameters(cls, components, classConsumes, jsonViewAnnotation, null);
    }

    public static List<Parameter> collectConstructorParameters(Class<?> cls, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation, Schema.SchemaResolution schemaResolution) {
        return collectConstructorParameters(cls, components, classConsumes, jsonViewAnnotation, schemaResolution, false);
    }
    /**
     * Collects constructor-level parameters from class.
     *
     * @param cls        is a class for collecting
     * @param components
     * @return the collection of supported parameters
     */
    public static List<Parameter> collectConstructorParameters(Class<?> cls, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation, Schema.SchemaResolution schemaResolution, boolean openapi31) {
        return collectConstructorParameters(cls, components, classConsumes, jsonViewAnnotation, schemaResolution, openapi31, null);
    }

    public static List<Parameter> collectConstructorParameters(Class<?> cls, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation, Schema.SchemaResolution schemaResolution, boolean openapi31, Configuration configuration) {
        if (cls.isLocalClass() || (cls.isMemberClass() && !Modifier.isStatic(cls.getModifiers()))) {
            return Collections.emptyList();
        }
        final Configuration effectiveConfiguration;
        if (configuration != null) {
            effectiveConfiguration = configuration;
        } else {
            effectiveConfiguration = new Configuration();
            effectiveConfiguration.setOpenAPI31(openapi31);
            effectiveConfiguration.setSchemaResolution(schemaResolution);
        }

        List<Parameter> selected = Collections.emptyList();
        int maxParamsCount = 0;

        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            if (!ReflectionUtils.isConstructorCompatible(constructor)
                    && !ReflectionUtils.isInject(Arrays.asList(constructor.getDeclaredAnnotations()))) {
                continue;
            }

            final Type[] genericParameterTypes = constructor.getGenericParameterTypes();
            final Annotation[][] annotations = constructor.getParameterAnnotations();

            int paramsCount = 0;
            final List<Parameter> parameters = new ArrayList<>();
            for (int i = 0; i < genericParameterTypes.length; i++) {
                final List<Annotation> tmpAnnotations = Arrays.asList(annotations[i]);
                if (isContext(tmpAnnotations)) {
                    paramsCount++;
                } else {
                    final Type genericParameterType = genericParameterTypes[i];
                    // only a caller-provided configuration is pushed to the extension;
                    // legacy signatures (configuration == null) leave the extension's
                    // own settings untouched, like collectFieldParameters does
                    final List<Parameter> tmpParameters = collectParameters(genericParameterType, tmpAnnotations, components, classConsumes, jsonViewAnnotation, configuration);
                    if (! tmpParameters.isEmpty()) {
                        for (Parameter tmpParameter : tmpParameters) {
                            Parameter processedParameter = ParameterProcessor.applyAnnotations(
                                    tmpParameter,
                                    genericParameterType,
                                    tmpAnnotations,
                                    components,
                                    classConsumes == null ? new String[0] : classConsumes.value(),
                                    null,
                                    jsonViewAnnotation,
                                    effectiveConfiguration);
                            if (processedParameter != null) {
                                parameters.add(processedParameter);
                            }
                        }
                        paramsCount++;
                    }
                }
            }

            if (paramsCount >= maxParamsCount) {
                maxParamsCount = paramsCount;
                selected = parameters;
            }
        }

        return selected;
    }

    /**
     * Collects field-level parameters from class.
     *
     * @param cls        is a class for collecting
     * @param components
     * @return the collection of supported parameters
     */
    public static List<Parameter> collectFieldParameters(Class<?> cls, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation) {
        return collectFieldParameters(cls, components, classConsumes, jsonViewAnnotation, null);
    }

    public static List<Parameter> collectFieldParameters(Class<?> cls, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation, Configuration configuration) {
        final List<Parameter> parameters = new ArrayList<>();
        for (Field field : ReflectionUtils.getDeclaredFields(cls)) {
            final List<Annotation> annotations = Arrays.asList(field.getAnnotations());
            final Type genericType = field.getGenericType();
            parameters.addAll(collectParameters(genericType, annotations, components, classConsumes, jsonViewAnnotation, configuration));
        }
        return parameters;
    }

    private static List<Parameter> collectParameters(Type type, List<Annotation> annotations, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation) {
        return collectParameters(type, annotations, components, classConsumes, jsonViewAnnotation, null);
    }

    private static List<Parameter> collectParameters(Type type, List<Annotation> annotations, Components components, jakarta.ws.rs.Consumes classConsumes, JsonView jsonViewAnnotation, Configuration configuration) {
        final Iterator<OpenAPIExtension> chain = OpenAPIExtensions.chain();
        if (!chain.hasNext()) {
            return Collections.emptyList();
        }
        OpenAPIExtension extension = chain.next();
        // align the extension with the caller's values on every call: the scan
        // configuration when provided, otherwise null, which clears a
        // configuration a previous scan left behind so a stale scan version
        // (e.g. 3.2) cannot leak into legacy calls
        extension.setConfiguration(configuration);
        return extension.extractParameters(annotations, type, new HashSet<>(), components, classConsumes, null, false, jsonViewAnnotation, chain).parameters;
    }

    private static boolean isContext(List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Context) {
                return true;
            }
        }
        return false;
    }

    public static Optional<List<String>> getStringListFromStringArray(String[] array) {
        if (array == null) {
            return Optional.empty();
        }
        List<String> list = new ArrayList<>();
        boolean isEmpty = true;
        for (String value : array) {
            if (StringUtils.isNotBlank(value)) {
                isEmpty = false;
            }
            list.add(value);
        }
        if (isEmpty) {
            return Optional.empty();
        }
        return Optional.of(list);
    }

    public static boolean isIgnored(String path, OpenAPIConfiguration config) {
        if (config.getIgnoredRoutes() == null) {
            return false;
        }
        for (String item : config.getIgnoredRoutes()) {
            final int length = item.length();
            if (path.startsWith(item) && (path.length() == length || path.startsWith(PATH_DELIMITER, length))) {
                return true;
            }
        }
        return false;
    }

    public static String getPath(jakarta.ws.rs.Path classLevelPath, jakarta.ws.rs.Path methodLevelPath, String parentPath, boolean isSubresource) {
        if (classLevelPath == null && methodLevelPath == null && StringUtils.isEmpty(parentPath)) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        appendPathComponent(parentPath, b);
        if (classLevelPath != null && !isSubresource) {
            appendPathComponent(classLevelPath.value(), b);
        }
        if (methodLevelPath != null) {
            appendPathComponent(methodLevelPath.value(), b);
        }
        return b.length() == 0 ? "/" : b.toString();
    }

    /**
     * appends a path component string to a StringBuilder
     * guarantees:
     * <ul>
     *     <li>nulls, empty strings and "/" are nops</li>
     *     <li>output will always start with "/" and never end with "/"</li>
     * </ul>
     * @param component component to be added
     * @param to output
     */
    private static void appendPathComponent(String component, StringBuilder to) {
        if (component == null || component.isEmpty() || "/".equals(component)) {
            return;
        }
        if (!component.startsWith("/") && (to.length() == 0 || '/' != to.charAt(to.length() - 1))) {
            to.append("/");
        }
        if (component.endsWith("/")) {
            to.append(component, 0, component.length() - 1);
        } else {
            to.append(component);
        }
    }

    public static String extractOperationMethod(Method method, Iterator<OpenAPIExtension> chain) {
        return extractOperationMethod(method, chain, false);
    }

    /**
     * @param declaredCase when true, a custom {@code @HttpMethod} value keeps its
     * declared case; OpenAPI 3.2 scans use this so a custom method can land in
     * {@code PathItem.additionalOperations} keyed by its declared name. When
     * false the value is lowercased exactly like the original contract, so
     * pre-3.2 method matching behaves as it did before.
     */
    public static String extractOperationMethod(Method method, Iterator<OpenAPIExtension> chain, boolean declaredCase) {
        if (method.getAnnotation(jakarta.ws.rs.GET.class) != null) {
            return GET_METHOD;
        } else if (method.getAnnotation(jakarta.ws.rs.PUT.class) != null) {
            return PUT_METHOD;
        } else if (method.getAnnotation(jakarta.ws.rs.POST.class) != null) {
            return POST_METHOD;
        } else if (method.getAnnotation(jakarta.ws.rs.DELETE.class) != null) {
            return DELETE_METHOD;
        } else if (method.getAnnotation(jakarta.ws.rs.OPTIONS.class) != null) {
            return OPTIONS_METHOD;
        } else if (method.getAnnotation(jakarta.ws.rs.HEAD.class) != null) {
            return HEAD_METHOD;
        } else if (method.getAnnotation(HttpMethod.class) != null) {
            HttpMethod httpMethod = method.getAnnotation(HttpMethod.class);
            return declaredCase ? httpMethod.value() : httpMethod.value().toLowerCase();
        } else if (!StringUtils.isEmpty(getHttpMethodFromCustomAnnotations(method, declaredCase))) {
            return getHttpMethodFromCustomAnnotations(method, declaredCase);
        } else if ((ReflectionUtils.getOverriddenMethod(method)) != null) {
            return extractOperationMethod(ReflectionUtils.getOverriddenMethod(method), chain, declaredCase);
        } else if (chain != null && chain.hasNext()) {
            return chain.next().extractOperationMethod(method, chain);
        } else {
            return null;
        }
    }

    public static String getHttpMethodFromCustomAnnotations(Method method) {
        return getHttpMethodFromCustomAnnotations(method, false);
    }

    /**
     * @param declaredCase see {@link #extractOperationMethod(Method, Iterator, boolean)}
     */
    public static String getHttpMethodFromCustomAnnotations(Method method, boolean declaredCase) {
        for (Annotation methodAnnotation : method.getAnnotations()) {
            HttpMethod httpMethod = methodAnnotation.annotationType().getAnnotation(HttpMethod.class);
            if (httpMethod != null) {
                return declaredCase ? httpMethod.value() : httpMethod.value().toLowerCase();
            }
        }
        return null;
    }
}
