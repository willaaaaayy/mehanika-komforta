package ru.mehanikakomforta.finance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mehanikakomforta.clients.ClientAccess;
import ru.mehanikakomforta.leads.ApiError;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
class PriceVersionController {

    private final PriceVersionService prices;

    PriceVersionController(PriceVersionService prices) {
        this.prices = prices;
    }

    @GetMapping("/api/objects/{id:\\d+}/price-versions")
    ResponseEntity<?> list(@PathVariable long id) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        List<PriceVersionService.VersionView> body = prices.list(id);
        if (body == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/objects/{id:\\d+}/price-versions")
    ResponseEntity<?> add(@PathVariable long id, @RequestBody(required = false) VersionBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || body.lines() == null) {
            return ResponseEntity.badRequest().body(new ApiError("lines"));
        }
        List<PriceVersionService.IncomingLine> lines = new ArrayList<>();
        for (LineBody line : body.lines()) {
            if (line == null) {
                continue;
            }
            lines.add(new PriceVersionService.IncomingLine(line.title(), line.change(), money(line.quantity()), money(line.clientPrice())));
        }
        PriceVersionService.VersionView saved = prices.add(id, body.reason(), lines);
        if (saved == null) {
            return ResponseEntity.badRequest().body(new ApiError("version"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/api/objects/{id:\\d+}/price-versions/compare")
    ResponseEntity<?> compare(@PathVariable long id, @RequestParam(required = false) Long from, @RequestParam(required = false) Long to) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (from == null || to == null) {
            return ResponseEntity.badRequest().body(new ApiError("version"));
        }
        PriceVersionService.CompareView body = prices.compare(id, from, to);
        if (body == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("version"));
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/api/objects/{id:\\d+}/price-versions/{versionId:\\d+}/approve")
    ResponseEntity<?> approve(@PathVariable long id, @PathVariable long versionId) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        PriceVersionService.VersionView saved = prices.approve(id, versionId);
        if (saved == null) {
            return ResponseEntity.badRequest().body(new ApiError("version"));
        }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/api/objects/{id:\\d+}/price-versions/{versionId:\\d+}/export")
    ResponseEntity<?> export(@PathVariable long id, @PathVariable long versionId, @RequestParam(defaultValue = "pdf") String format) throws java.io.IOException {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        PriceVersionService.VersionView version = prices.one(id, versionId);
        if (version == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("version"));
        }
        boolean pdf = "pdf".equals(format);
        byte[] body = pdf ? pdf(version) : xlsx(version);
        String name = pdf ? "price.pdf" : "price.xlsx";
        MediaType type = pdf
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }

    private static byte[] xlsx(PriceVersionService.VersionView version) throws java.io.IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Прайс");
            Row header = sheet.createRow(0);
            String[] titles = {"Работа", "План", "Факт", "Клиенту", "Себестоимость", "Маржа"};
            for (int index = 0; index < titles.length; index++) {
                header.createCell(index).setCellValue(titles[index]);
            }
            int rowIndex = 1;
            for (PriceVersionService.LineView line : version.lines()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(line.title());
                number(row, 1, line.plan());
                number(row, 2, line.fact());
                number(row, 3, line.clientPrice());
                number(row, 4, line.cost());
                number(row, 5, line.margin());
            }
            Row total = sheet.createRow(rowIndex);
            total.createCell(0).setCellValue("Сумма");
            number(total, 1, version.amount());
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    private static byte[] pdf(PriceVersionService.VersionView version) throws java.io.IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, PriceVersionController.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf"));
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = 760;
                y = text(stream, font, y, "Итоговый прайс v" + version.version() + "  " + plain(version.amount()));
                for (PriceVersionService.LineView line : version.lines()) {
                    y = text(stream, font, y, line.title() + "  " + plain(line.clientPrice()));
                }
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static float text(PDPageContentStream stream, PDType0Font font, float y, String value) throws java.io.IOException {
        stream.beginText();
        stream.setFont(font, 11);
        stream.newLineAtOffset(48, y);
        stream.showText(value == null ? "" : value);
        stream.endText();
        return y - 18;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "—" : value.toPlainString();
    }

    private static void number(Row row, int index, BigDecimal value) {
        if (value != null) {
            row.createCell(index).setCellValue(value.doubleValue());
        }
    }

    private static ResponseEntity<?> director() {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return null;
    }

    private static BigDecimal money(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(" ", "").replace(",", "."));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    record VersionBody(String reason, List<LineBody> lines) {
    }

    record LineBody(String title, String change, String quantity, String clientPrice) {
    }
}
