package ru.mehanikakomforta.leads;

public record LeadRequest(String name, String phone, String objectType, String source, TelegramInput telegram) {
}
