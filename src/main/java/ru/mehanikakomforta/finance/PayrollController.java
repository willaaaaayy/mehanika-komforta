package ru.mehanikakomforta.finance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mehanikakomforta.clients.ClientAccess;
import ru.mehanikakomforta.leads.ApiError;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@RestController
class PayrollController {

    private static final Set<String> TYPES = Set.of("piecework", "daily", "office");

    private final PayrollService payroll;

    PayrollController(PayrollService payroll) {
        this.payroll = payroll;
    }

    @GetMapping("/api/employees")
    ResponseEntity<?> employees() {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return ResponseEntity.ok(payroll.people());
    }

    @PostMapping("/api/employees")
    ResponseEntity<?> createEmployee(@RequestBody(required = false) EmployeeBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        EmployeeBody ready = ready(body);
        if (ready == null) {
            return ResponseEntity.badRequest().body(new ApiError("employee"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(payroll.saveEmployee(ready.name(), ready.payType(), money(ready.rate())));
    }

    @PatchMapping("/api/employees/{id:\\d+}")
    ResponseEntity<?> updateEmployee(@PathVariable long id, @RequestBody(required = false) EmployeeBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        EmployeeBody ready = ready(body);
        if (ready == null) {
            return ResponseEntity.badRequest().body(new ApiError("employee"));
        }
        PayrollService.EmployeeView saved = payroll.updateEmployee(id, ready.name(), ready.payType(), money(ready.rate()));
        if (saved == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/api/worklogs")
    ResponseEntity<?> logs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return ResponseEntity.ok(payroll.logs(from, to));
    }

    @PostMapping("/api/worklogs")
    ResponseEntity<?> createLog(@RequestBody(required = false) LogBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || body.employeeId() == null || date(body.date()) == null) {
            return ResponseEntity.badRequest().body(new ApiError("worklog"));
        }
        PayrollService.LogView saved = payroll.saveLog(body.employeeId(), date(body.date()), body.object(), body.workId(), money(body.quantity()));
        if (saved == null) {
            return ResponseEntity.badRequest().body(new ApiError("worklog"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/api/worklogs/{id:\\d+}")
    ResponseEntity<?> updateLog(@PathVariable long id, @RequestBody(required = false) LogBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || body.employeeId() == null || date(body.date()) == null) {
            return ResponseEntity.badRequest().body(new ApiError("worklog"));
        }
        PayrollService.LogView saved = payroll.updateLog(id, body.employeeId(), date(body.date()), body.object(), body.workId(), money(body.quantity()));
        if (saved == null) {
            return ResponseEntity.badRequest().body(new ApiError("worklog"));
        }
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/api/payroll")
    ResponseEntity<?> payroll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return ResponseEntity.ok(payroll.sheet(from, to));
    }

    @PostMapping("/api/payroll/{id:\\d+}/payments")
    ResponseEntity<?> pay(@PathVariable long id, @RequestBody(required = false) PayBody body) {
        ResponseEntity<?> denied = director();
        if (denied != null) {
            return denied;
        }
        if (body == null || date(body.date()) == null) {
            return ResponseEntity.badRequest().body(new ApiError("payment"));
        }
        PayrollService.Row row = payroll.pay(id, money(body.amount()), date(body.date()));
        if (row == null) {
            return ResponseEntity.badRequest().body(new ApiError("payment"));
        }
        return ResponseEntity.ok(row);
    }

    @GetMapping("/api/payroll/export")
    ResponseEntity<?> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "xlsx") String format
    ) throws java.io.IOException {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        PayrollService.Sheet sheet = payroll.sheet(from, to);
        byte[] body = "pdf".equals(format) ? pdf(sheet) : xlsx(sheet);
        String name = "pdf".equals(format) ? "payroll.pdf" : "payroll.xlsx";
        MediaType type = "pdf".equals(format)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }

    private static byte[] xlsx(PayrollService.Sheet sheet) throws java.io.IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            var table = book.createSheet("Ведомость");
            String[] titles = {"Сотрудник", "Начислено", "Аванс", "Выплачено", "К выплате"};
            Row header = table.createRow(0);
            for (int index = 0; index < titles.length; index++) {
                header.createCell(index).setCellValue(titles[index]);
            }
            int line = 1;
            for (PayrollService.Row row : sheet.rows()) {
                Row excel = table.createRow(line++);
                excel.createCell(0).setCellValue(row.employee() == null ? "" : row.employee());
                number(excel, 1, row.accrued());
                number(excel, 2, row.advance());
                number(excel, 3, row.paid());
                number(excel, 4, row.due());
            }
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    private static byte[] pdf(PayrollService.Sheet sheet) throws java.io.IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, PayrollController.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf"));
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(48, 760);
                stream.showText("Ведомость");
                stream.endText();
                float y = 730;
                for (PayrollService.Row row : sheet.rows()) {
                    stream.beginText();
                    stream.setFont(font, 11);
                    stream.newLineAtOffset(48, y);
                    stream.showText(line(row));
                    stream.endText();
                    y -= 18;
                }
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static String line(PayrollService.Row row) {
        return (row.employee() == null ? "" : row.employee())
                + "  " + text(row.accrued())
                + "  " + text(row.advance())
                + "  " + text(row.paid())
                + "  " + text(row.due());
    }

    private static String text(BigDecimal value) {
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

    private static EmployeeBody ready(EmployeeBody body) {
        if (body == null || body.name() == null || body.name().isBlank() || body.payType() == null || !TYPES.contains(body.payType())) {
            return null;
        }
        return new EmployeeBody(body.name().trim(), body.payType(), body.rate());
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

    record EmployeeBody(String name, String payType, String rate) {
    }

    record LogBody(Long employeeId, String date, String object, String workId, String quantity) {
    }

    record PayBody(String amount, String date) {
    }
}
