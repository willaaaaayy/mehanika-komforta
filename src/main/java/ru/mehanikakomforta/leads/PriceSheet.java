package ru.mehanikakomforta.leads;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PriceSheet {

    private static final Pattern NUMBER = Pattern.compile(
            "(\\d{1,3}(?:[ \\u00A0]\\d{3})+|\\d+)(?:[.,](\\d+))?"
    );

    private PriceSheet() {
    }

    static void apply(JsonNode document, String filename, byte[] bytes) {
        if (!(document instanceof ObjectNode catalog) || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("prices");
        }
        List<ParsedRow> rows = rows(filename, bytes);
        int updated = 0;
        for (ParsedRow row : rows) {
            ObjectNode item = match(document, row.title());
            if (item == null || !(item.get("prices") instanceof ObjectNode prices)) {
                continue;
            }
            List<Long> rates = row.rates();
            if (rates.size() == 1) {
                prices.put("economy", rates.get(0));
                prices.put("standard", rates.get(0));
                prices.put("premium", rates.get(0));
            } else if (rates.size() >= 2) {
                prices.put("economy", rates.get(0));
                prices.put("standard", rates.get(1));
                if (rates.size() >= 3) {
                    prices.put("premium", rates.get(2));
                }
            } else {
                continue;
            }
            if (row.laborShare() != null) {
                item.put("laborShare", row.laborShare());
            }
            updated++;
        }
        if (updated == 0) {
            throw new IllegalArgumentException("prices");
        }
        catalog.put("teaching", false);
        catalog.put("note", "Прайс компании. Загружен из файла. Строки, которых не было в файле, оставлены прежними.");
    }

    private static List<ParsedRow> rows(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") || starts(bytes, "%PDF")) {
            return fromText(pdfText(bytes));
        }
        if (name.endsWith(".xlsx") || name.endsWith(".xls") || isZip(bytes) || isOle(bytes)) {
            return fromExcel(bytes);
        }
        throw new IllegalArgumentException("prices");
    }

    private static String pdfText(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception exception) {
            throw new IllegalArgumentException("prices");
        }
    }

    private static List<ParsedRow> fromExcel(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("prices");
            }
            DataFormatter formatter = new DataFormatter();
            int header = headerRow(sheet, formatter);
            List<ParsedRow> rows = new ArrayList<>();
            int first = header < 0 ? sheet.getFirstRowNum() : header + 1;
            int titleColumn = header < 0 ? 0 : titleColumn(sheet.getRow(header), formatter);
            int economy = header < 0 ? -1 : column(sheet.getRow(header), formatter, "эконом", "economy");
            int standard = header < 0 ? -1 : column(sheet.getRow(header), formatter, "стандарт", "standard");
            int premium = header < 0 ? -1 : column(sheet.getRow(header), formatter, "премиум", "premium");
            for (int index = first; index <= sheet.getLastRowNum() && rows.size() < 500; index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                String title = formatter.formatCellValue(row.getCell(titleColumn)).trim();
                if (title.isBlank() || looksLikeHeader(title)) {
                    continue;
                }
                List<Double> numbers = new ArrayList<>();
                if (economy >= 0 || standard >= 0 || premium >= 0) {
                    add(numbers, cellNumber(row.getCell(economy), formatter));
                    add(numbers, cellNumber(row.getCell(standard), formatter));
                    add(numbers, cellNumber(row.getCell(premium), formatter));
                } else {
                    for (int cell = titleColumn + 1; cell < row.getLastCellNum(); cell++) {
                        add(numbers, cellNumber(row.getCell(cell), formatter));
                    }
                    if (numbers.isEmpty()) {
                        rows.addAll(fromText(title));
                        continue;
                    }
                }
                ParsedRow parsed = parsed(title, numbers);
                if (parsed != null) {
                    rows.add(parsed);
                }
            }
            return rows;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("prices");
        }
    }

    private static List<ParsedRow> fromText(String text) {
        List<ParsedRow> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        for (String line : text.split("\\R")) {
            if (rows.size() >= 500) {
                break;
            }
            int firstDigit = indexOfDigit(line);
            if (firstDigit < 0) {
                continue;
            }
            String title = line.substring(0, firstDigit).trim();
            if (title.length() < 2 || looksLikeHeader(title)) {
                continue;
            }
            List<Double> numbers = splitPrices(line.substring(firstDigit));
            ParsedRow parsed = parsed(title, numbers);
            if (parsed != null) {
                rows.add(parsed);
            }
        }
        return rows;
    }

    private static int indexOfDigit(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (Character.isDigit(line.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    static List<Double> splitPrices(String tail) {
        String[] tokens = tail.trim().split("[\\s\\u00A0]+");
        List<Double> numbers = new ArrayList<>();
        int index = 0;
        while (index < tokens.length) {
            String token = tokens[index].replace(',', '.');
            if (!token.matches("\\d+(?:\\.\\d+)?")) {
                index++;
                continue;
            }
            int dot = token.indexOf('.');
            String whole = dot < 0 ? token : token.substring(0, dot);
            String fraction = dot < 0 ? null : token.substring(dot + 1);
            index++;
            boolean grouped = false;
            if (fraction == null) {
                while (index < tokens.length && tokens[index].matches("\\d{3}")) {
                    boolean thousand = grouped || whole.length() < 3 || "000".equals(tokens[index]);
                    if (!thousand) {
                        break;
                    }
                    whole = whole + tokens[index];
                    grouped = true;
                    index++;
                }
            }
            numbers.add(Double.parseDouble(fraction == null ? whole : whole + "." + fraction));
        }
        return numbers;
    }

    private static ParsedRow parsed(String title, List<Double> numbers) {
        List<Long> rates = new ArrayList<>();
        Double share = null;
        for (Double number : numbers) {
            if (number == null || number < 0) {
                continue;
            }
            if (number <= 1 && !rates.isEmpty()) {
                share = number;
                continue;
            }
            rates.add(Math.round(number));
        }
        if (rates.isEmpty()) {
            return null;
        }
        return new ParsedRow(title, rates, share);
    }

    private static ObjectNode match(JsonNode document, String title) {
        String wanted = normalize(title);
        ObjectNode found = null;
        int best = 0;
        boolean tie = false;
        for (String section : List.of("works", "points")) {
            JsonNode group = document.get(section);
            if (group == null) {
                continue;
            }
            for (JsonNode item : group) {
                if (!(item instanceof ObjectNode node) || item.get("title") == null) {
                    continue;
                }
                String current = normalize(item.get("title").asString());
                int score = 0;
                if (current.equals(wanted)) {
                    score = 1000 + current.length();
                } else if (current.length() >= 6 && wanted.length() >= 6
                        && (current.contains(wanted) || wanted.contains(current))) {
                    score = Math.min(current.length(), wanted.length());
                }
                if (score > best) {
                    best = score;
                    found = node;
                    tie = false;
                } else if (score > 0 && score == best) {
                    tie = true;
                }
            }
        }
        return tie ? null : found;
    }

    private static int headerRow(Sheet sheet, DataFormatter formatter) {
        int last = Math.min(sheet.getLastRowNum(), 20);
        for (int index = sheet.getFirstRowNum(); index <= last; index++) {
            Row row = sheet.getRow(index);
            if (row == null) {
                continue;
            }
            if (column(row, formatter, "эконом", "economy") >= 0
                    || column(row, formatter, "стандарт", "standard") >= 0
                    || column(row, formatter, "премиум", "premium") >= 0) {
                return index;
            }
        }
        return -1;
    }

    private static int titleColumn(Row row, DataFormatter formatter) {
        int found = column(row, formatter, "работа", "наименование", "позиция", "услуга", "название");
        return found >= 0 ? found : 0;
    }

    private static int column(Row row, DataFormatter formatter, String... names) {
        if (row == null) {
            return -1;
        }
        for (Cell cell : row) {
            String value = normalize(formatter.formatCellValue(cell));
            for (String name : names) {
                if (value.equals(name) || value.contains(name)) {
                    return cell.getColumnIndex();
                }
            }
        }
        return -1;
    }

    private static Double cellNumber(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        Matcher matcher = NUMBER.matcher(formatter.formatCellValue(cell));
        if (!matcher.matches()) {
            return null;
        }
        return Double.parseDouble(matcher.group().replace(" ", "").replace("\u00A0", "").replace(',', '.'));
    }

    private static void add(List<Double> numbers, Double value) {
        if (value != null) {
            numbers.add(value);
        }
    }

    private static boolean looksLikeHeader(String title) {
        String value = normalize(title);
        return value.equals("работа") || value.equals("наименование") || value.equals("позиция") || value.equals("услуга");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^a-zа-я0-9]+", " ").trim();
    }

    private static boolean starts(byte[] bytes, String prefix) {
        if (bytes.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (bytes[i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isZip(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private static boolean isOle(byte[] bytes) {
        return bytes.length > 4 && (bytes[0] & 0xff) == 0xd0 && (bytes[1] & 0xff) == 0xcf;
    }

    private record ParsedRow(String title, List<Long> rates, Double laborShare) {
    }
}
