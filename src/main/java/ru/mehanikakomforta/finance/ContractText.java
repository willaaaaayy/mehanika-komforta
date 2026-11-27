package ru.mehanikakomforta.finance;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

public final class ContractText {

    private ContractText() {
    }

    public record Row(String title, BigDecimal amount, String kind, String dueBasis, Integer dueDays) {
    }

    public record Read(
            String number,
            String date,
            String subject,
            String client,
            String inn,
            String kpp,
            String ogrn,
            String clientAddress,
            String signatory,
            String objectAddress,
            BigDecimal amount,
            BigDecimal vat,
            List<Row> schedule,
            List<String> paragraphs,
            boolean addendum
    ) {
    }

    public static Read read(byte[] bytes) {
        String text = isPdf(bytes) ? pdf(bytes) : text(bytes);
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> paragraphs = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                paragraphs.add(line.trim());
            }
        }
        String head = paragraphs.stream().limit(3).reduce("", (left, right) -> left + "\n" + right).toLowerCase(java.util.Locale.ROOT);
        boolean addendum = head.contains("дополнительное соглашение") || head.contains("допсоглашение");
        String number = first(text, "ДОГОВОР\\s*№\\s*([0-9]+(?:-[0-9]+)*)");
        if (number == null && addendum) {
            number = first(text, "к договору\\s*№\\s*([0-9]+(?:-[0-9]+)*)");
        }
        String clientBlock = between(text, "ЗАКАЗЧИК", "ПОДРЯДЧИК");
        String client = preambleClient(text);
        if (client == null && clientBlock != null) {
            client = lineAfter(clientBlock, "ЗАКАЗЧИК");
        }
        String inn = clientBlock == null ? null : first(clientBlock, "ИНН\\s*(\\d{10,12})");
        String kpp = clientBlock == null ? null : first(clientBlock, "КПП\\s*(\\d{9})");
        String ogrn = clientBlock == null ? null : first(clientBlock, "ОГРН\\s*(\\d{13,15})");
        String objectAddress = objectAddress(text);
        BigDecimal amount = moneyAfter(text, "ориентировочн\\p{L}*\\s+стоимост\\p{L}*\\s+работ\\s+составляет");
        if (amount == null) {
            amount = moneyAfter(text, "общая\\s+стоимость[\\s\\S]{0,80}?составляет");
        }
        if (number == null || client == null || amount == null) {
            return null;
        }
        BigDecimal advance = moneyAfter(text, "авансов\\p{L}*\\s+платеж");
        boolean byAct = text.toLowerCase(java.util.Locale.ROOT).contains("кс-2")
                && text.toLowerCase(java.util.Locale.ROOT).contains("30 банковск");
        List<Row> schedule = new ArrayList<>();
        if (advance != null) {
            schedule.add(new Row("Аванс", advance, "advance", null, null));
            schedule.add(new Row("Окончательный расчёт", amount.subtract(advance), "final", byAct ? "act" : null, byAct ? 30 : null));
        } else {
            schedule.add(new Row("Оплата по договору", amount, "final", byAct ? "act" : null, byAct ? 30 : null));
        }
        String subject = paragraphs.stream().filter(line -> line.startsWith("1.1.")).findFirst().orElse(null);
        if (subject != null && subject.length() > 300) {
            subject = subject.substring(0, 300).trim();
        }
        BigDecimal vat = text.toLowerCase(java.util.Locale.ROOT).contains("ндс не облагается") ? BigDecimal.ZERO : null;
        return new Read(
                number,
                null,
                subject,
                client,
                inn,
                kpp,
                ogrn,
                null,
                signatory(text),
                objectAddress,
                amount,
                vat,
                schedule,
                paragraphs,
                addendum
        );
    }

    private static String preambleClient(String text) {
        for (String line : text.split("\\R")) {
            int mark = line.toLowerCase(java.util.Locale.ROOT).indexOf("именуем");
            if (mark < 0 || !line.contains("Заказчик")) {
                continue;
            }
            int comma = line.lastIndexOf(',', mark);
            if (comma > 10) {
                return line.substring(0, comma).trim();
            }
        }
        return null;
    }

    private static String signatory(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([А-ЯЁ]\\.[А-ЯЁ]\\.)\\s*([А-ЯЁ][а-яё]+)")
                .matcher(text);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(2) + " " + matcher.group(1);
        }
        return found;
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private static String pdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            return text == null ? null : text.replace('\u00A0', ' ');
        } catch (Exception exception) {
            return null;
        }
    }

    private static String text(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return paragraphs(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
                entry = zip.getNextEntry();
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static String paragraphs(String xml) {
        StringBuilder body = new StringBuilder();
        Matcher paragraph = Pattern.compile("<w:p[\\s>][\\s\\S]*?</w:p>").matcher(xml);
        while (paragraph.find()) {
            Matcher run = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>").matcher(paragraph.group());
            StringBuilder line = new StringBuilder();
            while (run.find()) {
                line.append(run.group(1));
            }
            append(body, line.toString().replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"));
        }
        return body.toString();
    }

    private static void append(StringBuilder body, String line) {
        if (line != null && !line.isBlank()) {
            body.append(line.replace('\u00A0', ' ').trim()).append('\n');
        }
    }

    private static String objectAddress(String text) {
        Matcher matcher = Pattern.compile("на объекте:\\s*«([^»]+)»([^\\n]*)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String address = matcher.group(1).trim();
        Matcher place = Pattern.compile("б/с\\s*[^,]+,\\s*этаж\\s*\\d+").matcher(matcher.group(2));
        if (place.find()) {
            address = address + ", " + place.group().trim();
        }
        return address;
    }

    private static BigDecimal moneyAfter(String text, String lead) {
        Matcher start = Pattern.compile(lead, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        if (!start.find()) {
            return null;
        }
        String window = text.substring(start.end(), Math.min(text.length(), start.end() + 160));
        Matcher money = Pattern.compile("([0-9]{1,3}(?:[ \\u00A0][0-9]{3})+|[0-9]+)\\s*(?:\\([^)]{0,120}\\)\\s*)?руб").matcher(window);
        if (!money.find()) {
            return null;
        }
        String digits = money.group(1).replace(" ", "").replace("\u00A0", "");
        if (digits.isBlank()) {
            return null;
        }
        return new BigDecimal(digits);
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        int to = text.indexOf(end, from + start.length());
        if (from < 0 || to < 0) {
            return null;
        }
        return text.substring(from, to);
    }

    private static String lineAfter(String block, String label) {
        String[] lines = block.split("\\R");
        boolean seen = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!seen && trimmed.contains(label)) {
                String rest = trimmed.substring(trimmed.indexOf(label) + label.length()).replace(":", "").trim();
                if (!rest.isBlank()) {
                    return rest;
                }
                seen = true;
                continue;
            }
            if (seen && !trimmed.isBlank()) {
                return trimmed;
            }
        }
        return null;
    }

    private static String first(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
