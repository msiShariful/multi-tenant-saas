package com.islamshariful.authservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@DisplayName("Role-based access control")
class RoleBasedAccessControlIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@acme.example";
    private static final String MEMBER_EMAIL = "member@acme.example";

    @Test
    @DisplayName("an administrator can provision a user; that user cannot provision another")
    void onlyAdministratorsMayCreateUsers() throws Exception {
        registerTenant("acme", ADMIN_EMAIL);
        Tokens admin = login("acme", ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(MEMBER_EMAIL, "TENANT_USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("TENANT_USER"));

        Tokens member = login("acme", MEMBER_EMAIL);
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody("someone@acme.example", "TENANT_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("an ordinary member can still reach their own account")
    void membersRetainSelfServiceAccess() throws Exception {
        registerTenant("acme", ADMIN_EMAIL);
        Tokens admin = login("acme", ADMIN_EMAIL);
        createUser(admin, MEMBER_EMAIL, "TENANT_USER");

        Tokens member = login("acme", MEMBER_EMAIL);
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(MEMBER_EMAIL));

        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("promoting a member takes effect on their next token, not the one they already hold")
    void roleChangesApplyAtTheNextTokenIssue() throws Exception {
        AbstractIntegrationTest.Tenant tenant = registerTenant("acme", ADMIN_EMAIL);
        Tokens admin = login("acme", ADMIN_EMAIL);
        String memberId = createUser(admin, MEMBER_EMAIL, "TENANT_USER");
        Tokens memberBefore = login("acme", MEMBER_EMAIL);

        mockMvc.perform(put("/api/v1/users/" + memberId + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["TENANT_ADMIN", "TENANT_USER"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));

        // The old access token still says TENANT_USER, because roles are claims inside a signed document
        // and nothing can edit one after the fact. This is the staleness that access-token-ttl bounds.
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, memberBefore.bearer()))
                .andExpect(status().isForbidden());

        Tokens memberAfter = login("acme", MEMBER_EMAIL);
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, memberAfter.bearer()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(tenant.slug()).isEqualTo("acme");
    }

    @Test
    @DisplayName("a tenant cannot demote its last administrator and lock itself out")
    void theLastAdministratorIsProtected() throws Exception {
        AbstractIntegrationTest.Tenant tenant = registerTenant("acme", ADMIN_EMAIL);
        Tokens admin = login("acme", ADMIN_EMAIL);

        mockMvc.perform(put("/api/v1/users/" + tenant.administratorId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["TENANT_USER"]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMINISTRATOR"));
    }

    @Test
    @DisplayName("demotion is allowed once a second administrator exists")
    void demotionIsAllowedWhenAnotherAdministratorRemains() throws Exception {
        AbstractIntegrationTest.Tenant tenant = registerTenant("acme", ADMIN_EMAIL);
        Tokens admin = login("acme", ADMIN_EMAIL);
        createUser(admin, MEMBER_EMAIL, "TENANT_ADMIN");

        mockMvc.perform(put("/api/v1/users/" + tenant.administratorId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["TENANT_USER"]}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an email already used in this tenant is a 409")
    void duplicateEmailWithinATenantIsRejected() throws Exception {
        registerTenant("acme", ADMIN_EMAIL);
        Tokens admin = login("acme", ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(ADMIN_EMAIL, "TENANT_USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    private String createUser(Tokens admin, String email, String role) throws Exception {
        String json = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(email, role)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    private static String createUserBody(String email, String role) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "firstName": "Grace",
                  "lastName": "Hopper",
                  "roles": ["%s"]
                }"""
                .formatted(email, DEFAULT_PASSWORD, role);
    }
}
