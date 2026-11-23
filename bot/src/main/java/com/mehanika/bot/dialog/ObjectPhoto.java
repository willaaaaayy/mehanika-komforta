package com.mehanika.bot.dialog;

import com.mehanika.bot.lead.LeadPhoto;

public record ObjectPhoto(Kind kind, LeadPhoto photo) {
    public enum Kind {
        OK, TOO_BIG, NOT_IMAGE
    }

    public static ObjectPhoto ok(LeadPhoto photo) {
        return new ObjectPhoto(Kind.OK, photo);
    }

    public static ObjectPhoto tooBig() {
        return new ObjectPhoto(Kind.TOO_BIG, null);
    }

    public static ObjectPhoto notImage() {
        return new ObjectPhoto(Kind.NOT_IMAGE, null);
    }
}
