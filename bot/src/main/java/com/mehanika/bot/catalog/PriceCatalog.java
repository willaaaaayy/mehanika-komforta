package com.mehanika.bot.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mehanika.bot.Jsons;
import tools.jackson.databind.JsonNode;

public final class PriceCatalog {
    public record Rate(int economy, int standard, int premium, double laborShare) {
        public int price(String grade) {
            return switch (grade) {
                case "economy" -> economy;
                case "standard" -> standard;
                case "premium" -> premium;
                default -> throw new IllegalArgumentException("unknown grade");
            };
        }

        Rate merge(RatePatch patch) {
            if (patch == null) {
                return this;
            }
            return new Rate(
                    patch.economy() == null ? economy : patch.economy(),
                    patch.standard() == null ? standard : patch.standard(),
                    patch.premium() == null ? premium : patch.premium(),
                    patch.laborShare() == null ? laborShare : patch.laborShare());
        }
    }

    public record RatePatch(Integer economy, Integer standard, Integer premium, Double laborShare) {
    }

    public record Work(String id, String title, String unit, String area, Rate rate, String excludes) {
    }

    public record Point(String id, String title, String unit, Rate rate, String note) {
    }

    public record Bundle(String id, String title, List<String> works, String note) {
    }

    public record ClientPatch(Map<String, RatePatch> works, Map<String, RatePatch> points, Map<String, Double> spread) {
    }

    private final boolean teaching;
    private final int validDays;
    private final int minOrder;
    private final double defaultHeight;
    private final double wallFactor;
    private final double chaseLabor;
    private final double highCeiling;
    private final double highHeightM;
    private final List<String> baseExcludes;
    private final Map<String, String> grades;
    private final Map<String, String> objects;
    private final Map<String, String> conditions;
    private final Map<String, Work> works;
    private final Map<String, Bundle> bundles;
    private final Map<String, Point> points;
    private final Map<String, String> complex;
    private final Map<String, String> complexNeeds;
    private final Map<String, Double> spread;
    private final Map<String, ClientPatch> clientOverrides;

    private PriceCatalog(
            boolean teaching,
            int validDays,
            int minOrder,
            double defaultHeight,
            double wallFactor,
            double chaseLabor,
            double highCeiling,
            double highHeightM,
            List<String> baseExcludes,
            Map<String, String> grades,
            Map<String, String> objects,
            Map<String, String> conditions,
            Map<String, Work> works,
            Map<String, Bundle> bundles,
            Map<String, Point> points,
            Map<String, String> complex,
            Map<String, String> complexNeeds,
            Map<String, Double> spread,
            Map<String, ClientPatch> clientOverrides) {
        this.teaching = teaching;
        this.validDays = validDays;
        this.minOrder = minOrder;
        this.defaultHeight = defaultHeight;
        this.wallFactor = wallFactor;
        this.chaseLabor = chaseLabor;
        this.highCeiling = highCeiling;
        this.highHeightM = highHeightM;
        this.baseExcludes = List.copyOf(baseExcludes);
        this.grades = ordered(grades);
        this.objects = ordered(objects);
        this.conditions = ordered(conditions);
        this.works = ordered(works);
        this.bundles = ordered(bundles);
        this.points = ordered(points);
        this.complex = ordered(complex);
        this.complexNeeds = ordered(complexNeeds);
        this.spread = ordered(spread);
        this.clientOverrides = ordered(clientOverrides);
    }

    public static PriceCatalog load(String json) {
        return load(Jsons.MAPPER.readTree(json));
    }

    public static PriceCatalog load(JsonNode root) {
        Map<String, Work> works = new LinkedHashMap<>();
        for (var entry : entries(root.path("works"))) {
            JsonNode node = entry.getValue();
            works.put(entry.getKey(), new Work(
                    entry.getKey(),
                    node.path("title").asText(),
                    node.path("unit").asText(),
                    node.path("area").asText(),
                    rate(node),
                    node.path("excludes").asText()));
        }
        Map<String, Bundle> bundles = new LinkedHashMap<>();
        for (var entry : entries(root.path("packages"))) {
            JsonNode node = entry.getValue();
            List<String> ids = new ArrayList<>();
            for (JsonNode id : node.path("works")) {
                ids.add(id.asText());
            }
            bundles.put(entry.getKey(), new Bundle(entry.getKey(), node.path("title").asText(), List.copyOf(ids), node.path("note").asText()));
        }
        Map<String, Point> points = new LinkedHashMap<>();
        for (var entry : entries(root.path("points"))) {
            JsonNode node = entry.getValue();
            points.put(entry.getKey(), new Point(
                    entry.getKey(),
                    node.path("title").asText(),
                    node.path("unit").asText(),
                    rate(node),
                    node.path("note").asText()));
        }
        Map<String, ClientPatch> overrides = new LinkedHashMap<>();
        for (var entry : entries(root.path("clientOverrides"))) {
            overrides.put(entry.getKey(), clientPatch(entry.getValue()));
        }
        JsonNode teaching = root.path("teaching");
        boolean marked = teaching.isMissingNode() || teaching.isNull() || teaching.asBoolean(true);
        return new PriceCatalog(
                marked,
                root.path("validDays").asInt(),
                root.path("minOrder").asInt(),
                root.path("defaultHeight").asDouble(),
                root.path("wallFactor").asDouble(),
                root.path("chaseLabor").asDouble(),
                root.path("highCeiling").asDouble(),
                root.path("highHeightM").asDouble(),
                strings(root.path("baseExcludes")),
                stringsMap(root.path("grades")),
                stringsMap(root.path("objects")),
                stringsMap(root.path("conditions")),
                works,
                bundles,
                points,
                stringsMap(root.path("complex")),
                stringsMap(root.path("complexNeeds")),
                doubles(root.path("spread")),
                overrides);
    }

