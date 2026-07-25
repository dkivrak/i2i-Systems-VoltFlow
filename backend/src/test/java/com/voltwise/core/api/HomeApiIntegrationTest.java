package com.voltwise.core.api;

import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.persistence.repository.ApplianceRepository;
import com.voltwise.core.persistence.repository.RegistrationOutboxRepository;
import com.voltwise.core.event.AssetRegistrationEvent;
import com.voltwise.core.auth.JwtTokenProvider;
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
    @Autowired ApplianceRepository appliances;
    @Autowired RegistrationOutboxRepository outbox;

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
    void registersHomeWithoutDevicesAndDefersAssetEventUntilFirstDevice() throws Exception {
        String created = mvc.perform(authorized(post("/api/v1/homes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Empty Home","contactEmail":"owner@example.com",
                                 "monthlyBudget":1000,"normalTariffPerKwh":2.5,
                                 "penaltyMultiplier":1.5,"appliances":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliances").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long homeId = objectMapper.readTree(created).get("id").asLong();
        assertThat(outbox.findByHomeIdOrderByCreatedAtAsc(homeId)).isEmpty();

        mvc.perform(authorized(post("/api/v1/homes/{homeId}/appliances", homeId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"İlk Cihaz","type":"LAMP","safePowerLimitWatts":60}
                                """))
                .andExpect(status().isCreated());

        assertThat(outbox.findByHomeIdOrderByCreatedAtAsc(homeId)).hasSize(1);
    }

    @Test
    void addsApplianceToOwnedHomePersistsItAndEnqueuesFullRegistrationSnapshot() throws Exception {
        String created = mvc.perform(authorized(post("/api/v1/homes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andReturn().getResponse().getContentAsString();
        long homeId = objectMapper.readTree(created).get("id").asLong();
        long before = appliances.count();

        String response = mvc.perform(authorized(post("/api/v1/homes/{homeId}/appliances", homeId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Salon Televizyonu","type":"TELEVISION","safePowerLimitWatts":450}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Salon Televizyonu"))
                .andExpect(jsonPath("$.type").value("TELEVISION"))
                .andExpect(jsonPath("$.safePowerLimitWatts").value(450))
                .andReturn().getResponse().getContentAsString();

        long applianceId = objectMapper.readTree(response).get("id").asLong();
        assertThat(appliances.count()).isEqualTo(before + 1);
        assertThat(appliances.findById(applianceId)).get()
                .extracting(entity -> entity.getHome().getId())
                .isEqualTo(homeId);

        var registrationRows = outbox.findByHomeIdOrderByCreatedAtAsc(homeId);
        assertThat(registrationRows).hasSize(2);
        AssetRegistrationEvent event = objectMapper.readValue(
                registrationRows.getLast().getEventPayload(),
                AssetRegistrationEvent.class);
        assertThat(event.eventType()).isEqualTo("HOME_REGISTERED");
        assertThat(event.appliances()).hasSize(3)
                .anySatisfy(item -> {
                    assertThat(item.applianceId()).isEqualTo(applianceId);
                    assertThat(item.type()).isEqualTo(com.voltwise.core.domain.ApplianceType.TELEVISION);
                });
    }

    @Test
    void rejectsUnauthenticatedApplianceRegistration() throws Exception {
        mvc.perform(post("/api/v1/homes/1/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cihaz","type":"LAMP","safePowerLimitWatts":50}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Geçerli bir oturum gereklidir."));
    }

    @Test
    void rejectsAddingApplianceToAnotherUsersHome() throws Exception {
        String created = mvc.perform(authorizedAs(post("/api/v1/homes"), "owner@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andReturn().getResponse().getContentAsString();
        long homeId = objectMapper.readTree(created).get("id").asLong();

        mvc.perform(authorizedAs(
                        post("/api/v1/homes/{homeId}/appliances", homeId),
                        "intruder@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Yetkisiz Cihaz","type":"LAMP","safePowerLimitWatts":50}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bu ev üzerinde işlem yapma yetkiniz yok."));
    }

    @Test
    void validatesInvalidAppliancePowerLimit() throws Exception {
        String created = mvc.perform(authorized(post("/api/v1/homes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andReturn().getResponse().getContentAsString();
        long homeId = objectMapper.readTree(created).get("id").asLong();

        mvc.perform(authorized(post("/api/v1/homes/{homeId}/appliances", homeId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Geçersiz Cihaz","type":"COMPUTER","safePowerLimitWatts":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.safePowerLimitWatts").exists());
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

    @Test
    void updatesApplianceNameInOwnedHome() throws Exception {
        String created = mvc.perform(authorized(post("/api/v1/homes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andReturn().getResponse().getContentAsString();
        long homeId = objectMapper.readTree(created).get("id").asLong();
        long applianceId = objectMapper.readTree(created).get("appliances").get(0).get("id").asLong();

        mvc.perform(authorized(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/homes/{homeId}/appliances/{applianceId}", homeId, applianceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Yeni Cihaz Adı"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yeni Cihaz Adı"))
                .andExpect(jsonPath("$.id").value(applianceId));

        mvc.perform(authorizedAs(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/homes/{homeId}/appliances/{applianceId}", homeId, applianceId),
                        "intruder@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Yetkisiz Ad"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bu ev üzerinde işlem yapma yetkiniz yok."));
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
        return authorizedAs(request, "integration@example.com");
    }

    private MockHttpServletRequestBuilder authorizedAs(
            MockHttpServletRequestBuilder request,
            String email) {
        return request.header(
                "Authorization",
                "Bearer " + tokenProvider.generateToken(email)
        );
    }
}
