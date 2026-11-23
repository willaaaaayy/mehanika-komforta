package com.mehanika.bot.dialog;

import java.util.ArrayList;
import java.util.List;

public final class Texts {
    private Texts() {
    }

    public static String html(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    public static List<String> pre(String text) {
        int size = 3500;
        List<String> parts = new ArrayList<>();
        if (text.isEmpty()) {
            parts.add(text);
        } else {
            for (int index = 0; index < text.length(); index += size) {
                parts.add(text.substring(index, Math.min(text.length(), index + size)));
            }
        }
        return parts.stream().map(part -> "<pre>" + html(part) + "</pre>").toList();
    }
}
