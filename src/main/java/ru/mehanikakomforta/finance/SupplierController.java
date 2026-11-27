package ru.mehanikakomforta.finance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mehanikakomforta.clients.ClientAccess;
import ru.mehanikakomforta.leads.ApiError;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
class SupplierController {

    private static final Set<String> KINDS = Set.of("delivery", "invoice");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final SupplierService suppliers;

    SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping("/api/suppliers")
    ResponseEntity<?> list() {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return ResponseEntity.ok(suppliers.list());
    }

    @GetMapping("/api/suppliers/{id:\\d+}")
    ResponseEntity<?> one(@PathVariable long id) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        SupplierService.Card card = suppliers.one(id);
        if (card == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        return ResponseEntity.ok(card);
    }

    @PostMapping("/api/suppliers")
    ResponseEntity<?> create(@RequestBody(required = false) SupplierBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("name"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(suppliers.save(body.name().trim(), blank(body.inn()), blank(body.phone())));
    }

    @PatchMapping("/api/suppliers/{id:\\d+}")
    ResponseEntity<?> update(@PathVariable long id, @RequestBody(required = false) SupplierBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("name"));
        }
        SupplierService.Card saved = suppliers.update(id, body.name().trim(), blank(body.inn()), blank(body.phone()), blank(body.address()), blank(body.contract()));
        if (saved == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/api/supplier-lines")
    ResponseEntity<?> lines(@RequestParam(required = false) String unassigned) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (!"1".equals(unassigned)) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(suppliers.unassigned());
    }

    @PatchMapping("/api/supplier-lines/{id:\\d+}")
    ResponseEntity<?> assign(@PathVariable long id, @RequestBody(required = false) AssignBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || !suppliers.assign(id, body.object())) {
            return ResponseEntity.badRequest().body(new ApiError("object"));
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/api/suppliers/{id:\\d+}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> document(
            @PathVariable long id,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String lines,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws java.io.IOException {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (kind == null || !KINDS.contains(kind) || date(date) == null) {
            return ResponseEntity.badRequest().body(new ApiError("document"));
        }
        SupplierService.Card saved = suppliers.addDocument(id, kind, blank(number), date(date), readLines(lines), fileName(file), file == null ? null : file.getBytes());
        if (saved == null) {
            return ResponseEntity.badRequest().body(new ApiError("document"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/api/suppliers/{id:\\d+}/documents/{documentId:\\d+}")
    ResponseEntity<?> remove(@PathVariable long id, @PathVariable long documentId) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("confirmed"));
    }

    @PostMapping("/api/suppliers/{id:\\d+}/documents/{documentId:\\d+}/cancel")
    ResponseEntity<?> cancel(@PathVariable long id, @PathVariable long documentId, @RequestBody(required = false) CancelBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || body.reason() == null || body.reason().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("reason"));
        }
        SupplierService.Card saved = suppliers.cancel(id, documentId, body.reason());
        if (saved == null) {
            return ResponseEntity.badRequest().body(new ApiError("document"));
        }
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/api/suppliers/{id:\\d+}/payments")
    ResponseEntity<?> pay(@PathVariable long id, @RequestBody(required = false) PayBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || date(body.date()) == null) {
            return ResponseEntity.badRequest().body(new ApiError("payment"));
        }
        SupplierService.Card saved = suppliers.pay(id, money(body.amount()), date(body.date()));
        if (saved == null) {
            return ResponseEntity.badRequest().body(new ApiError("payment"));
        }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/api/suppliers/{id:\\d+}/statement")
    ResponseEntity<?> statement(
            @PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) throws java.io.IOException {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        SupplierService.Statement statement = suppliers.statement(id, from, to);
        if (statement == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("statement.pdf").build().toString())
                .body(pdf(statement));
    }

    private static byte[] pdf(SupplierService.Statement statement) throws java.io.IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, SupplierController.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf"));
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                float y = 760;
                y = line(stream, font, y, "Акт сверки. " + (statement.name() == null ? "" : statement.name()));
                y = line(stream, font, y, "Закуплено " + text(statement.purchased()) + "  Оплачено " + text(statement.paid()) + "  Долг " + text(statement.debt()));
                for (String row : statement.rows()) {
                    y = line(stream, font, y, row);
                }
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static float line(PDPageContentStream stream, PDType0Font font, float y, String text) throws java.io.IOException {
        stream.beginText();
        stream.setFont(font, 11);
        stream.newLineAtOffset(48, y);
        stream.showText(text == null ? "" : text);
        stream.endText();
        return y - 18;
    }

    private static String text(BigDecimal value) {
        return value == null ? "—" : value.toPlainString();
    }

    private static List<SupplierService.IncomingLine> readLines(String lines) {
        if (lines == null || lines.isBlank()) {
            return List.of();
        }
        JsonNode node = MAPPER.readTree(lines);
        if (!node.isArray()) {
            return List.of();
        }
        List<SupplierService.IncomingLine> body = new ArrayList<>();
        for (JsonNode item : node) {
            body.add(new SupplierService.IncomingLine(item.path("title").asString(""), money(item.path("amount").asString("")), item.path("object").asString("")));
        }
        return body;
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

    private static String fileName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return null;
        }
        return java.nio.file.Path.of(file.getOriginalFilename()).getFileName().toString();
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            return null;
        }
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

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    record SupplierBody(String name, String inn, String phone, String address, String contract) {
    }

    record AssignBody(String object) {
    }

    record PayBody(String amount, String date) {
    }

    record CancelBody(String reason) {
    }
}
