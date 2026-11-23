package com.mehanika.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mehanika.bot.calc.Estimator;
import com.mehanika.bot.catalog.CatalogRepository;
import com.mehanika.bot.catalog.PriceCatalog;
import com.mehanika.bot.dialog.Dialog;
import com.mehanika.bot.dialog.InCallback;
import com.mehanika.bot.dialog.InMessage;
import com.mehanika.bot.dialog.ObjectPhoto;
import com.mehanika.bot.lead.LeadPhoto;
import com.mehanika.bot.lead.CrmLeadSink;
import com.mehanika.bot.lead.LeadStore;
import com.mehanika.bot.telegram.Out;
import com.mehanika.bot.telegram.UpdateRouter;
import tools.jackson.databind.node.ObjectNode;

class AcceptanceTest {
    private final CatalogRepository catalogs = new CatalogRepository("file:src/main/resources/prices.json");
    private final PriceCatalog catalog = catalogs.current();
    private final LocalDate day = LocalDate.of(2026, 11, 23);

    @Test
    void teachingPriceStaysMarkedAndOrdered() {
        assertTrue(catalog.teaching());
        assertEquals("screed", catalog.works().get(0).id());
        assertEquals("stretch", catalog.works().get(catalog.works().size() - 1).id());
        assertEquals(800, catalog.work("screed").rate().economy());
        assertEquals(2500, catalog.point("electro").rate().standard());
    }

    @Test
    void sampleTextMatchesTheLiveBot() throws IOException {
        Estimator.Job job = sample();
        Estimator.Estimate estimate = Estimator.build(job, catalog, day);
        assertEquals(150850, estimate.material());
        assertEquals(210150, estimate.labor());
        assertEquals(361000, estimate.mid());
        assertEquals(270750, estimate.low());
        assertEquals(451250, estimate.high());
        String expected = Files.readString(Path.of("src/test/resources/sample-both.txt")).stripTrailing();
        assertEquals(expected, Estimator.render(job, estimate, catalog));
    }

    @Test
    void electroChaseAndTallCeiling() {
        Estimator.Estimate estimate = Estimator.build(new Estimator.Job(
                "eng", "flat", "old", "Казань", "economy", null, null, null,
                10, 0, 0, 0, true, 3.2, null, null), catalog, day);
        assertEquals(8280, estimate.material());
        assertEquals(15525, estimate.labor());
        assertEquals(23805, estimate.mid());
        assertEquals(14283, estimate.low());
        assertEquals(33327, estimate.high());
        assertTrue(estimate.adjustments().contains("Работы инженерии ×1.25 за штробление."));
        assertTrue(estimate.adjustments().contains("Инженерия ×1.15 — потолок выше 3 м."));
    }

    @Test
    void bothWithoutPointsUsesFinishSpread() {
        Estimator.Estimate estimate = Estimator.build(new Estimator.Job(
                "both", "flat", "new", "Казань", "standard", 40.0, "paint", null,
                0, 0, 0, 0, null, null, null, null), catalog, day);
        assertEquals(293000, estimate.mid());
        assertEquals(249050, estimate.low());
        assertEquals(336950, estimate.high());
    }

    @Test
    void smallOrderKeepsThePriceAndWarns() {
        Estimator.Estimate estimate = Estimator.build(new Estimator.Job(
                "eng", "flat", "new", "Казань", "economy", null, null, null,
                1, 0, 0, 0, false, 2.7, null, null), catalog, day);
        assertEquals(1800, estimate.mid());
        assertEquals(1350, estimate.low());
        assertEquals(2250, estimate.high());
        assertTrue(estimate.assumptions().stream().anyMatch(line -> line.contains("15 000 ₽")));
    }

