package com.mehanika.bot;

import tools.jackson.databind.json.JsonMapper;

public final class Jsons {
    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Jsons() {
    }
}
