package com.mehanika.bot.telegram;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mehanika.bot.dialog.Dialog;
import com.mehanika.bot.dialog.InCallback;
import com.mehanika.bot.dialog.InMessage;
import com.mehanika.bot.dialog.ObjectPhoto;
import tools.jackson.databind.JsonNode;

@Component
public class UpdateRouter {
    private final Dialog dialog;
    private final PhotoIntake photos;

    @Autowired
    public UpdateRouter(Dialog dialog, PhotoIntake photos) {
        this.dialog = dialog;
        this.photos = photos;
    }

    public UpdateRouter(Dialog dialog) {
        this(dialog, message -> null);
    }

    public List<Out> handle(JsonNode update) {
        JsonNode message = update.get("message");
        if (message != null && message.isObject()) {
            long chatId = message.path("chat").path("id").asLong();
            long userId = message.path("from").path("id").asLong();
            String username = textOrNull(message.path("from").path("username"));
            String firstName = textOrNull(message.path("from").path("first_name"));
            String lastName = textOrNull(message.path("from").path("last_name"));
            ObjectPhoto photo = photos.read(message);
            List<Out> outs = photo == null
                    ? dialog.onMessage(new InMessage(chatId, message.path("message_id").asInt(), userId, username, textOrNull(message.path("text"))))
                    : dialog.onObjectPhoto(chatId, photo);
            dialog.noteUser(chatId, userId, username, firstName, lastName);
            return outs;
        }
        JsonNode query = update.get("callback_query");
        if (query != null && query.isObject()) {
            JsonNode origin = query.get("message");
            if (origin == null || !origin.isObject()) {
                return List.of();
            }
            long chatId = origin.path("chat").path("id").asLong();
            List<Out> outs = dialog.onCallback(new InCallback(
                    chatId,
                    origin.path("message_id").asInt(),
                    query.path("from").path("id").asLong(),
                    textOrNull(query.path("from").path("username")),
                    query.path("id").asText(),
                    query.path("data").asText("")));
            dialog.noteUser(chatId, query.path("from").path("id").asLong(),
                    textOrNull(query.path("from").path("username")),
                    textOrNull(query.path("from").path("first_name")),
                    textOrNull(query.path("from").path("last_name")));
            return outs;
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
