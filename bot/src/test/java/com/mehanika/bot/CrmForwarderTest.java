package com.mehanika.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.mehanika.bot.catalog.CatalogRepository;
import com.mehanika.bot.dialog.Dialog;
import com.mehanika.bot.lead.CrmForwarder;
import com.mehanika.bot.lead.CrmLeadSink;
import com.mehanika.bot.lead.LeadFacts;
import com.mehanika.bot.lead.LeadPhoto;
import com.mehanika.bot.lead.TelegramProfile;
import com.mehanika.bot.lead.LeadStore;
import com.sun.net.httpserver.HttpServer;

class CrmForwarderTest {
    @Test
    void postsNamePhoneAndObjectTypeWithTheLeadToken() throws IOException {
        List<String> bodies = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/leads", exchange -> {
            tokens.add(exchange.getRequestHeaders().getFirst("X-Lead-Token"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, ok.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(ok);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/leads";
            Path marker = Files.createTempFile("crm", ".crm");
            CrmForwarder forwarder = new CrmForwarder(url, "test-lead-token", marker);
            assertEquals(CrmLeadSink.Result.SENT, forwarder.forward("Иван", "+7 (900) 000-00-00", "Квартира"));
            assertEquals(List.of("test-lead-token"), tokens);
            assertTrue(bodies.get(0).contains("\"source\":\"telegram\""));
            assertTrue(bodies.get(0).contains("\"objectType\":\"Квартира\""));
            assertTrue(!bodies.get(0).contains("Вилка"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deliverPostsAccountAndBothPhotoSlots() throws IOException {
        List<String> paths = new ArrayList<>();
        List<String> types = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/leads", exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (path.endsWith("/photos") || path.endsWith("/telegram-photo")) {
                String payload = new String(request, StandardCharsets.ISO_8859_1);
                types.add(payload.contains("filename=\"photo.jpg\"") ? "photo.jpg" : "other");
            } else {
                String json = new String(request, StandardCharsets.UTF_8);
                types.add(json.contains("\"username\":\"anna\"")
                        && json.contains("\"firstName\":\"Анна\"")
                        && json.contains("\"floorArea\":40.0")
                        && json.contains("\"city\":\"Казань\"")
                        && json.contains("\"packageCode\":\"paint\"")
                        && json.contains("\"chase\":false")
                        ? "account" : "lead");
            }
            byte[] ok = "{\"id\":7}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, ok.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(ok);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/leads";
            CrmForwarder forwarder = new CrmForwarder(url, "test-lead-token", Files.createTempFile("crm", ".crm"));
            byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
            assertEquals(CrmLeadSink.Result.SENT, forwarder.deliver(
                    "Анна", "9000000000", "Квартира",
                    new LeadFacts(40.0, "standard", "new", "paint", List.of("screed"), "Казань",
                            12, 4, 0, 2, false, 2.7, null, null),
                    new TelegramProfile(42, "anna", "Анна", "", "коротко", new LeadPhoto("image/jpeg", jpeg)),
                    List.of(new LeadPhoto("image/jpeg", jpeg))));
            assertEquals(List.of("/api/leads", "/api/leads/7/photos", "/api/leads/7/telegram-photo"), paths);
            assertEquals(List.of("account", "photo.jpg", "photo.jpg"), types);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void crmOutageDoesNotCancelTheClientReply() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/leads", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":\"token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            Path leads = Files.createTempFile("leads", ".jsonl");
            Path marker = Files.createTempFile("crm", ".crm");
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/leads";
            CrmForwarder forwarder = new CrmForwarder(url, "test-lead-token", marker);
            Dialog dialog = new Dialog(new CatalogRepository("file:src/main/resources/prices.json"), new LeadStore(leads), 77L, forwarder);
            dialog.onMessage(new com.mehanika.bot.dialog.InMessage(42, 1, 42, "tester", "/start"));
            dialog.onCallback(new com.mehanika.bot.dialog.InCallback(42, 1, 42, "tester", "cb", "sec:finish"));
            dialog.onCallback(new com.mehanika.bot.dialog.InCallback(42, 1, 42, "tester", "cb", "ob:flat"));
            dialog.onCallback(new com.mehanika.bot.dialog.InCallback(42, 1, 42, "tester", "cb", "cd:new"));
            dialog.onMessage(new com.mehanika.bot.dialog.InMessage(42, 1, 42, "tester", "Казань"));
            dialog.onCallback(new com.mehanika.bot.dialog.InCallback(42, 1, 42, "tester", "cb", "gr:economy"));
            dialog.onMessage(new com.mehanika.bot.dialog.InMessage(42, 1, 42, "tester", "10"));
            dialog.onCallback(new com.mehanika.bot.dialog.InCallback(42, 1, 42, "tester", "cb", "fm:paint"));
            dialog.onMessage(new com.mehanika.bot.dialog.InMessage(42, 1, 42, "tester", "Иван"));
            dialog.onMessage(new com.mehanika.bot.dialog.InMessage(42, 1, 42, "tester", "9000000000"));
            dialog.onCallback(new com.mehanika.bot.dialog.InCallback(42, 1, 42, "tester", "cb", "ph:done"));
            String reply = dialog.onCallback(new com.mehanika.bot.dialog.InCallback(42, 1, 42, "tester", "cb", "ok:1"))
                    .stream()
                    .filter(out -> out instanceof com.mehanika.bot.telegram.Out.Send)
                    .map(out -> ((com.mehanika.bot.telegram.Out.Send) out).html())
                    .reduce("", (a, b) -> a + b);
            assertTrue(reply.contains("Заявку записал."));
            assertEquals(1, calls.get());
            assertEquals(1, Files.readAllLines(leads).size());
            assertEquals(0, Files.readAllLines(marker).size());
        } finally {
            server.stop(0);
        }
    }
}
