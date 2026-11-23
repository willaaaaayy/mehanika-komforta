package ru.mehanikakomforta;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm")
public record CrmProperties(String adminPassword, String leadToken) {
}
