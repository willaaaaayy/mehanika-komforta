package ru.mehanikakomforta.finance;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mehanikakomforta.leads.PriceList;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Order(3)
public class InternalRates implements ApplicationRunner {

    private static final ZoneId SARATOV = ZoneId.of("Europe/Saratov");
    private static final BigDecimal MATERIAL_SHARE = new BigDecimal("0.15");
    private static final BigDecimal THRESHOLD = new BigDecimal("10");

    private final InternalRateRepository rates;
    private final PriceList prices;

    public InternalRates(InternalRateRepository rates, PriceList prices) {
        this.rates = rates;
        this.prices = prices;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (rates.count() > 0) {
            return;
        }
        JsonNode works = prices.document().path("works");
        boolean teaching = prices.document().path("teaching").asBoolean(true);
        LocalDate from = LocalDate.of(2020, 1, 1);
        works.propertyNames().forEach(workId -> {
            JsonNode work = works.get(workId);
            BigDecimal standard = price(work, "standard");
            BigDecimal share = work.path("laborShare").isNumber()
                    ? work.path("laborShare").decimalValue()
                    : new BigDecimal("0.5");
            BigDecimal labor = standard == null ? null : standard.multiply(share).setScale(0, RoundingMode.HALF_UP);
            BigDecimal material = standard == null ? null : standard.multiply(MATERIAL_SHARE).setScale(0, RoundingMode.HALF_UP);
            rates.save(new InternalRateEntity(
                    workId,
                    work.path("title").asString(workId),
                    work.path("unit").asString("м²"),
                    labor,
                    material,
                    from,
                    teaching
            ));
        });
    }

    public Catalog catalog(String grade) {
        String wanted = grade == null || grade.isBlank() ? "standard" : grade;
        LocalDate today = LocalDate.now(SARATOV);
        Map<String, InternalRateEntity> current = current(today);
        List<RateView> body = new ArrayList<>();
        boolean teaching = false;
        for (InternalRateEntity rate : current.values()) {
            if (rate.isTeaching()) {
                teaching = true;
            }
            body.add(view(rate, wanted, today));
        }
        body.sort((left, right) -> left.workId().compareTo(right.workId()));
        return new Catalog(teaching, body);
    }

    @Transactional
    public RateView save(String workId, String title, String unit, BigDecimal labor, BigDecimal material, LocalDate validFrom) {
        InternalRateEntity previous = current(validFrom).get(workId);
        String keptTitle = title == null || title.isBlank()
                ? (previous == null ? workId : previous.getTitle())
                : title.trim();
        String keptUnit = unit == null || unit.isBlank()
                ? (previous == null ? "м²" : previous.getUnit())
                : unit.trim();
        boolean teaching = prices.document().path("teaching").asBoolean(true);
        InternalRateEntity saved = rates.save(new InternalRateEntity(workId, keptTitle, keptUnit, labor, material, validFrom, teaching));
        return view(saved, "standard", validFrom);
    }

