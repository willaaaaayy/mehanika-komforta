package com.mehanika.bot.calc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.mehanika.bot.catalog.PriceCatalog;

public final class Estimator {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.uuuu");

    public record Job(
            String section,
            String objectType,
            String condition,
            String city,
            String grade,
            Double floorArea,
            String finishMode,
            List<String> works,
            Integer electro,
            Integer water,
            Integer cameras,
            Integer lan,
            Boolean chase,
            Double heightM,
            List<String> complexItems,
            String complexNote) {
    }

    public record Line(
            String id,
            String group,
            String title,
            double qty,
            String unit,
            int unitPrice,
            double laborShare,
            int total) {
    }

    public record Estimate(
            List<Line> lines,
            int material,
            int labor,
            int mid,
            Integer low,
            Integer high,
            boolean priced,
            List<String> adjustments,
            List<String> assumptions,
            List<String> excludes,
            List<String> complexItems) {
    }

    private Estimator() {
    }

    public static Estimate build(Job job, PriceCatalog catalog, LocalDate today) {
        String section = job.section();
        String grade = job.grade();
        String condition = job.condition();
        double height = job.heightM() == null ? catalog.defaultHeight() : job.heightM();
        double floor = job.floorArea() == null ? 0 : job.floorArea();
        boolean chase = Boolean.TRUE.equals(job.chase());
        List<Line> lines = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        List<String> excludes = new ArrayList<>(catalog.baseExcludes());
        if (catalog.teaching()) {
            assumptions.add("Цены учебные: в прототипе стоит заглушка, не прайс компании.");
        }
        assumptions.add("Сообщение не является офертой. Точную стоимость фиксируем после замера.");
        assumptions.add("Ориентир действует до " + today.plusDays(catalog.validDays()).format(DAY) + ".");

        if (("finish".equals(section) || "both".equals(section)) && floor > 0) {
            List<String> workIds = job.works() == null ? List.of() : job.works();
            PriceCatalog.Bundle bundle = catalog.bundle(job.finishMode());
            if (bundle != null) {
                workIds = bundle.works();
                assumptions.add(bundle.note());
            }
            assumptions.add("Высота потолка в расчёте стен — " + oneDecimal(height)
                    + " м. Площадь стен принята как пол × " + formatG(catalog.wallFactor())
                    + " при " + comma(catalog.defaultHeight(), 1)
                    + " м и пересчитана по высоте. Это не обмер.");
            if ("old".equals(condition)) {
                assumptions.add("Вторичка: вилка шире, потому что основание и скрытые работы неизвестны.");
            }
            for (String workId : workIds) {
                PriceCatalog.Work work = catalog.work(workId);
                double qty = round1(quantity(work.area(), floor, height, catalog));
                int unitPrice = work.rate().price(grade);
                int total = round0(qty * unitPrice);
                lines.add(new Line(work.id(), "finish", work.title(), qty, work.unit(), unitPrice, work.rate().laborShare(), total));
                excludes.add(work.excludes());
            }
        }

        if ("eng".equals(section) || "both".equals(section)) {
            assumptions.add("Инженерия посчитана по точкам, без проекта и без спецификации оборудования.");
            if (chase) {
                assumptions.add("За штробление к стоимости работ по инженерии добавлено " + percent(catalog.chaseLabor()) + "%.");
            }
            if (height > catalog.defaultHeight()) {
                assumptions.add("Потолок выше 3 м: инженерия и стены пересчитаны с коэффициентом "
                        + comma(catalog.highCeiling(), 2) + ".");
            }
            for (String key : List.of("electro", "water", "cameras", "lan")) {
                int count = count(job, key);
                if (count <= 0) {
                    continue;
                }
                PriceCatalog.Point point = catalog.point(key);
                int unitPrice = point.rate().price(grade);
                int total = count * unitPrice;
                lines.add(new Line(point.id(), "eng", point.title(), count, point.unit(), unitPrice, point.rate().laborShare(), total));
                assumptions.add(point.note());
            }
        }

        double finishMaterial = 0;
        double finishLabor = 0;
        double engMaterial = 0;
        double engLabor = 0;
        for (Line line : lines) {
            double lineLabor = line.total() * line.laborShare();
            double lineMaterial = line.total() - lineLabor;
            if ("eng".equals(line.group())) {
                engLabor += lineLabor;
                engMaterial += lineMaterial;
            } else {
                finishLabor += lineLabor;
                finishMaterial += lineMaterial;
            }
        }

        List<String> adjustments = new ArrayList<>();
        if (engLabor != 0 && chase) {
            engLabor *= catalog.chaseLabor();
            adjustments.add("Работы инженерии ×" + String.format(Locale.US, "%.2f", catalog.chaseLabor()) + " за штробление.");
        }
        if ((engLabor != 0 || engMaterial != 0) && height > catalog.defaultHeight()) {
            engLabor *= catalog.highCeiling();
            engMaterial *= catalog.highCeiling();
            adjustments.add("Инженерия ×" + String.format(Locale.US, "%.2f", catalog.highCeiling()) + " — потолок выше 3 м.");
        }

        boolean plaster = false;
        boolean drywall = false;
        for (Line line : lines) {
            plaster = plaster || "plaster".equals(line.id());
            drywall = drywall || "drywall".equals(line.id());
        }
        if (plaster && drywall) {
            assumptions.add("Штукатурка и ГКЛ посчитаны каждая на всю площадь стен. Если гипсокартон только на части, цифра завышена.");
        }

        int material = round0(finishMaterial + engMaterial);
        int labor = round0(finishLabor + engLabor);
        int mid = material + labor;
        boolean hasFinish = finishMaterial + finishLabor > 0;
        boolean hasEng = engMaterial + engLabor > 0;
        boolean priced = mid > 0 && !"complex".equals(section);
        Integer low = null;
        Integer high = null;
        if (priced) {
            String spreadSection = section;
            if ("both".equals(section) && hasFinish && !hasEng) {
                spreadSection = "finish";
            } else if ("both".equals(section) && hasEng && !hasFinish) {
                spreadSection = "eng";
            }
            double spread = catalog.spread(spreadSection, condition);
            low = round0(mid * (1 - spread));
            high = round0(mid * (1 + spread));
            if (mid < catalog.minOrder()) {
                assumptions.add("Сумма ниже ориентира минимального выезда " + money(catalog.minOrder())
                        + ". Менеджер подтвердит, берёмся ли за объём.");
            }
        }

        List<String> complexItems = job.complexItems() == null ? List.of() : job.complexItems();
        if ("complex".equals(section) || !complexItems.isEmpty()) {
            priced = priced && !"complex".equals(section);
            if ("complex".equals(section)) {
                low = null;
                high = null;
            }
            assumptions.add("Сложные системы — щиты, сжатый воздух, СКУД, аудио и нестандартное видеонаблюдение — без точной цены. Нужен обход или исходные данные.");
            for (String item : complexItems) {
                String need = catalog.complexNeed(item);
                if (need != null) {
                    assumptions.add(need);
                }
            }
        }
        if ("plant".equals(job.objectType())) {
            assumptions.add("Производство: нормы в боте бытовые и офисные. Нагрузки и смены уточнит инженер.");
        }
        if (job.complexNote() != null && !job.complexNote().isEmpty()) {
            assumptions.add("Комментарий клиента учтён в заявке и не менял формулу.");
        }

        return new Estimate(
                List.copyOf(lines),
                material,
                labor,
                mid,
                low,
                high,
                priced,
                List.copyOf(adjustments),
                unique(assumptions),
                unique(excludes),
                List.copyOf(complexItems));
    }

