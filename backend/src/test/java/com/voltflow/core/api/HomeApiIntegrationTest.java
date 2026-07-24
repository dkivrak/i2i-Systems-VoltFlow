package com.voltflow.core.api;

import com.voltflow.core.persistence.repository.HomeRepository;
import com.voltflow.core.auth.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomeApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired HomeRepository homes;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    void registersHomeAndMultipleAppliancesTransactionally() throws Exception {
        long before = homes.count();
        mvc.perform(authorized(post("/api/v1/homes")).contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Kadikoy Home"))
                .andExpect(jsonPath("$.appliances.length()").value(2))
                .andExpect(jsonPath("$.appliances[0].type").value("KETTLE"));
        assertThat(homes.count()).isEqualTo(before + 1);
    }

    @Test
    void returnsConsistentFieldValidationErrors() throws Exception {
        mvc.perform(authorized(post("/api/v1/homes")).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"","contactEmail":"bad","monthlyBudget":0,"normalTariffPerKwh":0,
                 "penaltyMultiplier":0.5,"appliances":[]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.monthlyBudget").exists())
                .andExpect(jsonPath("$.fieldErrors.contactEmail").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/homes"));
    }

    @Test
    void exposesRegistrationEndpointAndValidatesItsRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void lazilyInitializesEmptyLiveStateAndListsIt() throws Exception {
        String response = mvc.perform(authorized(post("/api/v1/homes")).contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
        mvc.perform(authorized(get("/api/v1/homes/{id}/status", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPowerWatts").value(0))
                .andExpect(jsonPath("$.tariffState").value("NORMAL"))
                .andExpect(jsonPath("$.appliances.length()").value(2));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void appliesConfiguredDefaultsAndListsRegistrationFromLiveStateAfterCommit() throws Exception {
        String created = mvc.perform(authorized(post("/api/v1/homes")).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Defaults Home","contactEmail":"defaults@example.com","appliances":[
                  {"name":"Desk Lamp","type":"LAMP","safePowerLimitWatts":60}
                ]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyBudget").value(1000.0))
                .andExpect(jsonPath("$.normalTariffPerKwh").value(2.5))
                .andExpect(jsonPath("$.penaltyMultiplier").value(1.5))
                .andReturn().getResponse().getContentAsString();
        long homeId = objectMapper.readTree(created).get("id").asLong();

        String statuses = mvc.perform(authorized(get("/api/v1/homes/status")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(statuses).get("content")).anySatisfy(node ->
                assertThat(node.get("homeId").asLong()).isEqualTo(homeId));
    }

    @Test
    void mapsQueryTypeDateAndConstraintFailuresToSafeBadRequests() throws Exception {
        mvc.perform(authorized(get("/api/v1/homes/1/history")).param("bucket", "MINUTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.bucket").value("has an invalid value"));
        mvc.perform(authorized(get("/api/v1/homes/1/history")).param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));
        mvc.perform(authorized(get("/api/v1/homes/status")).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    private String validRequest() {
        return """
                {"name":"Kadikoy Home","contactEmail":"Owner@Example.com","monthlyBudget":100,
                 "normalTariffPerKwh":2.5,"penaltyMultiplier":1.5,
                 "appliances":[
                    {"name":"Kitchen Kettle","type":"KETTLE","safePowerLimitWatts":2200},
                    {"name":"Spare Kettle","type":"KETTLE","safePowerLimitWatts":2100}
                 ]}
                """;
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(
                "Authorization",
                "Bearer " + tokenProvider.generateToken("integration@example.com")
        );
    }
}
