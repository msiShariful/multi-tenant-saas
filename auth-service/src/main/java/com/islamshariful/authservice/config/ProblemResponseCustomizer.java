package com.islamshariful.authservice.config;

import com.islamshariful.authservice.dto.response.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Describes the error contract on every operation, without repeating an annotation on every method.
 *
 * <p>Annotating each failure by hand would mean the same {@code @Content(mediaType = "application/problem+json",
 * schema = @Schema(implementation = ApiError.class))} on roughly twenty methods, and a new endpoint would
 * silently ship undocumented errors the day someone forgot it. Applying it to the assembled document instead
 * means an endpoint cannot opt out by omission.
 *
 * <p>Per-operation {@code @ApiResponse} annotations stay where they are: they carry the descriptions that are
 * actually specific ("Email already registered in this tenant"). This only supplies the media type and schema
 * they all share, and adds the two responses that are true of an operation by construction rather than by
 * choice — 401 wherever a token is required, 400 wherever there is a body to validate.
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

    /** {@link ApiError} is referenced only by {@code $ref}, so nothing else pulls it into the components block. */
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
        // True of the operation by construction: a token is required, so it can always be missing or expired.
        if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
            responses.computeIfAbsent("401", code -> problem("Missing, malformed or expired access token"));
        }
        // Likewise: there is a body, so it can always fail bean validation.
        if (operation.getRequestBody() != null) {
            responses.computeIfAbsent("400", code -> problem("The request body failed validation"));
        }
        responses.forEach((code, response) -> {
            if (isFailure(code)) {
                applyProblemSchema(response);
            }
        });
    }

    private static boolean isFailure(String code) {
        return code.startsWith("4") || code.startsWith("5") || "default".equals(code);
    }

    /** Leaves an explicitly documented problem body alone; fills in the shared one otherwise. */
    private void applyProblemSchema(ApiResponse response) {
        Content existing = response.getContent();
        if (existing != null && existing.containsKey(PROBLEM_JSON)) {
            return;
        }
        response.setContent(new Content()
                .addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(SCHEMA_REF))));
    }

    private ApiResponse problem(String description) {
        return new ApiResponse().description(description);
    }

    /** Exposed for the contract test, which checks real responses against the documented members. */
    public static String schemaName() {
        return SCHEMA_NAME;
    }
}
