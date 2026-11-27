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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SupplierTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private SupplierRepository suppliers;
    @Autowired
    private SupplierDocumentRepository documents;
    @Autowired
    private SupplierLineRepository lines;
    @Autowired
    private SupplierPaymentRepository payments;
    @Autowired
    private ExpenseRepository expenses;
    @Autowired
    private WorkObjectRepository objects;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private LeadRepository leads;

    @Test
    void materialOnAnObjectAddsCostAndPaymentReducesDebt() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        try {
            mvc.perform(get("/api/suppliers")).andExpect(status().isUnauthorized());
            MvcResult login = mvc.perform(post("/api/session")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"test-admin-secret\"}"))
                    .andExpect(status().isNoContent())
                    .andReturn();
            MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
            MvcResult lead = mvc.perform(post("/api/leads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Материалы\",\"phone\":\"79006660009\",\"objectType\":\"Квартира\",\"source\":\"site\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String leadId = lead.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            MvcResult converted = mvc.perform(post("/api/leads/" + leadId + "/convert")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"person\",\"name\":\"Материалы\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String clientId = converted.getResponse().getContentAsString().replaceFirst(".*?\"clientId\":(\\d+).*", "$1");
            MvcResult created = mvc.perform(post("/api/objects")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientId\":" + clientId + ",\"address\":\"Объект материалов\",\"type\":\"квартира\",\"status\":\"prep\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String objectId = created.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            MvcResult supplier = mvc.perform(post("/api/suppliers")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Снабжение\",\"inn\":\"6450000000\",\"phone\":\"79001112233\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String supplierId = supplier.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            MvcResult document = mvc.perform(multipart("/api/suppliers/" + supplierId + "/documents")
                            .param("kind", "delivery")
                            .param("number", "15")
                            .param("date", "2026-12-02")
                            .param("lines", "[{\"title\":\"Гипс\",\"amount\":\"1000\",\"object\":\"Объект материалов\"},{\"title\":\"Клей\",\"amount\":\"400\",\"object\":\"\"}]")
                            .with(csrf())
                            .session(session))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.purchased").value(1400.0))
                    .andExpect(jsonPath("$.debt").value(1400.0))
                    .andExpect(jsonPath("$.nextDue").value("2026-12-02"))
                    .andReturn();
            String documentId = document.getResponse().getContentAsString().replaceFirst(".*?\"documents\":\\[\\{\"id\":(\\d+).*", "$1");
            String looseId = document.getResponse().getContentAsString().replaceFirst(".*?\"title\":\"Клей\",\"amount\":400\\.0,\"object\":null,\"id\":(\\d+).*", "$1");
            if (!looseId.matches("\\d+")) {
                looseId = lines.findAll().stream().filter(line -> "Клей".equals(line.getTitle())).findFirst().orElseThrow().getId().toString();
            }
            mvc.perform(get("/api/supplier-lines").param("unassigned", "1").session(session))
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get("/api/finance").param("from", "2026-12-02").param("to", "2026-12-02").session(session))
                    .andExpect(jsonPath("$.totals.spent").value(1000.0))
                    .andExpect(jsonPath("$.totals.payable").value(1400.0))
                    .andExpect(jsonPath("$.expenses[?(@.category=='materials')].kind", org.hamcrest.Matchers.hasItem("object")));
            mvc.perform(get("/api/objects/" + objectId).session(session))
                    .andExpect(jsonPath("$.materials[?(@.title=='Гипс')].amount", org.hamcrest.Matchers.hasItem(1000.0)))
                    .andExpect(jsonPath("$.materials[?(@.title=='Гипс')].supplier", org.hamcrest.Matchers.hasItem("Снабжение")));
            mvc.perform(patch("/api/supplier-lines/" + looseId)
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"object\":\"Объект материалов\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/supplier-lines").param("unassigned", "1").session(session))
                    .andExpect(jsonPath("$.length()").value(0));
            mvc.perform(get("/api/finance").param("from", "2026-12-02").param("to", "2026-12-02").session(session))
                    .andExpect(jsonPath("$.totals.spent").value(1400.0));
            mvc.perform(post("/api/suppliers/" + supplierId + "/payments")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":\"500\",\"date\":\"2026-12-02\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paid").value(500.0))
                    .andExpect(jsonPath("$.debt").value(900.0))
                    .andExpect(jsonPath("$.purchased").value(1400.0));
            mvc.perform(get("/api/finance").param("from", "2026-12-02").param("to", "2026-12-02").session(session))
                    .andExpect(jsonPath("$.totals.spent").value(1400.0))
                    .andExpect(jsonPath("$.totals.payable").value(900.0));
            mvc.perform(delete("/api/suppliers/" + supplierId + "/documents/" + documentId).with(csrf()).session(session))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("confirmed"));
            mvc.perform(post("/api/suppliers/" + supplierId + "/documents/" + documentId + "/cancel")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Ошибочная накладная\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documents[0].cancelled").value(true))
                    .andExpect(jsonPath("$.purchased").value(org.hamcrest.Matchers.nullValue()));
            mvc.perform(get("/api/finance").param("from", "2026-12-02").param("to", "2026-12-02").session(session))
                    .andExpect(jsonPath("$.totals.spent").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.totals.payable").value(org.hamcrest.Matchers.nullValue()));
            mvc.perform(get("/api/suppliers/" + supplierId + "/statement").param("from", "2026-12-01").param("to", "2026-12-31").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        } finally {
            payments.deleteAll();
            lines.deleteAll();
            documents.deleteAll();
            suppliers.deleteAll();
            expenses.deleteAll(expenses.findAll().stream().filter(expense -> "materials".equals(expense.getCategory())).toList());
            objects.deleteAll(objects.findAll().stream().filter(object -> "Объект материалов".equals(object.getAddress())).toList());
            clients.deleteAll(clients.findAll().stream().filter(client -> "Материалы".equals(client.getName())).toList());
            leads.deleteAll(leads.findAll().stream().filter(lead -> "79006660009".equals(lead.getPhone())).toList());
            mvc.perform(get("/api/session").with(request -> {
                WebTestUtils.setCsrfTokenRepository(request, CookieCsrfTokenRepository.withHttpOnlyFalse());
                return request;
            }));
        }
    }
}