    @Test
    void plasterAndDrywallAndPlantStayOnTheFormula() {
        Estimator.Estimate walls = Estimator.build(new Estimator.Job(
                "finish", "office", "old", "Казань", "premium", 20.0, "custom",
                List.of("plaster", "drywall"), null, null, null, null, null, 2.7, null, null), catalog, day);
        assertEquals(165000, walls.mid());
        assertEquals(123750, walls.low());
        assertEquals(206250, walls.high());
        assertTrue(walls.assumptions().stream().anyMatch(line -> line.contains("Штукатурка и ГКЛ")));

        Estimator.Estimate plant = Estimator.build(new Estimator.Job(
                "finish", "plant", "new", "Казань", "economy", 10.0, "custom",
                List.of("screed"), null, null, null, null, null, 2.7, null, null), catalog, day);
        assertEquals(8000, plant.mid());
        assertEquals(6800, plant.low());
        assertEquals(9200, plant.high());
        assertTrue(plant.assumptions().stream().anyMatch(line -> line.startsWith("Производство:")));
    }

    @Test
    void complexHasNoFork() {
        Estimator.Job job = new Estimator.Job(
                "complex", "plant", "old", "Казань", "premium", null, null, null,
                null, null, null, null, null, null, List.of("panel", "air"), "цех");
        Estimator.Estimate estimate = Estimator.build(job, catalog, day);
        assertFalse(estimate.priced());
        assertNull(estimate.low());
        String text = Estimator.render(job, estimate, catalog);
        assertTrue(text.contains("Автоматической цены по выбранным работам нет."));
        assertTrue(text.contains("На обследование: Сборка щита, Сжатый воздух."));
        assertFalse(text.contains("Вилка:"));
    }

    @Test
    void wallQuantitiesMatchPythonRounding() {
        record Row(double floor, double height, double qty, int total) {
        }
        List<Row> rows = List.of(
                new Row(1, 2.7, 2.5, 2375),
                new Row(1, 3.2, 3.0, 2850),
                new Row(10.24, 2.7, 25.6, 24320),
                new Row(10.24, 3.2, 30.3, 28785),
                new Row(42.5, 2.7, 106.2, 100890),
                new Row(42.5, 3.2, 125.9, 119605),
                new Row(100, 2.7, 250.0, 237500),
                new Row(100, 3.2, 296.3, 281485),
                new Row(333.33, 2.7, 833.3, 791635),
                new Row(333.33, 3.2, 987.6, 938220),
                new Row(0.4, 2.7, 1.0, 950),
                new Row(0.4, 3.2, 1.2, 1140));
        for (Row row : rows) {
            Estimator.Estimate estimate = Estimator.build(new Estimator.Job(
                    "finish", "flat", "new", "Казань", "standard", row.floor(), "custom",
                    List.of("plaster"), null, null, null, null, null, row.height(), null, null), catalog, day);
            assertEquals(row.qty(), estimate.lines().get(0).qty(), 1e-9);
            assertEquals(row.total(), estimate.lines().get(0).total());
        }
    }

    @Test
    void clientOverrideChangesOnlyThatRate() throws IOException {
        Path file = Files.createTempFile("prices", ".json");
        ObjectNode root = (ObjectNode) Jsons.MAPPER.readTree(Files.readString(Path.of("src/main/resources/prices.json")));
        ObjectNode screed = Jsons.MAPPER.createObjectNode();
        screed.putObject("prices").put("standard", 5000);
        root.putObject("clientOverrides").putObject("c1").putObject("works").set("screed", screed);
        Files.writeString(file, Jsons.MAPPER.writeValueAsString(root));
        PriceCatalog custom = new CatalogRepository(file.toString()).forClient("c1");
        Estimator.Job job = new Estimator.Job(
                "finish", "flat", "new", "Казань", "standard", 40.0, "custom",
                List.of("screed"), null, null, null, null, null, 2.7, null, null);
        assertEquals(44000, Estimator.build(job, catalog, day).mid());
        assertEquals(200000, Estimator.build(job, custom, day).mid());
        assertEquals(170000, Estimator.build(job, custom, day).low());
        assertEquals(230000, Estimator.build(job, custom, day).high());
    }

