package ru.mehanikakomforta;

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

    private static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
}
