package com.mehanika.bot.telegram;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mehanika.bot.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class TelegramClient {
    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base;

    public TelegramClient(String token) {
        this.base = "https://api.telegram.org/bot" + token + "/";
    }

    public String username() {
        return call("getMe", null, Duration.ofSeconds(20)).path("result").path("username").asText();
    }

    public void deleteWebhook() {
        ObjectNode body = Jsons.MAPPER.createObjectNode();
        body.put("drop_pending_updates", false);
        call("deleteWebhook", body, Duration.ofSeconds(20));
    }

    public List<JsonNode> getUpdates(long offset) {
        ObjectNode body = Jsons.MAPPER.createObjectNode();
        body.put("timeout", 25);
        if (offset > 0) {
            body.put("offset", offset);
        }
        ArrayNode allowed = body.putArray("allowed_updates");
        allowed.add("message");
        allowed.add("callback_query");
        JsonNode response = call("getUpdates", body, Duration.ofSeconds(40));
        List<JsonNode> batch = new ArrayList<>();
        for (JsonNode update : response.path("result")) {
            batch.add(update);
        }
        return batch;
    }

    public void perform(List<Out> outs) {
        for (Out out : outs) {
            try {
                switch (out) {
                    case Out.Send send -> send(send);
                    case Out.Markup markup -> markup(markup);
                    case Out.Toast toast -> toast(toast);
                }
            } catch (RuntimeException ex) {
                if (out instanceof Out.Send send && send.bestEffort()) {
                    log.warn("admin notify failed ({})", ex.getClass().getSimpleName());
                } else {
                    log.warn("telegram call failed ({})", ex.getClass().getSimpleName());
                }
            }
        }
    }

    public void close() {
        http.close();
    }

    private void send(Out.Send send) {
        ObjectNode body = Jsons.MAPPER.createObjectNode();
        body.put("chat_id", send.chatId());
        body.put("text", send.html());
        body.put("parse_mode", "HTML");
        if (send.keyboard() != null) {
            body.set("reply_markup", markupNode(send.keyboard()));
        }
        call("sendMessage", body, Duration.ofSeconds(20));
    }

    private void markup(Out.Markup markup) {
        ObjectNode body = Jsons.MAPPER.createObjectNode();
        body.put("chat_id", markup.chatId());
        body.put("message_id", markup.messageId());
        body.set("reply_markup", markupNode(markup.keyboard() == null ? Keyboard.CLEAR : markup.keyboard()));
        call("editMessageReplyMarkup", body, Duration.ofSeconds(20));
    }

    private void toast(Out.Toast toast) {
        ObjectNode body = Jsons.MAPPER.createObjectNode();
        body.put("callback_query_id", toast.callbackId());
        if (toast.text() != null) {
            body.put("text", toast.text());
        }
        call("answerCallbackQuery", body, Duration.ofSeconds(20));
    }

    private ObjectNode markupNode(Keyboard keyboard) {
        ObjectNode node = Jsons.MAPPER.createObjectNode();
        ArrayNode rows = node.putArray("inline_keyboard");
        for (List<Keyboard.Button> row : keyboard.rows()) {
            ArrayNode buttons = rows.addArray();
            for (Keyboard.Button button : row) {
                buttons.addObject().put("text", button.text()).put("callback_data", button.data());
            }
        }
        return node;
    }

    private JsonNode call(String method, JsonNode body, Duration timeout) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + method)).timeout(timeout);
            if (body == null) {
                request.GET();
            } else {
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(Jsons.MAPPER.writeValueAsString(body)));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 409) {
                throw new Conflict();
            }
            JsonNode tree = Jsons.MAPPER.readTree(response.body());
            if (!tree.path("ok").asBoolean(false)) {
                int code = tree.path("error_code").asInt(status);
                if (code == 409) {
                    throw new Conflict();
                }
                throw new TelegramFailure(method, code == 0 ? status : code);
            }
            return tree;
        } catch (Conflict | TelegramFailure ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TelegramFailure(method, 0);
        }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super("conflict");
        }
    }

    public static final class TelegramFailure extends RuntimeException {
        public TelegramFailure(String method, int status) {
            super(method + " http " + status);
        }
    }
}