    @Test
    void startUpdateAnswersWithWelcome() {
        Dialog dialog = dialog(new LeadStore(Path.of("target", "unused-leads.jsonl")), null);
        String json = """
                {"update_id":7,"message":{"message_id":3,"from":{"id":42,"first_name":"A"},"chat":{"id":42,"type":"private"},"text":"/start"}}
                """;
        List<Out> outs = new UpdateRouter(dialog).handle(Jsons.MAPPER.readTree(json));
        Out.Send send = (Out.Send) outs.get(0);
        assertEquals(Dialog.WELCOME_BODY + "\n\n" + Dialog.TEACHING_LINE, send.html());
        assertEquals(List.of("Отделка", "Инженерия", "Отделка и инженерия", "Сложная система"),
                send.keyboard().rows().stream().map(row -> row.get(0).text()).toList());
    }

    @Test
    void dialogReachesTheSampleForkAndSavesTheTypedPhone() throws IOException {
        Path file = Files.createTempFile("leads", ".jsonl");
        Dialog dialog = dialog(new LeadStore(file), 77L);
        List<Out> outs = new ArrayList<>();
        driveSample(dialog, outs);
        assertTrue(text(outs).contains("270 750 ₽ – 451 250 ₽"));
        outs.clear();
        outs.addAll(dialog.onMessage(msg("Иван")));
        outs.addAll(dialog.onMessage(msg("нет")));
        assertTrue(text(outs).contains("Нужен телефон: не меньше 10 цифр."));
        outs.clear();
        String phone = "+7 (900) 000-00-00";
        outs.addAll(dialog.onMessage(msg(phone)));
        outs.addAll(dialog.onCallback(cb("ph:done")));
        assertTrue(text(outs).contains("Телефон: " + phone));
        outs.clear();
        outs.addAll(dialog.onCallback(cb("ok:1")));
        assertTrue(text(outs).contains("Заявку записал."));
        assertTrue(outs.stream().anyMatch(out -> out instanceof Out.Send send && send.chatId() == 77 && send.bestEffort()));
        Jsons.MAPPER.readTree(Files.readString(file).lines().findFirst().orElseThrow());
        assertTrue(Files.readString(file).contains(phone));
        assertEquals(1, Files.readAllLines(file).size());
        assertTrue(dialog.onCallback(cb("ok:1")).isEmpty());
    }

    @Test
    void photosComeAfterThePhoneAndStayOptional() {
        Dialog dialog = dialog(new LeadStore(Path.of("target", "unused-leads.jsonl")), null);
        driveSample(dialog, new ArrayList<>());
        dialog.onMessage(msg("Иван"));
        String asked = text(dialog.onMessage(msg("9000000000")));
        assertTrue(asked.contains("Если есть фото объекта"));
        assertTrue(text(dialog.onMessage(msg("нет"))).contains("Нужно фото. Или нажмите «Дальше»."));
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
        for (int i = 0; i < 10; i++) {
            assertTrue(dialog.onObjectPhoto(42, ObjectPhoto.ok(new LeadPhoto("image/jpeg", jpeg))).isEmpty());
        }
        assertTrue(text(dialog.onObjectPhoto(42, ObjectPhoto.tooBig())).contains("Можно не больше 10 фото"));
        assertTrue(text(dialog.onCallback(cb("ph:done"))).contains("Телефон: 9000000000"));
    }

    @Test
    void forwardSendsTheChosenObjectNotTheEstimate() throws IOException {
        Path file = Files.createTempFile("leads", ".jsonl");
        List<String> objects = new ArrayList<>();
        CrmLeadSink sink = (name, phone, objectType) -> {
            objects.add(name + "|" + phone + "|" + objectType);
            return CrmLeadSink.Result.SENT;
        };
        Dialog dialog = new Dialog(catalogs, new LeadStore(file), 77L, sink);
        driveSample(dialog, new ArrayList<>());
        dialog.onMessage(msg("Иван"));
        dialog.onMessage(msg("+7 (900) 000-00-00"));
        dialog.onCallback(cb("ph:done"));
        String reply = text(dialog.onCallback(cb("ok:1")));
        assertTrue(reply.contains("Заявку записал."));
        assertEquals(List.of("Иван|+7 (900) 000-00-00|Квартира"), objects);
    }

