package ru.mehanikakomforta.finance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.web.context.WebApplicationContext;
import ru.mehanikakomforta.clients.ClientRepository;
import ru.mehanikakomforta.leads.LeadRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PayrollTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private EmployeeRepository employees;
    @Autowired
    private WorkLogRepository logs;
    @Autowired
    private SalaryMonthRepository salaries;
    @Autowired
    private PayrollPaymentRepository payments;
    @Autowired
    private ExpenseRepository expenses;
    @Autowired
    private InternalRateRepository rates;
    @Autowired
    private WorkObjectRepository objects;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private LeadRepository leads;

    @Test
    void accrualUsesTheRateOfItsDayAndEntersObjectCost() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        try {
            mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
            MvcResult login = mvc.perform(post("/api/session")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"test-admin-secret\"}"))
                    .andExpect(status().isNoContent())
                    .andReturn();
            MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
            MvcResult lead = mvc.perform(post("/api/leads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Зарплата\",\"phone\":\"79006660008\",\"objectType\":\"Квартира\",\"source\":\"site\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String leadId = lead.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            MvcResult converted = mvc.perform(post("/api/leads/" + leadId + "/convert")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"person\",\"name\":\"Зарплата\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String clientId = converted.getResponse().getContentAsString().replaceFirst(".*?\"clientId\":(\\d+).*", "$1");
            MvcResult created = mvc.perform(post("/api/objects")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientId\":" + clientId + ",\"address\":\"Объект зарплаты\",\"type\":\"квартира\",\"status\":\"prep\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String objectId = created.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            MvcResult crew = mvc.perform(post("/api/employees")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Бригада\",\"payType\":\"piecework\",\"rate\":\"\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String crewId = crew.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            mvc.perform(post("/api/worklogs")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"employeeId\":" + crewId + ",\"date\":\"2026-11-20\",\"object\":\"Объект зарплаты\",\"workId\":\"screed\",\"quantity\":\"2\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/payroll").param("from", "2026-11-20").param("to", "2026-11-20").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Бригада')].accrued", org.hamcrest.Matchers.hasItem(1210.0)))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Бригада')].due", org.hamcrest.Matchers.hasItem(1210.0)));
            mvc.perform(get("/api/finance").param("from", "2026-11-20").param("to", "2026-11-20").session(session))
                    .andExpect(jsonPath("$.totals.spent").value(1210.0))
                    .andExpect(jsonPath("$.totals.payable").value(1210.0))
                    .andExpect(jsonPath("$.expenses[0].category").value("labor"))
                    .andExpect(jsonPath("$.expenses[0].kind").value("object"));
            mvc.perform(get("/api/objects/" + objectId).session(session))
                    .andExpect(jsonPath("$.labor[?(@.employee=='Бригада')].amount", org.hamcrest.Matchers.hasItem(1210.0)));
            String tomorrow = LocalDate.now(ZoneId.of("Europe/Saratov")).plusDays(1).toString();
            mvc.perform(put("/api/internal-rates")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"workId\":\"screed\",\"labor\":\"999\",\"material\":\"1\",\"validFrom\":\"" + tomorrow + "\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/payroll").param("from", "2026-11-20").param("to", "2026-11-20").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Бригада')].accrued", org.hamcrest.Matchers.hasItem(1210.0)));
            mvc.perform(post("/api/worklogs")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"employeeId\":" + crewId + ",\"date\":\"" + tomorrow + "\",\"object\":\"Объект зарплаты\",\"workId\":\"screed\",\"quantity\":\"1\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/payroll").param("from", tomorrow).param("to", tomorrow).session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Бригада')].accrued", org.hamcrest.Matchers.hasItem(999.0)));
            mvc.perform(get("/api/prices").session(session))
                    .andExpect(jsonPath("$.works.screed.prices.standard").value(1100))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("999"))));
            mvc.perform(post("/api/payroll/" + crewId + "/payments")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":\"100\",\"date\":\"2026-11-20\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/payroll").param("from", "2026-11-20").param("to", "2026-11-20").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Бригада')].advance", org.hamcrest.Matchers.hasItem(100.0)))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Бригада')].due", org.hamcrest.Matchers.hasItem(1110.0)));
            mvc.perform(get("/api/finance").param("from", "2026-11-20").param("to", "2026-11-20").session(session))
                    .andExpect(jsonPath("$.totals.spent").value(1210.0))
                    .andExpect(jsonPath("$.totals.payable").value(1110.0));
            MvcResult daily = mvc.perform(post("/api/employees")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Дневной\",\"payType\":\"daily\",\"rate\":\"2000\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            String dailyId = daily.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
            mvc.perform(post("/api/worklogs")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"employeeId\":" + dailyId + ",\"date\":\"2026-11-21\",\"object\":\"Объект зарплаты\",\"quantity\":\"3\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(patch("/api/employees/" + dailyId)
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Дневной\",\"payType\":\"daily\",\"rate\":\"3000\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/payroll").param("from", "2026-11-21").param("to", "2026-11-21").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Дневной')].accrued", org.hamcrest.Matchers.hasItem(6000.0)));
            mvc.perform(post("/api/worklogs")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"employeeId\":" + dailyId + ",\"date\":\"2026-11-22\",\"object\":\"Объект зарплаты\",\"quantity\":\"1\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/payroll").param("from", "2026-11-22").param("to", "2026-11-22").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Дневной')].accrued", org.hamcrest.Matchers.hasItem(3000.0)));
            mvc.perform(post("/api/employees")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Офис\",\"payType\":\"office\",\"rate\":\"40000\"}"))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/payroll").param("from", "2026-10-01").param("to", "2026-10-31").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Офис')].accrued", org.hamcrest.Matchers.hasItem(40000.0)));
            String officeId = employees.findAll().stream().filter(person -> "Офис".equals(person.getName())).findFirst().orElseThrow().getId().toString();
            mvc.perform(patch("/api/employees/" + officeId)
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Офис\",\"payType\":\"office\",\"rate\":\"55000\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/payroll").param("from", "2026-10-01").param("to", "2026-10-31").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Офис')].accrued", org.hamcrest.Matchers.hasItem(40000.0)));
            mvc.perform(get("/api/payroll").param("from", "2026-11-01").param("to", "2026-11-28").session(session))
                    .andExpect(jsonPath("$.rows[?(@.employee=='Офис')].accrued", org.hamcrest.Matchers.hasItem(55000.0)));
            mvc.perform(get("/api/finance").param("from", "2026-11-01").param("to", "2026-11-01").session(session))
                    .andExpect(jsonPath("$.expenses[?(@.category=='salary_office')].kind", org.hamcrest.Matchers.hasItem("overhead")));
            mvc.perform(get("/api/payroll/export").param("format", "xlsx").param("from", "2026-11-20").param("to", "2026-11-20").session(session))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("spreadsheetml")));
            mvc.perform(get("/api/payroll/export").param("format", "pdf").param("from", "2026-11-20").param("to", "2026-11-20").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        } finally {
            payments.deleteAll();
            logs.deleteAll();
            salaries.deleteAll();
            employees.deleteAll();
            expenses.deleteAll(expenses.findAll().stream()
                    .filter(expense -> "labor".equals(expense.getCategory()) || "salary_office".equals(expense.getCategory()))
                    .toList());
            rates.deleteAll(rates.findAll().stream()
                    .filter(rate -> rate.getLabor() != null && rate.getLabor().compareTo(new BigDecimal("999")) == 0)
                    .toList());
            objects.deleteAll(objects.findAll().stream().filter(object -> "Объект зарплаты".equals(object.getAddress())).toList());
            clients.deleteAll(clients.findAll().stream().filter(client -> "Зарплата".equals(client.getName())).toList());
            leads.deleteAll(leads.findAll().stream().filter(lead -> "79006660008".equals(lead.getPhone())).toList());
            mvc.perform(get("/api/session").with(request -> {
                WebTestUtils.setCsrfTokenRepository(request, CookieCsrfTokenRepository.withHttpOnlyFalse());
                return request;
            }));
        }
    }
}
