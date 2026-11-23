package ru.mehanikakomforta.leads;

public final class LeadRules {

    private LeadRules() {
    }

    public static String check(LeadRequest request) {
        if (request == null) {
            return "name";
        }
        String name = clean(request.name());
        if (name.length() < 2 || name.length() > 80) {
            return "name";
        }
        String phone = clean(request.phone());
        if (phone.length() > 40 || digits(phone) < 10 || digits(phone) > 15) {
            return "phone";
        }
        String objectType = clean(request.objectType());
        if (objectType.length() < 2 || objectType.length() > 80) {
            return "objectType";
        }
        if (source(request.source()) == null) {
            return "source";
        }
        return telegram(request.telegram());
    }

    public static String telegram(TelegramInput telegram) {
        if (telegram == null) {
            return null;
        }
        if (telegram.id() == null) {
            return "telegram";
        }
        String username = text(telegram.username(), 32);
        String firstName = text(telegram.firstName(), 64);
        String lastName = text(telegram.lastName(), 64);
        String bio = text(telegram.bio(), 70);
        if (username == null || firstName == null || lastName == null || bio == null) {
            return "telegram";
        }
        if (username.startsWith("@")) {
            return "telegram";
        }
        return null;
    }

    public static String username(TelegramInput telegram) {
        return text(telegram.username(), 32);
    }

    public static String firstName(TelegramInput telegram) {
        return text(telegram.firstName(), 64);
    }

    public static String lastName(TelegramInput telegram) {
        return text(telegram.lastName(), 64);
    }

    public static String bio(TelegramInput telegram) {
        return text(telegram.bio(), 70);
    }

    private static String text(String value, int max) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.length() > max ? null : cleaned;
    }

    public static LeadSource source(String raw) {
        if (raw == null) {
            return null;
        }
        for (LeadSource source : LeadSource.values()) {
            if (source.name().equals(raw)) {
                return source;
            }
        }
        return null;
    }

    public static String name(LeadRequest request) {
        return clean(request.name());
    }

    public static String phone(LeadRequest request) {
        return clean(request.phone());
    }

    public static String objectType(LeadRequest request) {
        return clean(request.objectType());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static int digits(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