    public static String render(Job job, Estimate estimate, PriceCatalog catalog) {
        String objectName = catalog.objects().getOrDefault(job.objectType(), "—");
        String condition = catalog.conditions().getOrDefault(job.condition(), "—").toLowerCase(Locale.forLanguageTag("ru"));
        String grade = catalog.grades().getOrDefault(job.grade(), "—");
        String city = job.city() == null || job.city().isEmpty() ? "—" : job.city();
        List<String> rows = new ArrayList<>();
        rows.add("Предварительный расчёт");
        rows.add("Объект: " + objectName + ", " + condition + ", " + city);
        rows.add("Класс: " + grade);
        rows.add("");
        if (!estimate.lines().isEmpty()) {
            rows.add("Состав, середина вилки");
            for (Line line : estimate.lines()) {
                rows.add("• " + line.title() + " — " + qtyText(line.qty()) + " " + line.unit()
                        + " × " + money(line.unitPrice()) + " = " + money(line.total()));
            }
            rows.add("");
            if (!estimate.adjustments().isEmpty()) {
                rows.add("Строки выше — цены прайса без коэффициентов.");
                rows.addAll(estimate.adjustments());
                rows.add("");
            }
            rows.add("Материалы: " + money(estimate.material()));
            rows.add("Работы: " + money(estimate.labor()));
            if (estimate.priced() && estimate.low() != null) {
                rows.add("Вилка: " + money(estimate.low()) + " – " + money(estimate.high()));
            }
            rows.add("");
        } else {
            rows.add("Автоматической цены по выбранным работам нет.");
            rows.add("");
        }
        if (!estimate.complexItems().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String item : estimate.complexItems()) {
                String name = catalog.complex().get(item);
                if (name != null) {
                    names.add(name);
                }
            }
            rows.add("На обследование: " + String.join(", ", names) + ".");
            rows.add("");
        }
        rows.add("Допущения");
        for (String item : estimate.assumptions()) {
            rows.add("• " + item);
        }
        rows.add("");
        rows.add("Не входит");
        for (String item : estimate.excludes()) {
            rows.add("• " + item);
        }
        return String.join("\n", rows);
    }

    public static String money(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', ' ') + " ₽";
    }

    static double round1(double value) {
        return Math.rint(value * 10.0) / 10.0;
    }

    static int round0(double value) {
        return (int) Math.rint(value);
    }

    private static double quantity(String area, double floor, double height, PriceCatalog catalog) {
        if ("floor".equals(area) || "ceiling".equals(area)) {
            return floor;
        }
        return floor * catalog.wallFactor() * (height / catalog.defaultHeight());
    }

    private static int count(Job job, String key) {
        Integer value = switch (key) {
            case "electro" -> job.electro();
            case "water" -> job.water();
            case "cameras" -> job.cameras();
            case "lan" -> job.lan();
            default -> null;
        };
        return value == null ? 0 : value;
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String comma(double value, int digits) {
        return String.format(Locale.US, "%." + digits + "f", value).replace('.', ',');
    }

    private static String percent(double multiplier) {
        double pct = (multiplier - 1.0) * 100.0;
        double whole = Math.rint(pct);
        if (Math.abs(pct - whole) < 1e-9) {
            return Long.toString((long) whole);
        }
        return String.format(Locale.US, "%.2f", pct).replace('.', ',');
    }

    private static String formatG(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) Math.rint(value));
        }
        return Double.toString(value);
    }

    private static String qtyText(double qty) {
        double nearest = Math.rint(qty);
        if (Math.abs(qty - nearest) < 1e-9) {
            return Long.toString((long) nearest);
        }
        return String.format(Locale.US, "%.1f", qty);
    }

    private static List<String> unique(List<String> items) {
        return List.copyOf(new LinkedHashSet<>(items));
    }
}
