package com.voltwise.core.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.persistence.entity.UserEntity;
import com.voltwise.core.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    void registersNormalizedUserHashesPasswordAndIssuesCompatibleJwt() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"New.User@Example.COM","password":"securePassword"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        String token = response.get("token").asText();
        assertThat(tokenProvider.extractEmail(token)).isEqualTo("new.user@example.com");

        UserEntity stored = users.findByEmail("new.user@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("securePassword");
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches("securePassword", stored.getPasswordHash())).isTrue();

        mvc.perform(get("/api/v1/homes/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDuplicateRegistrationWithoutReturningSecurityDetails() throws Exception {
        register("duplicate@example.com", "securePassword");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"DUPLICATE@example.com","password":"anotherPassword"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Bu e-posta adresiyle bir hesap zaten mevcut."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void logsInWithPasswordAndReturnsTheSameResponseContract() throws Exception {
        register("login@example.com", "securePassword");

        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"LOGIN@EXAMPLE.COM","password":"securePassword"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login@example.com"))
                .andExpect(jsonPath("$.user.id").isNumber())
                .andReturn().getResponse().getContentAsString();

        assertThat(tokenProvider.extractEmail(
                objectMapper.readTree(response).get("token").asText()
        )).isEqualTo("login@example.com");
    }

    @Test
    void usesTheSameGenericErrorForInvalidPasswordAndUnknownEmail() throws Exception {
        register("known@example.com", "securePassword");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"known@example.com","password":"wrongPassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-posta adresi veya şifre hatalı."));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com","password":"wrongPassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-posta adresi veya şifre hatalı."));
    }

    @Test
    void validatesRequestsAndRejectsMissingOrMalformedBearerTokens() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bad","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mvc.perform(get("/api/v1/homes/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Geçerli bir oturum gereklidir."));

        mvc.perform(get("/api/v1/homes/status")
                        .header("Authorization", "Bearer malformed.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Geçerli bir oturum gereklidir."));
    }

    private String register(String email, String password) throws Exception {
        String payload = objectMapper.createObjectNode()
                .put("email", email)
                .put("password", password)
                .toString();
        return mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
