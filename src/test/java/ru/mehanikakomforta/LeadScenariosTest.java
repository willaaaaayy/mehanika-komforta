package ru.mehanikakomforta;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@SpringBootTest
class LeadScenariosTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void guestCreatesALeadAndCannotReadTheList() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Анна","phone":"+7 900 111-22-33","objectType":"Кофейня","source":"site"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("new"))
                .andExpect(jsonPath("$.source").value("site"))
                .andExpect(jsonPath("$.phone").value("+7 900 111-22-33"));

        mvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("auth"));
    }

    @Test
    void browserLoginUsesTheCsrfCookie() throws Exception {
        MockMvc mvc = mvc();
        MvcResult first = mvc.perform(get("/api/session")).andExpect(status().isUnauthorized()).andReturn();
        String token = first.getResponse().getCookie("XSRF-TOKEN").getValue();
        mvc.perform(post("/api/session")
                        .cookie(first.getResponse().getCookies())
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("password"));
        mvc.perform(post("/api/session")
                        .cookie(first.getResponse().getCookies())
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void wrongPasswordDoesNotOpenTheList() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("password"));
    }

    @Test
    void ownerSeesNewestFirstAndChangesStage() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Первая","phone":"79001112233","objectType":"Автомойка","source":"site"}
                                """))
                .andExpect(status().isCreated());
        Thread.sleep(5);
        MvcResult created = mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Вторая","phone":"79001112234","objectType":"Клиника","source":"site"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        mvc.perform(get("/api/leads").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Вторая"));

        mvc.perform(patch("/api/leads/" + id)
                        .session(session(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"warranty\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.stage").value("warranty"));
    }

    @Test
    void ownerAddsAManualLead() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Сам","phone":"79000000000","objectType":"Офис","source":"manual"}
                                """))
                .andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        mvc.perform(post("/api/leads")
                        .session(session(login))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Сам","phone":"79000000000","objectType":"Офис","source":"manual"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("manual"))
                .andExpect(jsonPath("$.stage").value("new"));
    }

    @Test
    void telegramNeedsTheToken() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Из бота","phone":"79001110000","objectType":"Квартира","source":"telegram"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token"));

        mvc.perform(post("/api/leads")
                        .header("X-Lead-Token", "test-lead-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Из бота","phone":"79001110000","objectType":"Квартира","source":"telegram"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("telegram"));
    }

    @Test
    void badPhoneIsRejected() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Анна","phone":"123","objectType":"Кофейня","source":"site"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("phone"));
    }

    @Test
    void logoutClosesTheList() throws Exception {
        MockMvc mvc = mvc();
        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession loggedIn = session(login);
        mvc.perform(get("/api/session").session(loggedIn))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/session").with(csrf()).session(loggedIn))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/leads").session(loggedIn))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void botPhotoIsPrivateToTheOwner() throws Exception {
        MockMvc mvc = mvc();
        MvcResult created = mvc.perform(post("/api/leads")
                        .header("X-Lead-Token", "test-lead-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"С фото","phone":"79001112255","objectType":"Офис","source":"telegram"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String leadId = created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
        MockMultipartFile photo = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg);

        mvc.perform(multipart("/api/leads/" + leadId + "/photos").file(photo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token"));
        mvc.perform(multipart("/api/leads/" + leadId + "/photos").file(photo).header("X-Lead-Token", "nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token"));
        mvc.perform(multipart("/api/leads/999999/photos").file(photo).header("X-Lead-Token", "test-lead-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("id"));
        mvc.perform(multipart("/api/leads/" + leadId + "/photos")
                        .file(new MockMultipartFile("file", "photo.png", "image/jpeg", jpeg))
                        .header("X-Lead-Token", "test-lead-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("photo"));
        mvc.perform(multipart("/api/leads/" + leadId + "/photos")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]))
                        .header("X-Lead-Token", "test-lead-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("photo"));

        MvcResult stored = mvc.perform(multipart("/api/leads/" + leadId + "/photos")
                        .file(photo)
                        .header("X-Lead-Token", "test-lead-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        String photoId = stored.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/leads/" + leadId + "/photos/" + photoId))
                .andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(get("/api/leads/" + leadId + "/photos").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(Integer.parseInt(photoId)));
        mvc.perform(get("/api/leads/" + leadId + "/photos/" + photoId).session(session(login)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(jpeg));

        for (int i = 0; i < 9; i++) {
            mvc.perform(multipart("/api/leads/" + leadId + "/photos").file(photo).header("X-Lead-Token", "test-lead-token"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(multipart("/api/leads/" + leadId + "/photos").file(photo).header("X-Lead-Token", "test-lead-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("photo"));
    }

    @Test
    void oldLeadBodyStillWorksAndTelegramStaysBesideIt() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Без аккаунта","phone":"79001112266","objectType":"Офис","source":"site"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Без аккаунта"))
                .andExpect(jsonPath("$.telegram").doesNotExist());

        MvcResult created = mvc.perform(post("/api/leads")
                        .header("X-Lead-Token", "test-lead-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Из диалога","phone":"79001112277","objectType":"Квартира","source":"telegram","telegram":{"id":123456789,"username":"","firstName":"Анна","lastName":"","bio":""}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Из диалога"))
                .andExpect(jsonPath("$.telegram.id").value(123456789))
                .andExpect(jsonPath("$.telegram.username").value(""))
                .andExpect(jsonPath("$.telegram.photo").value(false))
                .andReturn();
        String leadId = created.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
        MockMultipartFile portrait = new MockMultipartFile("file", "portrait.jpg", "image/jpeg", jpeg);
        mvc.perform(multipart("/api/leads/" + leadId + "/telegram-photo").file(portrait))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token"));
        mvc.perform(multipart("/api/leads/" + leadId + "/telegram-photo").file(portrait).header("X-Lead-Token", "test-lead-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
        mvc.perform(get("/api/leads/" + leadId + "/telegram-photo"))
                .andExpect(status().isUnauthorized());
        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(get("/api/leads").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Из диалога')].telegram.photo").value(true));
        mvc.perform(get("/api/leads/" + leadId + "/telegram-photo").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }

    @Test
    void ownerEditsAreaAndSeesTheBotPriceFork() throws Exception {
        MockMvc mvc = mvc();
        MvcResult created = mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Расчёт","phone":"79001112299","objectType":"Офис","source":"site"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(patch("/api/leads/" + id)
                        .session(session(login))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"floorArea":40,"grade":"economy","condition":"new","packageCode":"paint"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teaching").value(true))
                .andExpect(jsonPath("$.estimate.low").isNumber())
                .andExpect(jsonPath("$.estimate.high").isNumber());
        mvc.perform(put("/api/prices").with(csrf()).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerUploadsExcelAndPdfIntoTheBotPrice() throws Exception {
        MockMvc mvc = mvc();
        byte[] sheet;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var table = workbook.createSheet();
            var header = table.createRow(0);
            header.createCell(0).setCellValue("Работа");
            header.createCell(1).setCellValue("Эконом");
            header.createCell(2).setCellValue("Стандарт");
            header.createCell(3).setCellValue("Премиум");
            var screed = table.createRow(1);
            screed.createCell(0).setCellValue("Стяжка пола");
            screed.createCell(1).setCellValue(1000);
            screed.createCell(2).setCellValue(2000);
            screed.createCell(3).setCellValue(3000);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            sheet = out.toByteArray();
        }
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf").toFile());
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Штукатурка стен по маякам 111 222 333");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }
        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(multipart("/api/prices").file(new MockMultipartFile("file", "prices.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sheet)).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/prices")
                        .file(new MockMultipartFile("file", "prices.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sheet))
                        .session(session(login))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teaching").value(false))
                .andExpect(jsonPath("$.works.screed.prices.economy").value(1000))
                .andExpect(jsonPath("$.works.plaster.prices.economy").value(700));
        mvc.perform(multipart("/api/prices")
                        .file(new MockMultipartFile("file", "prices.pdf", "application/pdf", pdf))
                        .session(session(login))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.works.plaster.prices.economy").value(111))
                .andExpect(jsonPath("$.works.plaster.prices.premium").value(333))
                .andExpect(jsonPath("$.works.screed.prices.economy").value(1000));
        mvc.perform(multipart("/api/prices")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII)))
                        .session(session(login))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("prices"));
        String original = new String(getClass().getResourceAsStream("/policies/prices.json").readAllBytes(), StandardCharsets.UTF_8);
        mvc.perform(put("/api/prices")
                        .session(session(login))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teaching").value(true));
    }

    @Test
    void botSavesTheFullDialogAndOldLeadsStayIntact() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Старая","phone":"79001110000","objectType":"Офис","source":"site"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city").doesNotExist())
                .andExpect(jsonPath("$.works").doesNotExist())
                .andExpect(jsonPath("$.floorArea").doesNotExist());
        mvc.perform(post("/api/leads")
                        .header("X-Lead-Token", "test-lead-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Полный","phone":"79001110001","objectType":"Квартира","source":"telegram","section":"both","floorArea":42.5,"grade":"standard","condition":"old","packageCode":null,"works":["screed","plaster"],"city":"Казань","electro":4,"water":null,"cameras":null,"lan":null,"chase":true,"heightM":3.2,"complexItems":null,"complexNote":null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.section").value("both"))
                .andExpect(jsonPath("$.floorArea").value(42.5))
                .andExpect(jsonPath("$.grade").value("standard"))
                .andExpect(jsonPath("$.condition").value("old"))
                .andExpect(jsonPath("$.packageCode").doesNotExist())
                .andExpect(jsonPath("$.works[0]").value("screed"))
                .andExpect(jsonPath("$.city").value("Казань"))
                .andExpect(jsonPath("$.electro").value(4))
                .andExpect(jsonPath("$.water").doesNotExist())
                .andExpect(jsonPath("$.chase").value(true))
                .andExpect(jsonPath("$.heightM").value(3.2));
    }

    @Test
    void ownerConfirmsAContractAndSeesItInFinance() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("ДОГОВОР № 11-10-1-69");
            document.createParagraph().createRun().setText("Заказчик поручает выполнить работы на объекте: «Дом в районе Солнечный», б/с В, этаж 17.");
            document.createParagraph().createRun().setText("Ориентировочная стоимость работ составляет 1 062 500 руб. 00 коп.");
            document.createParagraph().createRun().setText("авансовый платеж в размере 50% (531 250 руб. 00 коп.)");
            document.createParagraph().createRun().setText("ЗАКАЗЧИК");
            document.createParagraph().createRun().setText("ООО СЗ ГК «Кронверк»");
            document.createParagraph().createRun().setText("ИНН 6450096705");
            document.createParagraph().createRun().setText("ПОДРЯДЧИК");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            docx = out.toByteArray();
        }
        MockMvc mvc = mvc();
        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(multipart("/api/contracts")
                        .file(new MockMultipartFile("file", "dogovor.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        MvcResult uploaded = mvc.perform(multipart("/api/contracts")
                        .file(new MockMultipartFile("file", "dogovor.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx))
                        .session(session(login))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("11-10-1-69"))
                .andExpect(jsonPath("$.amount").value(1062500))
                .andExpect(jsonPath("$.client.name").value("ООО СЗ ГК «Кронверк»"))
                .andExpect(jsonPath("$.schedule[0].kind").value("advance"))
                .andExpect(jsonPath("$.schedule[1].amount").value(531250))
                .andReturn();
        String id = uploaded.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
        mvc.perform(get("/api/finance").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients").isEmpty());
        mvc.perform(patch("/api/contracts/" + id)
                        .session(session(login))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploaded.getResponse().getContentAsString()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/finance").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.receivable").value(1062500))
                .andExpect(jsonPath("$.totals.payable").doesNotExist())
                .andExpect(jsonPath("$.clients[0].name").value("ООО СЗ ГК «Кронверк»"))
                .andExpect(jsonPath("$.clients[0].items[0].object").value(org.hamcrest.Matchers.containsString("этаж 17")));
        byte[] pdf;
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            org.apache.pdfbox.pdmodel.font.PDType0Font font = org.apache.pdfbox.pdmodel.font.PDType0Font.load(
                    document, java.nio.file.Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf").toFile());
            try (org.apache.pdfbox.pdmodel.PDPageContentStream stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 11);
                stream.setLeading(16);
                stream.newLineAtOffset(40, 760);
                for (String line : java.util.List.of(
                        "ДОГОВОР № 12-01-2-70",
                        "на объекте: «Дом в районе Солнечный», б/с В, этаж 17",
                        "Ориентировочная стоимость работ составляет 1 062 500 (Один миллион) руб. 00 коп.",
                        "авансовый платеж в размере 50% (531 250 (Пятьсот) руб. 00 коп.)",
                        "ЗАКАЗЧИК",
                        "ООО СЗ ГК «Кронверк»",
                        "ИНН 6450096705",
                        "ПОДРЯДЧИК"
                )) {
                    stream.showText(line);
                    stream.newLine();
                }
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }
        mvc.perform(multipart("/api/contracts")
                        .file(new MockMultipartFile("file", "dogovor.pdf", "application/pdf", pdf))
                        .session(session(login))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("12-01-2-70"))
                .andExpect(jsonPath("$.amount").value(1062500))
                .andExpect(jsonPath("$.schedule[0].kind").value("advance"));
    }

    @Test
    void directorConvertsALeadAndCanReturnIt() throws Exception {
        MockMvc mvc = mvc();
        MvcResult created = mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Клиент","phone":"+7 900 111-22-33","objectType":"Офис","source":"site"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = created.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
        mvc.perform(post("/api/leads/" + id + "/convert").content("{}"))
                .andExpect(status().isUnauthorized());
        MvcResult login = mvc.perform(post("/api/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(post("/api/leads/" + id + "/convert")
                        .session(session(login))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merge":false,"type":"company","name":"Клиент","phone":"+7 900 111-22-33","inn":"7700000001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").isNumber());
        mvc.perform(get("/api/leads").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Клиент')].clientId").isNotEmpty());
        mvc.perform(get("/api/leads").param("converted", "true").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Клиент')].clientId").isNotEmpty());
        mvc.perform(post("/api/leads/" + id + "/convert")
                        .session(session(login))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merge\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("converted"));
        MvcResult second = mvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Другой","phone":"79009998877","objectType":"Дом","source":"site"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = second.getResponse().getContentAsString().replaceFirst(".*?\"id\":(\\d+).*", "$1");
        mvc.perform(post("/api/leads/" + secondId + "/convert")
                        .session(session(login))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merge\":false,\"inn\":\"7700000001\",\"name\":\"Другой\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate"))
                .andExpect(jsonPath("$.clientId").isNumber());
        mvc.perform(get("/api/clients/duplicates").param("phone", "8 (900) 111-22-33").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Клиент"));
        mvc.perform(post("/api/leads/" + id + "/unconvert").session(session(login)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/leads").session(session(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Клиент')].stage").value("new"));
    }

    private static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
}
