package ru.mehanikakomforta;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class InternalRateTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void futureRateDoesNotChangeTodayAndStaysOutOfTheClientCatalog() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        String catalog = mvc.perform(get("/api/prices").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(get("/api/internal-rates"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal-rates").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teaching").value(true))
                .andExpect(jsonPath("$.rates[?(@.workId=='screed')].labor", org.hamcrest.Matchers.hasItem(605.0)))
                .andExpect(jsonPath("$.rates[?(@.workId=='screed')].material", org.hamcrest.Matchers.hasItem(165.0)))
                .andExpect(jsonPath("$.rates[?(@.workId=='screed')].clientPrice", org.hamcrest.Matchers.hasItem(1100)))
                .andExpect(jsonPath("$.rates[?(@.workId=='screed')].margin", org.hamcrest.Matchers.hasItem(330.0)))
                .andExpect(jsonPath("$.rates[?(@.workId=='screed')].teaching", org.hamcrest.Matchers.hasItem(true)));
        String tomorrow = LocalDate.now(ZoneId.of("Europe/Saratov")).plusDays(1).toString();
        mvc.perform(put("/api/internal-rates")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workId\":\"screed\",\"title\":\"Стяжка\",\"unit\":\"м²\",\"labor\":\"123456\",\"material\":\"1\",\"validFrom\":\"" + tomorrow + "\",\"grade\":\"standard\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/internal-rates?grade=standard").session(session))
                .andExpect(jsonPath("$.rates[?(@.workId=='screed')].labor", org.hamcrest.Matchers.hasItem(605.0)))
                .andExpect(jsonPath("$.rates[?(@.workId=='screed')].version", org.hamcrest.Matchers.hasItem(1)));
        mvc.perform(get("/api/prices").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works.screed.prices.standard").value(1100));
        org.junit.jupiter.api.Assertions.assertFalse(
                mvc.perform(get("/api/prices").session(session)).andReturn().getResponse().getContentAsString().contains("123456")
        );
        org.junit.jupiter.api.Assertions.assertTrue(catalog.contains("\"teaching\""));
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            var sheet = book.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("workId");
            header.createCell(1).setCellValue("title");
            header.createCell(2).setCellValue("unit");
            header.createCell(3).setCellValue("labor");
            header.createCell(4).setCellValue("material");
            header.createCell(5).setCellValue("validFrom");
            var broken = sheet.createRow(1);
            broken.createCell(0).setCellValue("custom_gap");
            broken.createCell(1).setCellValue("");
            var ready = sheet.createRow(2);
            ready.createCell(0).setCellValue("custom_ok");
            ready.createCell(1).setCellValue("Своя");
            ready.createCell(2).setCellValue("м²");
            ready.createCell(3).setCellValue(40);
            ready.createCell(4).setCellValue(10);
            ready.createCell(5).setCellValue(LocalDate.now(ZoneId.of("Europe/Saratov")).toString());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            book.write(bytes);
            MockMultipartFile file = new MockMultipartFile("file", "rates.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
            mvc.perform(multipart("/api/internal-rates/import").file(file).param("preview", "true").with(csrf()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors[0].row").value(2))
                    .andExpect(jsonPath("$.rows[0].workId").value("custom_ok"));
            mvc.perform(get("/api/internal-rates").session(session))
                    .andExpect(jsonPath("$.rates[?(@.workId=='custom_ok')]").isEmpty());
            mvc.perform(multipart("/api/internal-rates/import")
                            .param("preview", "false")
                            .param("rows", "[{\"workId\":\"custom_ok\",\"title\":\"Своя\",\"unit\":\"м²\",\"labor\":40,\"material\":10,\"validFrom\":\"" + LocalDate.now(ZoneId.of("Europe/Saratov")) + "\"}]")
                            .with(csrf())
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rates[?(@.workId=='custom_ok')].teaching", org.hamcrest.Matchers.hasItem(true)))
                    .andExpect(jsonPath("$.rates[?(@.workId=='custom_ok')].labor", org.hamcrest.Matchers.hasItem(40.0)));
        }
        mvc.perform(get("/api/prices").session(session))
                .andExpect(jsonPath("$.works.custom_ok").doesNotExist());
        MvcResult lead = mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Прайс\",\"phone\":\"79007770007\",\"objectType\":\"Квартира\",\"source\":\"site\",\"works\":[\"screed\"],\"grade\":\"standard\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String leadId = lead.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
        MvcResult converted = mvc.perform(post("/api/leads/" + leadId + "/convert")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"person\",\"name\":\"Прайс\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String clientId = converted.getResponse().getContentAsString().replaceFirst(".*?\"clientId\":(\\d+).*", "$1");
        MvcResult created = mvc.perform(post("/api/objects")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + clientId + ",\"address\":\"Объект прайса\",\"type\":\"квартира\",\"status\":\"prep\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String objectId = created.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
        mvc.perform(get("/api/objects/" + objectId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threshold").value(10))
                .andExpect(jsonPath("$.costs[?(@.title)].plan", org.hamcrest.Matchers.hasItem(770.0)))
                .andExpect(jsonPath("$.costs[0].fact").doesNotExist())
                .andExpect(jsonPath("$.costs[0].over").value(false));
    }
}
