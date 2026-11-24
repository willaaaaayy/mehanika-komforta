package ru.mehanikakomforta.finance;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ContractText {

    private ContractText() {
    }

    record Row(String title, BigDecimal amount, String kind) {
    }

    record Read(String number, String client, String inn, String objectAddress, BigDecimal amount, List<Row> schedule) {
    }

    static Read read(byte[] bytes) {
        String text = text(bytes);
        if (text == null || text.isBlank()) {
            return null;
        }
        String number = first(text, "ДОГОВОР\\s*№\\s*([0-9]+(?:-[0-9]+)*)");
        String clientBlock = between(text, "ЗАКАЗЧИК", "ПОДРЯДЧИК");
        String client = clientBlock == null ? null : lineAfter(clientBlock, "ЗАКАЗЧИК");
        String inn = clientBlock == null ? null : first(clientBlock, "ИНН\\s*(\\d{10,12})");
        String objectAddress = objectAddress(text);
        BigDecimal amount = moneyAfter(text, "ориентировочн\\p{L}*\\s+стоимост\\p{L}*\\s+работ\\s+составляет");
        if (amount == null) {
            amount = moneyAfter(text, "общая\\s+стоимость[\\s\\S]{0,80}?составляет");
        }
        if (number == null || client == null || amount == null) {
            return null;
        }
        BigDecimal advance = moneyAfter(text, "авансов\\p{L}*\\s+платеж");
        List<Row> schedule = new ArrayList<>();
        if (advance != null) {
            schedule.add(new Row("Аванс", advance, "advance"));
            schedule.add(new Row("Окончательный расчёт", amount.subtract(advance), "final"));
        } else {
            schedule.add(new Row("Оплата по договору", amount, "final"));
        }
        return new Read(number, client, inn, objectAddress, amount, schedule);
    }

    private static String text(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder body = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> {
                String line = paragraph.getText();
                if (line != null && !line.isBlank()) {
                    body.append(line.replace('\u00A0', ' ').trim()).append('\n');
                }
            });
            return body.toString();
        } catch (Exception exception) {
            return null;
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
        Matcher money = Pattern.compile("([0-9][0-9 \\u00A0]*)\\s*руб").matcher(window);
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
            if (!seen) {
                seen = trimmed.contains(label);
                continue;
            }
            if (!trimmed.isBlank()) {
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
