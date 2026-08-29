package com.islamshariful.authservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.islamshariful.authservice.dto.response.ApiError;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Keeps the documented error shape honest.
 *
 * <p>{@link ApiError} exists only to describe {@code GlobalExceptionHandler}'s output to OpenAPI — nothing
 * constructs it, so nothing would notice if the handler started emitting a member it does not declare. These
 * tests provoke real failures and compare what comes back against the record's components, which turns that
 * silent drift into a build failure.
 */
@DisplayName("Error contract")
class ProblemDetailContractTest extends AbstractIntegrationTest {

    private static final Set<String> DOCUMENTED = Arrays.stream(ApiError.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toUnmodifiableSet());

    @Test
    @DisplayName("every member of a real error response is documented")
    void realResponsesCarryNoUndocumentedMembers() throws Exception {
        registerTenant("acme", "admin@acme.example");
        Tokens admin = login("acme", "admin@acme.example");

        // One request per distinct branch of the handler, so between them they exercise every
        // extension member the service is capable of emitting.
        assertDocumented("401 unauthenticated", get("/api/v1/auth/me"));
        assertDocumented("401 bad credentials", post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tenantSlug":"acme","email":"admin@acme.example","password":"wrong password here"}"""));
        assertDocumented("400 validation", post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tenantName":"x","tenantSlug":"NOT A SLUG","adminEmail":"nope","adminPassword":"short"}"""));
        assertDocumented("404 not found", get("/api/v1/users/" + java.util.UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, admin.bearer()));
        assertDocumented("409 conflict", put("/api/v1/users/" + currentUserId(admin) + "/roles")
                .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roles":["TENANT_USER"]}"""));
    }

    @Test
    @DisplayName("validation failures expose field and message, as documented")
    void validationErrorsMatchTheDocumentedShape() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantName":"x","tenantSlug":"NOT A SLUG","adminEmail":"nope","adminPassword":"short"}"""))
                .andReturn()
                .getResponse()
                .getContentAsString();

        java.util.List<Map<String, Object>> errors = JsonPath.read(body, "$.errors");
        assertThat(errors).isNotEmpty();
        assertThat(errors).allSatisfy(error -> assertThat(error).containsOnlyKeys("field", "message"));
    }

    private void assertDocumented(String label, RequestBuilder request) throws Exception {
        var response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getContentType())
                .as("%s should be a problem document", label)
                .contains("application/problem+json");

        Map<String, Object> body = JsonPath.read(response.getContentAsString(), "$");
        assertThat(body.keySet())
                .as("%s returned a member ApiError does not document — add it there", label)
                .isSubsetOf(DOCUMENTED);
        assertThat(body).as("%s must carry a machine-readable code", label).containsKey("code");
        assertThat(body).as("%s must carry a status", label).containsKey("status");
    }
}