    public ImportPreview preview(byte[] file) {
        List<RateView> rows = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = book.getNumberOfSheets() == 0 ? null : book.getSheetAt(0);
            if (sheet == null) {
                errors.add(new ImportError(1, "В файле нет листа."));
                return new ImportPreview(rows, errors);
            }
            int first = sheet.getFirstRowNum();
            Row header = sheet.getRow(first);
            Columns columns = columns(header, formatter);
            for (int index = first + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || blank(text(row, columns.workId, formatter))) {
                    continue;
                }
                int number = index + 1;
                String workId = text(row, columns.workId, formatter);
                String title = text(row, columns.title, formatter);
                BigDecimal labor = money(text(row, columns.labor, formatter));
                BigDecimal material = money(text(row, columns.material, formatter));
                if (labor == null && material == null) {
                    errors.add(new ImportError(number, "Нет ставки и материала."));
                    continue;
                }
                if (title.isBlank() && prices.document().path("works").path(workId).isMissingNode()) {
                    errors.add(new ImportError(number, "Неизвестная работа без названия."));
                    continue;
                }
                rows.add(new RateView(workId, title, text(row, columns.unit, formatter), labor, material, null, null, null, null, text(row, columns.validFrom, formatter), null, false));
            }
        } catch (Exception exception) {
            errors.add(new ImportError(1, "Файл не читается."));
        }
        return new ImportPreview(rows, errors);
    }

    public List<FinanceView.WorkLine> lines(String worksJson, String grade, LocalDate asOf) {
        List<FinanceView.WorkLine> lines = new ArrayList<>();
        if (worksJson == null || worksJson.isBlank()) {
            return lines;
        }
        JsonNode node = tools.jackson.databind.json.JsonMapper.builder().build().readTree(worksJson);
        if (!node.isArray()) {
            return lines;
        }
        for (JsonNode item : node) {
            String workId = item.asString();
            RateView rate = viewAt(workId, grade, asOf);
            if (rate == null || (rate.clientPrice() == null && rate.cost() == null && rate.margin() == null)) {
                continue;
            }
            lines.add(new FinanceView.WorkLine(rate.title(), rate.clientPrice(), rate.cost(), rate.margin(), rate.teaching()));
        }
        return lines;
    }

    public List<FinanceView.CostLine> costs(List<String> workIds, LocalDate asOf) {
        List<FinanceView.CostLine> lines = new ArrayList<>();
        for (String workId : workIds) {
            RateView rate = viewAt(workId, "standard", asOf);
            if (rate == null || rate.cost() == null) {
                continue;
            }
            lines.add(new FinanceView.CostLine(rate.title(), rate.cost(), null, null, false));
        }
        return lines;
    }

    public BigDecimal threshold() {
        return THRESHOLD;
    }

    public BigDecimal laborAt(String workId, LocalDate day) {
        if (workId == null || workId.isBlank() || day == null) {
            return null;
        }
        InternalRateEntity rate = current(day).get(workId);
        return rate == null ? null : rate.getLabor();
    }

    private RateView viewAt(String workId, String grade, LocalDate asOf) {
        InternalRateEntity rate = current(asOf).get(workId);
        return rate == null ? null : view(rate, grade == null || grade.isBlank() ? "standard" : grade, asOf);
    }

    private Map<String, InternalRateEntity> current(LocalDate day) {
        Map<String, InternalRateEntity> current = new LinkedHashMap<>();
        for (InternalRateEntity rate : rates.findAllByOrderByWorkIdAscEffectiveFromDescIdDesc()) {
            if (rate.getEffectiveFrom().isAfter(day) || current.containsKey(rate.getWorkId())) {
                continue;
            }
            current.put(rate.getWorkId(), rate);
        }
        return current;
    }

    private RateView view(InternalRateEntity rate, String grade, LocalDate asOf) {
        BigDecimal clientPrice = price(prices.document().path("works").path(rate.getWorkId()), grade);
        BigDecimal cost = null;
        if (rate.getLabor() != null || rate.getMaterial() != null) {
            cost = zero(rate.getLabor()).add(zero(rate.getMaterial()));
        }
        BigDecimal margin = clientPrice != null && cost != null ? clientPrice.subtract(cost) : null;
        int version = 0;
        for (InternalRateEntity older : rates.findByWorkIdOrderByEffectiveFromDescIdDesc(rate.getWorkId())) {
            if (!older.getEffectiveFrom().isAfter(asOf)) {
                version++;
            }
        }
        return new RateView(
                rate.getWorkId(),
                rate.getTitle(),
                rate.getUnit(),
                rate.getLabor(),
                rate.getMaterial(),
                clientPrice,
                cost,
                margin,
                version == 0 ? 1 : version,
                rate.getEffectiveFrom().toString(),
                null,
                rate.isTeaching()
        );
    }

    private static BigDecimal price(JsonNode work, String grade) {
        JsonNode prices = work.path("prices");
        JsonNode chosen = prices.path(grade);
        if (!chosen.isNumber()) {
            chosen = prices.path("standard");
        }
        return chosen.isNumber() ? chosen.decimalValue() : null;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Columns columns(Row header, DataFormatter formatter) {
        Columns columns = new Columns();
        if (header == null) {
            return columns;
        }
        for (Cell cell : header) {
            String name = formatter.formatCellValue(cell).trim().toLowerCase();
            int index = cell.getColumnIndex();
            if (name.equals("workid") || name.equals("id")) {
                columns.workId = index;
            } else if (name.equals("title") || name.equals("название")) {
                columns.title = index;
            } else if (name.equals("unit") || name.equals("единица")) {
                columns.unit = index;
            } else if (name.equals("labor") || name.equals("ставка")) {
                columns.labor = index;
            } else if (name.equals("material") || name.equals("материал")) {
                columns.material = index;
            } else if (name.equals("validfrom") || name.equals("дата")) {
                columns.validFrom = index;
            }
        }
        return columns;
    }

    private static String text(Row row, int index, DataFormatter formatter) {
        if (index < 0) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static BigDecimal money(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(" ", "").replace(",", "."));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Catalog(boolean teaching, List<RateView> rates) {
    }

    public record RateView(
            String workId,
            String title,
            String unit,
            BigDecimal labor,
            BigDecimal material,
            BigDecimal clientPrice,
            BigDecimal cost,
            BigDecimal margin,
            Integer version,
            String validFrom,
            String grade,
            boolean teaching
    ) {
    }

    public record ImportPreview(List<RateView> rows, List<ImportError> errors) {
    }

    public record ImportError(int row, String message) {
    }

    private static final class Columns {
        private int workId;
        private int title = 1;
        private int unit = 2;
        private int labor = 3;
        private int material = 4;
        private int validFrom = 5;
    }
}