    public PriceCatalog forClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return this;
        }
        ClientPatch patch = clientOverrides.get(clientId);
        if (patch == null) {
            return this;
        }
        Map<String, Work> nextWorks = new LinkedHashMap<>();
        for (Work work : works.values()) {
            nextWorks.put(work.id(), new Work(
                    work.id(), work.title(), work.unit(), work.area(), work.rate().merge(patch.works().get(work.id())), work.excludes()));
        }
        Map<String, Point> nextPoints = new LinkedHashMap<>();
        for (Point point : points.values()) {
            nextPoints.put(point.id(), new Point(
                    point.id(), point.title(), point.unit(), point.rate().merge(patch.points().get(point.id())), point.note()));
        }
        Map<String, Double> nextSpread = new LinkedHashMap<>(spread);
        nextSpread.putAll(patch.spread());
        return new PriceCatalog(
                teaching, validDays, minOrder, defaultHeight, wallFactor, chaseLabor, highCeiling, highHeightM,
                baseExcludes, grades, objects, conditions, nextWorks, bundles, nextPoints, complex, complexNeeds,
                nextSpread, clientOverrides);
    }

    public boolean teaching() {
        return teaching;
    }

    public int validDays() {
        return validDays;
    }

    public int minOrder() {
        return minOrder;
    }

    public double defaultHeight() {
        return defaultHeight;
    }

    public double wallFactor() {
        return wallFactor;
    }

    public double chaseLabor() {
        return chaseLabor;
    }

    public double highCeiling() {
        return highCeiling;
    }

    public double highHeightM() {
        return highHeightM;
    }

    public List<String> baseExcludes() {
        return baseExcludes;
    }

    public Map<String, String> grades() {
        return grades;
    }

    public Map<String, String> objects() {
        return objects;
    }

    public Map<String, String> conditions() {
        return conditions;
    }

    public List<Work> works() {
        return List.copyOf(works.values());
    }

    public Work work(String id) {
        Work work = works.get(id);
        if (work == null) {
            throw new IllegalStateException("unknown work");
        }
        return work;
    }

    public Bundle bundle(String id) {
        return bundles.get(id);
    }

    public Point point(String id) {
        Point point = points.get(id);
        if (point == null) {
            throw new IllegalStateException("unknown point");
        }
        return point;
    }

    public Map<String, String> complex() {
        return complex;
    }

    public String complexNeed(String id) {
        return complexNeeds.get(id);
    }

    public double spread(String section, String condition) {
        Double value = spread.get(section + "|" + condition);
        if (value == null) {
            throw new IllegalStateException("unknown spread");
        }
        return value;
    }

    private static Rate rate(JsonNode node) {
        JsonNode prices = node.path("prices");
        return new Rate(
                prices.path("economy").asInt(),
                prices.path("standard").asInt(),
                prices.path("premium").asInt(),
                node.path("laborShare").asDouble());
    }

    private static ClientPatch clientPatch(JsonNode node) {
        Map<String, RatePatch> works = new LinkedHashMap<>();
        for (var entry : entries(node.path("works"))) {
            works.put(entry.getKey(), ratePatch(entry.getValue()));
        }
        Map<String, RatePatch> points = new LinkedHashMap<>();
        for (var entry : entries(node.path("points"))) {
            points.put(entry.getKey(), ratePatch(entry.getValue()));
        }
        return new ClientPatch(ordered(works), ordered(points), doubles(node.path("spread")));
    }

    private static RatePatch ratePatch(JsonNode node) {
        JsonNode prices = node.path("prices");
        return new RatePatch(
                optionalInt(prices, "economy"),
                optionalInt(prices, "standard"),
                optionalInt(prices, "premium"),
                optionalDouble(node, "laborShare"));
    }

    private static Integer optionalInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asInt();
    }

    private static Double optionalDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asDouble();
    }

    private static List<Map.Entry<String, JsonNode>> entries(JsonNode node) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        if (node != null && node.isObject()) {
            entries.addAll(node.properties());
        }
        return entries;
    }

    private static Map<String, String> stringsMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        for (var entry : entries(node)) {
            map.put(entry.getKey(), entry.getValue().asText());
        }
        return map;
    }

    private static Map<String, Double> doubles(JsonNode node) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (var entry : entries(node)) {
            map.put(entry.getKey(), entry.getValue().asDouble());
        }
        return map;
    }

    private static <K, V> Map<K, V> ordered(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.asText());
            }
        }
        return values;
    }
}