    @Test
    void failedSaveRollsBack() {
        LeadStore broken = new LeadStore(Path.of("target", "missing", "leads.jsonl")) {
            @Override
            public String append(Map<String, Object> lead) {
                throw new UncheckedIOException(new IOException("disk"));
            }
        };
        Dialog dialog = dialog(broken, null);
        driveSample(dialog, new ArrayList<>());
        dialog.onMessage(msg("Иван"));
        dialog.onMessage(msg("9000000000"));
        dialog.onCallback(cb("ph:done"));
        List<Out> outs = dialog.onCallback(cb("ok:1"));
        assertTrue(outs.stream().anyMatch(out -> out instanceof Out.Toast toast
                && "Не удалось записать заявку, попробуйте ещё раз".equals(toast.text())));
        assertTrue(text(dialog.onCallback(cb("ok:1"))).contains("Не удалось записать заявку"));
    }

    @Test
    void engineeringWithoutPointsReturnsToElectrics() {
        Dialog dialog = dialog(new LeadStore(Path.of("target", "unused-leads.jsonl")), null);
        dialog.onMessage(msg("/start"));
        dialog.onCallback(cb("sec:eng"));
        dialog.onCallback(cb("ob:flat"));
        dialog.onCallback(cb("cd:old"));
        dialog.onMessage(msg("Казань"));
        dialog.onCallback(cb("gr:economy"));
        dialog.onCallback(cb("el:0"));
        dialog.onCallback(cb("wa:0"));
        dialog.onCallback(cb("cm:0"));
        List<Out> outs = dialog.onCallback(cb("ln:0"));
        assertTrue(text(outs).contains("Нужна хотя бы одна точка."));
        assertTrue(text(outs).contains("Сколько электрических точек?"));
    }

    @Test
    void parsersRejectTheSameShapes() {
        assertNull(Dialog.parseArea("нет"));
        assertNull(Dialog.parseArea("0"));
        assertNull(Dialog.parseArea("50000.1"));
        assertEquals(54.5, Dialog.parseArea("54,5"));
        assertEquals(50000.0, Dialog.parseArea("50 000"));
        assertNull(Dialog.parseCount("3.5"));
        assertNull(Dialog.parseCount("5001"));
        assertEquals(0, Dialog.parseCount("0"));
        assertEquals(5000, Dialog.parseCount("5000"));
    }

    private static Estimator.Job sample() {
        return new Estimator.Job("both", "flat", "new", "Казань", "standard", 40.0, "paint",
                null, 12, 4, 0, 2, false, 2.7, null, null);
    }

    private Dialog dialog(LeadStore leads, Long admin) {
        return new Dialog(catalogs, leads, admin);
    }

    private void driveSample(Dialog dialog, List<Out> outs) {
        outs.addAll(dialog.onMessage(msg("/start")));
        outs.addAll(dialog.onCallback(cb("sec:both")));
        outs.addAll(dialog.onCallback(cb("ob:flat")));
        outs.addAll(dialog.onCallback(cb("cd:new")));
        outs.addAll(dialog.onMessage(msg("Казань")));
        outs.addAll(dialog.onCallback(cb("gr:standard")));
        outs.addAll(dialog.onMessage(msg("40")));
        outs.addAll(dialog.onCallback(cb("fm:paint")));
        outs.addAll(dialog.onCallback(cb("el:12")));
        outs.addAll(dialog.onCallback(cb("wa:4")));
        outs.addAll(dialog.onCallback(cb("cm:0")));
        outs.addAll(dialog.onCallback(cb("ln:2")));
        outs.addAll(dialog.onCallback(cb("ch:0")));
        outs.addAll(dialog.onCallback(cb("ht:27")));
    }

    private static InMessage msg(String text) {
        return new InMessage(42, 1, 42, "tester", text);
    }

    private static InCallback cb(String data) {
        return new InCallback(42, 1, 42, "tester", "cb", data);
    }

    private static String text(List<Out> outs) {
        StringBuilder builder = new StringBuilder();
        for (Out out : outs) {
            if (out instanceof Out.Send send) {
                builder.append(send.html()).append('\n');
            }
            if (out instanceof Out.Toast toast && toast.text() != null) {
                builder.append(toast.text()).append('\n');
            }
        }
        return builder.toString();
    }
}
