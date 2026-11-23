package com.mehanika.bot.telegram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record Keyboard(List<List<Button>> rows) {
    public record Button(String text, String data) {
    }

    public static final Keyboard CLEAR = new Keyboard(List.of());

    public Keyboard {
        rows = List.copyOf(rows);
    }

    @SafeVarargs
    public static Keyboard of(List<Button>... rows) {
        return new Keyboard(Arrays.asList(rows));
    }

    public static Keyboard from(List<List<Button>> rows) {
        return new Keyboard(new ArrayList<>(rows));
    }
}
