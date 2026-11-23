package com.mehanika.bot.telegram;

import com.mehanika.bot.dialog.ObjectPhoto;
import tools.jackson.databind.JsonNode;

public interface PhotoIntake {
    ObjectPhoto read(JsonNode message);
}
