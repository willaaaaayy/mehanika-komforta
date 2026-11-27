package ru.mehanikakomforta.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.mehanikakomforta.clients.ClientRepository;
import ru.mehanikakomforta.leads.LeadRepository;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PriceVersionTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PriceVersionRepository versions;
    @Autowired
    private PriceVersionLineRepository lines;
    @Autowired
    private ContractRepository contracts;
    @Autowired
    private WorkObjectRepository objects;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private LeadRepository leads;

    @Test
    void approvalKeepsTheOldVersionAndRecalculatesTheContract() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Long objectId = null;
        try {
            mvc.perform(get("/api/objects/1/price-versions")).andExpect(status().isUnauthorized());
            MvcResult login = mvc.perform(post("/api/session")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"test-admin-secret\"}"))
                    .andExpect(status().isNoContent())
                    .andReturn();
            MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
            MvcResult lead = mvc.perform(post("/api/leads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Смета\",\"phone\":\"79006660010\",\"objectType\":\"Квартира\",\"source\":\"site\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String leadId = lead.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            MvcResult converted = mvc.perform(post("/api/leads/" + leadId + "/convert")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"person\",\"name\":\"Смета\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String clientId = converted.getResponse().getContentAsString().replaceFirst(".*?\"clientId\":(\\d+).*", "$1");
            MvcResult created = mvc.perform(post("/api/objects")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientId\":" + clientId + ",\"address\":\"Объект сметы\",\"type\":\"квартира\",\"status\":\"prep\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            objectId = Long.valueOf(created.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1"));
            ContractEntity contract = new ContractEntity(Instant.now(), "СМ-10", "смета");
            contract.fill("СМ-10", "Смета", null, "Объект сметы", new BigDecimal("1000"), true);
            contract.linked(Long.valueOf(clientId), objectId, null);
            contract = contracts.save(contract);
            contract.getSchedule().add(new ScheduleEntity(contract, "Оплата", new BigDecimal("1000"), null, "final", 0));
            contracts.save(contract);
            MvcResult listed = mvc.perform(get("/api/objects/" + objectId + "/price-versions").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].version").value(1))
                    .andExpect(jsonPath("$[0].amount").value(1000.0))
                    .andExpect(jsonPath("$[0].approved").value(true))
                    .andReturn();
            String firstId = listed.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            MvcResult second = mvc.perform(post("/api/objects/" + objectId + "/price-versions")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Допработа\",\"lines\":[{\"title\":\"Штукатурка\",\"change\":\"extra\",\"quantity\":\"2\",\"clientPrice\":\"500\"}]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.version").value(2))
                    .andExpect(jsonPath("$.amount").value(2000.0))
                    .andExpect(jsonPath("$.approved").value(false))
                    .andReturn();
            String secondId = second.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            mvc.perform(get("/api/objects/" + objectId + "/price-versions").session(session))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].version").value(1))
                    .andExpect(jsonPath("$[1].amount").value(1000.0));
            mvc.perform(get("/api/objects/" + objectId + "/price-versions/compare")
                            .param("from", firstId)
                            .param("to", secondId)
                            .session(session))
                    .andExpect(jsonPath("$.amount.was").value(1000.0))
                    .andExpect(jsonPath("$.amount.now").value(2000.0))
                    .andExpect(jsonPath("$.difference").value(1000.0));
            mvc.perform(get("/api/clients/" + clientId).session(session))
                    .andExpect(jsonPath("$.due").value(1000.0));
            mvc.perform(post("/api/objects/" + objectId + "/price-versions/" + secondId + "/approve")
                            .with(csrf())
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.approved").value(true))
                    .andExpect(jsonPath("$.addendum").value("СМ-10-ДС-2"));
            mvc.perform(get("/api/clients/" + clientId).session(session))
                    .andExpect(jsonPath("$.amount").value(2000.0))
                    .andExpect(jsonPath("$.due").value(2000.0));
            mvc.perform(get("/api/finance").param("client", clientId).session(session))
                    .andExpect(jsonPath("$.totals.receivable").value(2000.0))
                    .andExpect(jsonPath("$.clients[0].amount").value(2000.0));
            mvc.perform(get("/api/objects/" + objectId + "/price-versions/" + firstId + "/export").param("format", "pdf").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));
            mvc.perform(get("/api/objects/" + objectId + "/price-versions/" + secondId + "/export").param("format", "xlsx").session(session))
                    .andExpect(status().isOk());
        } finally {
            if (objectId != null) {
                for (PriceVersionEntity version : versions.findByObjectIdOrderByVersionNoDesc(objectId)) {
                    lines.deleteAll(lines.findByVersionIdOrderByIdAsc(version.getId()));
                }
                versions.deleteAll(versions.findByObjectIdOrderByVersionNoDesc(objectId));
            }
            contracts.deleteAll(contracts.findAll().stream().filter(contract -> "СМ-10".equals(contract.getNumber())).toList());
            objects.deleteAll(objects.findAll().stream().filter(object -> "Объект сметы".equals(object.getAddress())).toList());
            clients.deleteAll(clients.findAll().stream().filter(client -> "Смета".equals(client.getName())).toList());
            leads.deleteAll(leads.findAll().stream().filter(lead -> "79006660010".equals(lead.getPhone())).toList());
            mvc.perform(get("/api/session").with(request -> {
                WebTestUtils.setCsrfTokenRepository(request, CookieCsrfTokenRepository.withHttpOnlyFalse());
                return request;
            }));
        }
    }
}
