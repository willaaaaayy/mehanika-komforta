package com.mehanika.bot.lead;

public record LeadPhoto(String contentType, byte[] bytes) {
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    public static String sniff(byte[] body) {
        if (body == null) {
            return null;
        }
        if (body.length >= 3 && (body[0] & 0xff) == 0xff && (body[1] & 0xff) == 0xd8 && (body[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (body.length >= 4 && (body[0] & 0xff) == 0x89 && body[1] == 0x50 && body[2] == 0x4e && body[3] == 0x47) {
            return "image/png";
        }
        if (body.length >= 12
                && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'E' && body[10] == 'B' && body[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public static String filename(String contentType) {
        return switch (contentType) {
            case "image/png" -> "photo.png";
            case "image/webp" -> "photo.webp";
            default -> "photo.jpg";
        };
    }
}
