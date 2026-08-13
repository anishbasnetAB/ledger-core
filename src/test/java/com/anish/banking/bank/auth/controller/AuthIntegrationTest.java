package com.anish.banking.bank.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional   // roll back per test: registered users would otherwise pile up in the shared DB
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void registerLoginAndUseTokenOnAProtectedEndpoint() throws Exception {
        String email = "auth-test-" + java.util.UUID.randomUUID() + "@example.com";
        String body = """
                {"email":"%s","password":"correct-horse-battery"}
                """.formatted(email);

        String token = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();

        // a valid token from registration works immediately on a protected endpoint
        String bearer = "Bearer " + token.split("\"token\":\"")[1].split("\"")[0];
        mockMvc.perform(get("/api/accounts/1/balance").header("Authorization", bearer))
                .andExpect(status().isOk());

        // logging in with the same credentials also succeeds
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void registeringTheSameEmailTwiceIsRejected() throws Exception {
        String email = "dup-test-" + java.util.UUID.randomUUID() + "@example.com";
        String body = """
                {"email":"%s","password":"correct-horse-battery"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        String email = "badpw-test-" + java.util.UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"correct-horse-battery"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/accounts/1/balance"))
                .andExpect(status().isUnauthorized());
    }
}
