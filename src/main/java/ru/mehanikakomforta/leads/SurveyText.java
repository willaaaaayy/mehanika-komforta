package ru.mehanikakomforta.leads;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class SurveyText {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private SurveyText() {
    }

    static String check(LeadRequest request, JsonNode prices) {
        String section = request.section() == null ? null : request.section().trim();
        if (section != null && section.isEmpty()) {
            section = null;
        }
        if (section != null && !section.equals("finish") && !section.equals("eng") && !section.equals("both") && !section.equals("complex")) {
            return "section";
        }
        if (request.floorArea() != null
                && (request.floorArea().signum() <= 0 || request.floorArea().compareTo(new BigDecimal("50000")) > 0)) {
            return "floorArea";
        }
        if (request.grade() != null && !request.grade().equals("economy") && !request.grade().equals("standard") && !request.grade().equals("premium")) {
            return "grade";
        }
        if (request.condition() != null && !request.condition().equals("new") && !request.condition().equals("old")) {
            return "condition";
        }
        if (request.packageCode() != null && !request.packageCode().equals("paint") && !request.packageCode().equals("turnkey")) {
            return "packageCode";
        }
        if (request.city() != null) {
            String city = request.city().trim();
            if (city.length() < 2 || city.length() > 80) {
                return "city";
            }
        }
        if (!known(request.works(), prices.get("works"))) {
            return "works";
        }
        if (!count(request.electro())) {
            return "electro";
        }
        if (!count(request.water())) {
            return "water";
        }
        if (!count(request.cameras())) {
            return "cameras";
        }
        if (!count(request.lan())) {
            return "lan";
        }
        if (request.heightM() != null) {
            BigDecimal height = request.heightM().stripTrailingZeros();
            if (height.compareTo(new BigDecimal("2.7")) != 0 && height.compareTo(new BigDecimal("3.2")) != 0) {
                return "heightM";
            }
        }
        if (!known(request.complexItems(), prices.get("complex"))) {
            return "complexItems";
        }
        if (request.complexNote() != null && request.complexNote().trim().length() > 1000) {
            return "complexNote";
        }
        return null;
    }

    static String json(List<String> values) {
        if (values == null) {
            return null;
        }
        return MAPPER.writeValueAsString(values);
    }

    static List<String> list(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        MAPPER.readTree(json).forEach(item -> values.add(item.asString()));
        return values;
    }

    private static boolean known(List<String> values, JsonNode allowed) {
        if (values == null) {
            return true;
        }
        for (String value : values) {
            if (value == null || allowed == null || allowed.get(value) == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean count(Integer value) {
        return value == null || (value >= 0 && value <= 5000);
    }
}
