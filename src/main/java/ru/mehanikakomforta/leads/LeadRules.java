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
        return null;
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
