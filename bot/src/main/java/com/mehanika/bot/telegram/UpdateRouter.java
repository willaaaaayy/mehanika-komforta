package com.mehanika.bot.telegram;

import java.util.List;

import org.springframework.stereotype.Component;

import com.mehanika.bot.dialog.Dialog;
import com.mehanika.bot.dialog.InCallback;
import com.mehanika.bot.dialog.InMessage;
import tools.jackson.databind.JsonNode;

@Component
public class UpdateRouter {
    private final Dialog dialog;

    public UpdateRouter(Dialog dialog) {
        this.dialog = dialog;
    }

    public List<Out> handle(JsonNode update) {
        JsonNode message = update.get("message");
        if (message != null && message.isObject()) {
            return dialog.onMessage(new InMessage(
                    message.path("chat").path("id").asLong(),
                    message.path("message_id").asInt(),
                    message.path("from").path("id").asLong(),
                    textOrNull(message.path("from").path("username")),
                    textOrNull(message.path("text"))));
        }
        JsonNode query = update.get("callback_query");
        if (query != null && query.isObject()) {
            JsonNode origin = query.get("message");
            if (origin == null || !origin.isObject()) {
                return List.of();
            }
            return dialog.onCallback(new InCallback(
                    origin.path("chat").path("id").asLong(),
                    origin.path("message_id").asInt(),
                    query.path("from").path("id").asLong(),
                    textOrNull(query.path("from").path("username")),
                    query.path("id").asText(),
                    query.path("data").asText("")));
        }
        return List.of();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
