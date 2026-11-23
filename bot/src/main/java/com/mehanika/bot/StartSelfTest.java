package com.mehanika.bot;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import com.mehanika.bot.calc.Estimator;
import com.mehanika.bot.catalog.CatalogRepository;
import com.mehanika.bot.catalog.PriceCatalog;
import com.mehanika.bot.dialog.Dialog;
import com.mehanika.bot.dialog.InMessage;
import com.mehanika.bot.lead.LeadStore;
import com.mehanika.bot.telegram.Out;

public final class StartSelfTest {
    private StartSelfTest() {
    }

    public static int run() {
        try {
            CatalogRepository catalogs = new CatalogRepository("file:./prices.json");
            PriceCatalog catalog = catalogs.current();
            if (!catalog.teaching()) {
                System.out.println("start failed");
                return 1;
            }
            Dialog dialog = new Dialog(catalogs, new LeadStore(Path.of("data", "self-test-unused.jsonl")), (Long) null);
            List<Out> outs = dialog.onMessage(new InMessage(1, 1, 1, null, "/start"));
            if (outs.size() != 1 || !(outs.get(0) instanceof Out.Send send)) {
                System.out.println("start failed");
                return 1;
            }
            if (!send.html().equals(Dialog.WELCOME_BODY + "\n\n" + Dialog.TEACHING_LINE)) {
                System.out.println("start failed");
                return 1;
            }
            List<String> labels = send.keyboard().rows().stream().map(row -> row.get(0).text()).toList();
            if (!labels.equals(List.of("Отделка", "Инженерия", "Отделка и инженерия", "Сложная система"))) {
                System.out.println("start failed");
                return 1;
            }
            Estimator.Estimate sample = Estimator.build(sampleJob(), catalog, LocalDate.of(2026, 11, 23));
            Estimator.Estimate electro = Estimator.build(electroJob(), catalog, LocalDate.of(2026, 11, 23));
            if (sample.material() != 150850 || sample.labor() != 210150 || sample.mid() != 361000
                    || sample.low() == null || sample.low() != 270750 || sample.high() == null || sample.high() != 451250) {
                System.out.println("start failed");
                return 1;
            }
            if (electro.material() != 8280 || electro.labor() != 15525 || electro.mid() != 23805
                    || electro.low() == null || electro.low() != 14283 || electro.high() == null || electro.high() != 33327) {
                System.out.println("start failed");
                return 1;
            }
            System.out.println("start ok");
            System.out.println("sample " + sample.material() + " " + sample.labor() + " " + sample.mid()
                    + " " + sample.low() + " " + sample.high());
            System.out.println("electro " + electro.material() + " " + electro.labor() + " " + electro.mid()
                    + " " + electro.low() + " " + electro.high());
            return 0;
        } catch (RuntimeException ex) {
            System.out.println("start failed");
            return 1;
        }
    }

    private static Estimator.Job sampleJob() {
        return new Estimator.Job("both", "flat", "new", "Казань", "standard", 40.0, "paint",
                null, 12, 4, 0, 2, false, 2.7, null, null);
    }

    private static Estimator.Job electroJob() {
        return new Estimator.Job("eng", "flat", "old", "Казань", "economy", null, null,
                null, 10, 0, 0, 0, true, 3.2, null, null);
    }
}
