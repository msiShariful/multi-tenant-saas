package com.islamshariful.userservice.config;

import com.islamshariful.userservice.dto.response.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Describes the error contract on every operation without repeating an annotation on every method.
 *
 * <p>Applying it to the assembled document means a new endpoint cannot ship undocumented errors by
 * forgetting an annotation. It also adds the two responses true of an operation by construction: 401
 * wherever a token is required, 400 wherever there is a body to validate.
 */
@Component
public class ProblemResponseCustomizer implements OpenApiCustomizer {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String SCHEMA_NAME = "ApiError";
    private static final String SCHEMA_REF = "#/components/schemas/" + SCHEMA_NAME;

    @Override
    public void customise(OpenAPI openApi) {
        registerErrorSchema(openApi);
        openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .forEach(this::documentErrors);
    }

    /** {@link ApiError} is referenced only by {@code $ref}, so nothing else pulls it into components. */
    private void registerErrorSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Components components = openApi.getComponents();
        if (components.getSchemas() != null && components.getSchemas().containsKey(SCHEMA_NAME)) {
            return;
        }
        ModelConverters.getInstance().readAll(ApiError.class).forEach(components::addSchemas);
    }

    private void documentErrors(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }
        if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
            responses.computeIfAbsent("401", code -> new ApiResponse()
                    .description("Missing, malformed or expired access token"));
        }
        if (operation.getRequestBody() != null) {
            responses.computeIfAbsent("400", code -> new ApiResponse()
                    .description("The request body failed validation"));
        }
        responses.forEach((code, response) -> {
            if (code.startsWith("4") || code.startsWith("5") || "default".equals(code)) {
                applyProblemSchema(response);
            }
        });
    }

    private void applyProblemSchema(ApiResponse response) {
        Content existing = response.getContent();
        if (existing != null && existing.containsKey(PROBLEM_JSON)) {
            return;
        }
        response.setContent(new Content()
                .addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(SCHEMA_REF))));
    }
}
