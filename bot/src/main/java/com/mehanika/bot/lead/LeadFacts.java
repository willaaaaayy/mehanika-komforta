package com.mehanika.bot.lead;

import java.util.List;

public record LeadFacts(
        Double floorArea,
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
        Double heightM,
        List<String> complexItems,
        String complexNote
) {
}
