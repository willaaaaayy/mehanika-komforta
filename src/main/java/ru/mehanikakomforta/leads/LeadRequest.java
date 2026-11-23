package ru.mehanikakomforta.leads;

import java.math.BigDecimal;
import java.util.List;

public record LeadRequest(
        String name,
        String phone,
        String objectType,
        String source,
        TelegramInput telegram,
        BigDecimal floorArea,
        String grade,
        String condition,
        String packageCode,
        List<String> works,
        String city,
        Integer electro,
        Integer water,
        Integer cameras,
        Integer lan,
        Boolean chase,
        BigDecimal heightM,
        List<String> complexItems,
        String complexNote
) {
}
