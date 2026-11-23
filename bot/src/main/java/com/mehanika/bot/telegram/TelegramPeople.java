package com.mehanika.bot.telegram;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.mehanika.bot.BotSettings;
import com.mehanika.bot.Jsons;
import com.mehanika.bot.dialog.ObjectPhoto;
import com.mehanika.bot.lead.AccountLookup;
import com.mehanika.bot.lead.LeadPhoto;
import com.mehanika.bot.lead.TelegramProfile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class TelegramPeople implements PhotoIntake, AccountLookup {
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TelegramPeople(BotSettings settings) {
        this.token = settings.token() == null ? "" : settings.token().trim();
    }

    @Override
    public ObjectPhoto read(JsonNode message) {
        JsonNode photo = message == null ? null : message.get("photo");
        if (photo != null && photo.isArray() && !photo.isEmpty()) {
            return download(largest(photo), fileSize(largest(photo)));
        }
        JsonNode document = message == null ? null : message.get("document");
        if (document != null && document.isObject()) {
            String mime = document.path("mime_type").asText("");
            if (mime.startsWith("image/")) {
                return download(document.path("file_id").asText(""), document.path("file_size").asInt(0));
            }
        }
        return null;
    }

    @Override
    public TelegramProfile load(long userId, String username, String firstName, String lastName) {
        String bio = "";
        String resolvedFirst = firstName;
        String resolvedLast = lastName;
        String resolvedUser = username;
        LeadPhoto photo = null;
        if (!token.isBlank() && userId > 0) {
            JsonNode chat = call("getChat", node -> node.put("chat_id", userId));
            if (chat != null) {
                JsonNode result = chat.path("result");
                bio = result.path("bio").asText("");
                if (result.path("first_name").isTextual()) {
                    resolvedFirst = result.path("first_name").asText();
                }
                if (result.path("last_name").isTextual()) {
                    resolvedLast = result.path("last_name").asText();
                }
                if (result.path("username").isTextual()) {
                    resolvedUser = result.path("username").asText();
                }
            }
            photo = profilePhoto(userId);
        }
        return new TelegramProfile(userId, resolvedUser, resolvedFirst, resolvedLast, bio, photo);
    }

    private LeadPhoto profilePhoto(long userId) {
        JsonNode response = call("getUserProfilePhotos", node -> {
            node.put("user_id", userId);
            node.put("limit", 1);
        });
        if (response == null) {
            return null;
        }
        JsonNode photos = response.path("result").path("photos");
        if (!photos.isArray() || photos.isEmpty() || !photos.get(0).isArray() || photos.get(0).isEmpty()) {
            return null;
        }
        JsonNode largest = largest(photos.get(0));
        ObjectPhoto downloaded = download(largest.path("file_id").asText(""), largest.path("file_size").asInt(0));
        return downloaded.kind() == ObjectPhoto.Kind.OK ? downloaded.photo() : null;
    }

    private ObjectPhoto download(JsonNode file, int declaredSize) {
        return download(file.path("file_id").asText(""), declaredSize);
    }

    private ObjectPhoto download(String fileId, int declaredSize) {
        if (fileId == null || fileId.isBlank()) {
            return ObjectPhoto.notImage();
        }
        if (declaredSize > LeadPhoto.MAX_BYTES) {
            return ObjectPhoto.tooBig();
        }
        JsonNode info = call("getFile", node -> node.put("file_id", fileId));
        if (info == null) {
            return ObjectPhoto.notImage();
        }
        int size = info.path("result").path("file_size").asInt(declaredSize);
        if (size > LeadPhoto.MAX_BYTES) {
            return ObjectPhoto.tooBig();
        }
        String path = info.path("result").path("file_path").asText("");
        if (path.isBlank() || token.isBlank()) {
            return ObjectPhoto.notImage();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/file/bot" + token + "/" + path))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return ObjectPhoto.notImage();
            }
            byte[] bytes = response.body();
            if (bytes.length > LeadPhoto.MAX_BYTES) {
                return ObjectPhoto.tooBig();
            }
            String type = LeadPhoto.sniff(bytes);
            if (type == null || bytes.length == 0) {
                return ObjectPhoto.notImage();
            }
            return ObjectPhoto.ok(new LeadPhoto(type, bytes));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ObjectPhoto.notImage();
        } catch (Exception ex) {
            return ObjectPhoto.notImage();
        }
    }

    private JsonNode call(String method, java.util.function.Consumer<ObjectNode> fill) {
        if (token.isBlank()) {
            return null;
        }
        try {
            ObjectNode body = Jsons.MAPPER.createObjectNode();
            fill.accept(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Jsons.MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode tree = Jsons.MAPPER.readTree(response.body());
            return tree.path("ok").asBoolean(false) ? tree : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static JsonNode largest(JsonNode sizes) {
        JsonNode best = sizes.get(0);
        int bestSize = best.path("file_size").asInt(0);
        for (JsonNode size : sizes) {
            int candidate = size.path("file_size").asInt(0);
            if (candidate >= bestSize) {
                best = size;
                bestSize = candidate;
            }
        }
        return best;
    }

    private static int fileSize(JsonNode file) {
        return file.path("file_size").asInt(0);
    }
}
