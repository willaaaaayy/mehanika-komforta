package com.mehanika.bot.dialog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.mehanika.bot.BotSettings;
import com.mehanika.bot.calc.Estimator;
import com.mehanika.bot.catalog.CatalogRepository;
import com.mehanika.bot.catalog.PriceCatalog;
import com.mehanika.bot.lead.LeadStore;
import com.mehanika.bot.telegram.Keyboard;
import com.mehanika.bot.telegram.Out;

@Component
public class Dialog {
    public static final String WELCOME_BODY = "<b>Механика комфорта</b>\n"
            + "Предварительный расчёт отделки и квартирной инженерии.\n\n"
            + "В ответе будет вилка, не цена договора. Точную смету фиксируем после замера. "
            + "Щиты, сжатый воздух, СКУД и другие сложные системы бот только принимает в заявку.";
    public static final String TEACHING_LINE = "Сейчас в боте учебный прайс — его заменим вашими ценами.";
    static final String HELP = "Бот собирает объект и считает вилку по учебному прайсу.\n"
            + "/start — новый расчёт\n"
            + "/cancel — сбросить диалог\n"
            + "/myid — ваш Telegram id для уведомлений менеджеру";
    private static final String NEED = "На этом шаге нужно выбрать кнопку или отправить текст в нужном формате. Сбросить диалог — /cancel";
    private static final String IDLE = "Нажмите /start, чтобы собрать предварительный расчёт.";
    private static final String SUCCESS = "Заявку записал. Менеджер сверит объём и скажет, когда может выехать на замер.\n"
            + "Новый расчёт — /start";

    private final CatalogRepository catalogs;
    private final LeadStore leads;
    private final Long adminChatId;
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public Dialog(CatalogRepository catalogs, LeadStore leads, BotSettings settings) {
        this(catalogs, leads, settings.adminChatId());
    }

    public Dialog(CatalogRepository catalogs, LeadStore leads, Long adminChatId) {
        this.catalogs = catalogs;
        this.leads = leads;
        this.adminChatId = adminChatId;
    }

    public List<Out> onMessage(InMessage message) {
        synchronized (lock(message.chatId())) {
            String command = command(message.text());
            if ("start".equals(command) || "cancel".equals(command)) {
                return start(message.chatId(), message.userId(), message.username());
            }
            Session session = session(message.chatId(), message.userId(), message.username());
            if ("help".equals(command)) {
                return List.of(send(session.chatId, HELP, null));
            }
            if ("myid".equals(command)) {
                return List.of(send(session.chatId, "Ваш id: <code>" + message.userId() + "</code>", null));
            }
            if (message.text() == null) {
                return List.of(send(session.chatId, session.step == Step.NONE ? IDLE : NEED, null));
            }
            return switch (session.step) {
                case CITY -> onCity(session, message.text());
                case AREA -> onArea(session, message.text());
                case ELECTRO_N -> onCount(session, message.text(), "electro", this::askWater);
                case WATER_N -> onCount(session, message.text(), "water", this::askCameras);
                case CAMERAS_N -> onCount(session, message.text(), "cameras", this::askLan);
                case LAN_N -> onCount(session, message.text(), "lan", this::afterPoints);
                case COMPLEX_TEXT -> onComplexText(session, message.text());
                case NAME -> onName(session, message.text());
                case PHONE -> onPhone(session, message.text());
                case NONE -> List.of(send(session.chatId, IDLE, null));
                default -> List.of(send(session.chatId, NEED, null));
            };
        }
    }

    public List<Out> onCallback(InCallback query) {
        synchronized (lock(query.chatId())) {
            Session session = sessions.get(query.chatId());
            if (session == null || session.step == Step.NONE) {
                return List.of();
            }
            session.remember(query.userId(), query.username());
            return switch (session.step) {
                case SECTION -> onSection(session, query);
                case OBJECT -> onChoice(session, query, "ob:", () -> {
                    session.objectType = value(query.data());
                    askCondition(session);
                });
                case CONDITION -> onChoice(session, query, "cd:", () -> {
                    session.condition = value(query.data());
                    session.step = Step.CITY;
                    session.pending.add(send(session.chatId, "В каком городе объект? Напишите название.", null));
                });
                case GRADE -> onChoice(session, query, "gr:", () -> {
                    session.grade = value(query.data());
                    afterCommon(session);
                });
                case FINISH_MODE -> onFinishMode(session, query);
                case WORKS -> onWorks(session, query);
                case ELECTRO -> onQty(session, query, "el:", "electro", Step.ELECTRO_N, this::askWater);
                case WATER -> onQty(session, query, "wa:", "water", Step.WATER_N, this::askCameras);
                case CAMERAS -> onQty(session, query, "cm:", "cameras", Step.CAMERAS_N, this::askLan);
                case LAN -> onQty(session, query, "ln:", "lan", Step.LAN_N, this::afterPoints);
                case CHASE -> onChase(session, query);
                case HEIGHT -> onHeight(session, query);
                case COMPLEX_PICK -> onComplex(session, query);
                case CONFIRM -> onConfirm(session, query);
                default -> List.of();
            };
        }
    }

    private List<Out> start(long chatId, long userId, String username) {
        Session session = new Session(chatId);
        session.remember(userId, username);
        session.step = Step.SECTION;
        sessions.put(chatId, session);
        PriceCatalog catalog = catalogs.forClient(null);
        String welcome = catalog.teaching() ? WELCOME_BODY + "\n\n" + TEACHING_LINE : WELCOME_BODY;
        return List.of(send(chatId, welcome, sectionKeyboard()));
    }

    private List<Out> onSection(Session session, InCallback query) {
        if (!query.data().startsWith("sec:")) {
            return List.of();
        }
        session.section = value(query.data());
        session.step = Step.OBJECT;
        return List.of(
                toast(query, null),
                clear(query),
                send(session.chatId, "Какой объект?", choiceKeyboard("ob:", catalogs.forClient(null).objects(), 2)));
    }

    private List<Out> onChoice(Session session, InCallback query, String prefix, Runnable apply) {
        if (!query.data().startsWith(prefix)) {
            return List.of();
        }
        session.pending.clear();
        apply.run();
        List<Out> outs = new ArrayList<>();
        outs.add(toast(query, null));
        outs.add(clear(query));
        outs.addAll(session.pending);
        session.pending.clear();
        return outs;
    }

    private void askCondition(Session session) {
        session.step = Step.CONDITION;
        session.pending.add(send(session.chatId, "Состояние объекта?", choiceKeyboard("cd:", catalogs.forClient(null).conditions(), 2)));
    }

    private List<Out> onCity(Session session, String text) {
        String city = text.strip();
        if (city.length() < 2 || city.length() > 80) {
            return List.of(send(session.chatId, "Напишите город, например: Казань.", null));
        }
        session.city = city;
        session.step = Step.GRADE;
        return List.of(send(session.chatId, "Класс материалов и оборудования?", choiceKeyboard("gr:", catalogs.forClient(null).grades(), 1)));
    }

    private void afterCommon(Session session) {
        if ("finish".equals(session.section) || "both".equals(session.section)) {
            askArea(session);
        } else if ("eng".equals(session.section)) {
            askElectro(session);
        } else {
            askComplex(session);
        }
    }

    private void askArea(Session session) {
        session.step = Step.AREA;
        session.pending.add(send(session.chatId, "Площадь пола, м². Можно дробное число, например 42,5.", null));
    }

    private List<Out> onArea(Session session, String text) {
        Double area = parseArea(text);
        if (area == null) {
            return List.of(send(session.chatId, "Нужна площадь числом, например 54 или 54,5.", null));
        }
        session.floorArea = area;
        session.pending.clear();
        askFinishMode(session);
        return session.pending;
    }

    private void askFinishMode(Session session) {
        session.step = Step.FINISH_MODE;
        session.pending.add(send(session.chatId, "Какой состав отделки посчитать?", Keyboard.of(
                List.of(new Keyboard.Button("Под покраску", "fm:paint")),
                List.of(new Keyboard.Button("Под ключ без санузлов", "fm:turnkey")),
                List.of(new Keyboard.Button("Выбрать работы", "fm:custom")))));
    }

    private List<Out> onFinishMode(Session session, InCallback query) {
        if (!query.data().startsWith("fm:")) {
            return List.of();
        }
        String mode = value(query.data());
        if ("custom".equals(mode)) {
            session.finishMode = "custom";
            session.works = new ArrayList<>();
            session.step = Step.WORKS;
            return List.of(toast(query, null), clear(query),
                    send(session.chatId, "Отметьте работы и нажмите «Дальше».", worksKeyboard(List.of())));
        }
        PriceCatalog.Bundle bundle = catalogs.forClient(null).bundle(mode);
        if (bundle == null) {
            return List.of();
        }
        session.finishMode = mode;
        session.works = new ArrayList<>(bundle.works());
        session.pending.clear();
        afterFinish(session);
        List<Out> outs = new ArrayList<>();
        outs.add(toast(query, null));
        outs.add(clear(query));
        outs.addAll(session.pending);
        session.pending.clear();
        return outs;
    }

    private List<Out> onWorks(Session session, InCallback query) {
        if (!query.data().startsWith("wk:")) {
            return List.of();
        }
        String code = value(query.data());
        if ("done".equals(code)) {
            if (session.works == null || session.works.isEmpty()) {
                return List.of(toast(query, "Выберите хотя бы одну работу"));
            }
            session.pending.clear();
            afterFinish(session);
            List<Out> outs = new ArrayList<>();
            outs.add(toast(query, null));
            outs.add(clear(query));
            outs.addAll(session.pending);
            session.pending.clear();
            return outs;
        }
        PriceCatalog catalog = catalogs.forClient(null);
        boolean known = catalog.works().stream().anyMatch(work -> work.id().equals(code));
        if (!known) {
            return List.of();
        }
        if (session.works.contains(code)) {
            session.works.remove(code);
        } else {
            session.works.add(code);
        }
        return List.of(toast(query, null), new Out.Markup(query.chatId(), query.messageId(), worksKeyboard(session.works)));
    }

    private void afterFinish(Session session) {
        if ("both".equals(session.section)) {
            askElectro(session);
        } else {
            showEstimate(session);
        }
    }

    private void askElectro(Session session) {
        session.step = Step.ELECTRO;
        session.pending.add(send(session.chatId,
                "Сколько электрических точек? Розетка, выключатель или вывод света — это одна точка. "
                        + "0, если электрика не нужна. Щит и ввод сюда не входят.",
                qtyKeyboard("el:")));
    }

    private void askWater(Session session) {
        session.step = Step.WATER;
        session.pending.add(send(session.chatId,
                "Сколько сантехнических точек? Один вывод воды или канализации. 0, если сантехника не нужна.",
                qtyKeyboard("wa:")));
    }

    private void askCameras(Session session) {
        session.step = Step.CAMERAS;
        session.pending.add(send(session.chatId,
                "Сколько типовых камер в помещении? 0, если видеонаблюдение не нужно. "
                        + "Улица, цех и регистратор на объект считаются отдельно, через сложную систему.",
                qtyKeyboard("cm:")));
    }

    private void askLan(Session session) {
        session.step = Step.LAN;
        session.pending.add(send(session.chatId,
                "Сколько точек интернета или розеток СКС? 0, если сеть не нужна.",
                qtyKeyboard("ln:")));
    }

    private List<Out> onQty(Session session, InCallback query, String prefix, String field, Step custom, java.util.function.Consumer<Session> next) {
        if (!query.data().startsWith(prefix)) {
            return List.of();
        }
        String raw = query.data().substring(prefix.length());
        if ("x".equals(raw)) {
            session.step = custom;
            return List.of(toast(query, null), send(session.chatId, "Введите количество целым числом.", null));
        }
        int count;
        try {
            count = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return List.of();
        }
        putCount(session, field, count);
        session.pending.clear();
        next.accept(session);
        List<Out> outs = new ArrayList<>();
        outs.add(toast(query, null));
        outs.add(clear(query));
        outs.addAll(session.pending);
        session.pending.clear();
        return outs;
    }

    private List<Out> onCount(Session session, String text, String field, java.util.function.Consumer<Session> next) {
        Integer count = parseCount(text);
        if (count == null) {
            return List.of(send(session.chatId, "Нужно целое число от 0 до 5000.", null));
        }
        putCount(session, field, count);
        session.pending.clear();
        next.accept(session);
        List<Out> outs = new ArrayList<>(session.pending);
        session.pending.clear();
        return outs;
    }

    private void afterPoints(Session session) {
        int total = n(session.electro) + n(session.water) + n(session.cameras) + n(session.lan);
        if (total == 0 && "eng".equals(session.section)) {
            session.pending.add(send(session.chatId,
                    "Нужна хотя бы одна точка. Если объект сложнее квартирной разводки, "
                            + "начните заново и выберите «Сложная система». Сброс — /cancel",
                    null));
            askElectro(session);
            return;
        }
        if (total == 0) {
            showEstimate(session);
            return;
        }
        askChase(session);
    }

    private void askChase(Session session) {
        session.step = Step.CHASE;
        session.pending.add(send(session.chatId, "Трассы нужно штробить?", Keyboard.of(List.of(
                new Keyboard.Button("Да, штробление", "ch:1"),
                new Keyboard.Button("Нет", "ch:0")))));
    }

    private List<Out> onChase(Session session, InCallback query) {
        if (!query.data().startsWith("ch:")) {
            return List.of();
        }
        session.chase = query.data().endsWith(":1");
        session.pending.clear();
        askHeight(session);
        List<Out> outs = new ArrayList<>();
        outs.add(toast(query, null));
        outs.add(clear(query));
        outs.addAll(session.pending);
        session.pending.clear();
        return outs;
    }

    private void askHeight(Session session) {
        session.step = Step.HEIGHT;
        session.pending.add(send(session.chatId, "Высота потолка?", Keyboard.of(List.of(
                new Keyboard.Button("До 3 м", "ht:27"),
                new Keyboard.Button("Выше 3 м", "ht:32")))));
    }

    private List<Out> onHeight(Session session, InCallback query) {
        if (!query.data().startsWith("ht:")) {
            return List.of();
        }
        PriceCatalog catalog = catalogs.forClient(session.clientId);
        session.heightM = query.data().endsWith(":32") ? catalog.highHeightM() : catalog.defaultHeight();
        session.pending.clear();
        showEstimate(session);
        List<Out> outs = new ArrayList<>();
        outs.add(toast(query, null));
        outs.add(clear(query));
        outs.addAll(session.pending);
        session.pending.clear();
        return outs;
    }

    private void askComplex(Session session) {
        session.step = Step.COMPLEX_PICK;
        session.complexItems = new ArrayList<>();
        session.pending.add(send(session.chatId,
                "Отметьте системы, по которым нужна заявка. Точную цену по ним бот не ставит.",
                complexKeyboard(List.of())));
    }

    private List<Out> onComplex(Session session, InCallback query) {
        if (!query.data().startsWith("cx:")) {
            return List.of();
        }
        String code = value(query.data());
        if ("done".equals(code)) {
            if (session.complexItems == null || session.complexItems.isEmpty()) {
                return List.of(toast(query, "Отметьте хотя бы одну систему"));
            }
            session.step = Step.COMPLEX_TEXT;
            return List.of(toast(query, null), clear(query), send(session.chatId,
                    "Что уже известно: мощность, число дверей, давление, бренд? Если данных мало, напишите «пока не знаю».",
                    null));
        }
        if (!catalogs.forClient(null).complex().containsKey(code)) {
            return List.of();
        }
        if (session.complexItems.contains(code)) {
            session.complexItems.remove(code);
        } else {
            session.complexItems.add(code);
        }
        return List.of(toast(query, null), new Out.Markup(query.chatId(), query.messageId(), complexKeyboard(session.complexItems)));
    }

    private List<Out> onComplexText(Session session, String text) {
        String note = text.strip();
        if (note.length() > 1000) {
            return List.of(send(session.chatId, "Сократите описание до 1000 символов.", null));
        }
        session.complexNote = note;
        session.pending.clear();
        showEstimate(session);
        List<Out> outs = new ArrayList<>(session.pending);
        session.pending.clear();
        return outs;
    }

    private void showEstimate(Session session) {
        PriceCatalog catalog = catalogs.forClient(session.clientId);
        Estimator.Job job = session.job();
        Estimator.Estimate estimate = Estimator.build(job, catalog, LocalDate.now());
        String text = Estimator.render(job, estimate, catalog);
        session.estimateText = text;
        for (String part : Texts.pre(text)) {
            session.pending.add(send(session.chatId, part, null));
        }
        session.step = Step.NAME;
        session.pending.add(send(session.chatId, "Как к вам обращаться? Напишите имя.", null));
    }

    private List<Out> onName(Session session, String text) {
        String name = text.strip();
        if (name.length() < 2 || name.length() > 60) {
            return List.of(send(session.chatId, "Напишите имя, чтобы менеджер понимал, как обращаться.", null));
        }
        session.name = name;
        session.step = Step.PHONE;
        return List.of(send(session.chatId, "Телефон для связи. Можно в любом привычном формате.", null));
    }

    private List<Out> onPhone(Session session, String text) {
        String phone = text.strip();
        int digits = 0;
        for (int i = 0; i < phone.length(); i++) {
            if (Character.isDigit(phone.charAt(i))) {
                digits++;
            }
        }
        if (digits < 10 || digits > 15) {
            return List.of(send(session.chatId, "Нужен телефон: не меньше 10 цифр.", null));
        }
        session.phone = phone;
        session.card = session.estimateText + "\n\nКонтакт: " + session.name + "\nТелефон: " + phone;
        session.step = Step.CONFIRM;
        List<Out> outs = new ArrayList<>();
        for (String part : Texts.pre(session.card)) {
            outs.add(send(session.chatId, part, null));
        }
        outs.add(send(session.chatId, "Проверьте заявку и отправьте её менеджеру.", Keyboard.of(
                List.of(new Keyboard.Button("Отправить заявку", "ok:1")),
                List.of(new Keyboard.Button("Начать заново", "ok:0")))));
        return outs;
    }

    private List<Out> onConfirm(Session session, InCallback query) {
        if (!query.data().startsWith("ok:")) {
            return List.of();
        }
        if (query.data().endsWith(":0")) {
            return join(toast(query, null), start(query.chatId(), query.userId(), query.username()));
        }
        if (session.submitted) {
            return List.of(toast(query, "Заявка уже отправлена"));
        }
        session.submitted = true;
        try {
            leads.append(session.lead());
        } catch (RuntimeException ex) {
            session.submitted = false;
            return List.of(toast(query, "Не удалось записать заявку, попробуйте ещё раз"));
        }
        List<Out> outs = new ArrayList<>();
        if (adminChatId != null && session.card != null) {
            for (String part : Texts.pre(session.card)) {
                outs.add(new Out.Send(adminChatId, part, null, true));
            }
        }
        outs.add(toast(query, "Заявка сохранена"));
        outs.add(clear(query));
        outs.add(send(session.chatId, SUCCESS, null));
        sessions.remove(session.chatId);
        return outs;
    }

    private Session session(long chatId, long userId, String username) {
        Session session = sessions.computeIfAbsent(chatId, Session::new);
        session.remember(userId, username);
        return session;
    }

    private Object lock(long chatId) {
        return locks.computeIfAbsent(chatId, id -> new Object());
    }

    private static Out.Send send(long chatId, String html, Keyboard keyboard) {
        return new Out.Send(chatId, html, keyboard, false);
    }

    private static Out.Toast toast(InCallback query, String text) {
        return new Out.Toast(query.callbackId(), text);
    }

    private static Out.Markup clear(InCallback query) {
        return new Out.Markup(query.chatId(), query.messageId(), Keyboard.CLEAR);
    }

    private static List<Out> join(Out first, List<Out> rest) {
        List<Out> outs = new ArrayList<>();
        outs.add(first);
        outs.addAll(rest);
        return outs;
    }

    private Keyboard worksKeyboard(List<String> selected) {
        List<List<Keyboard.Button>> rows = new ArrayList<>();
        for (PriceCatalog.Work work : catalogs.forClient(null).works()) {
            String mark = selected.contains(work.id()) ? "✓ " : "";
            rows.add(List.of(new Keyboard.Button(mark + work.title(), "wk:" + work.id())));
        }
        rows.add(List.of(new Keyboard.Button("Дальше", "wk:done")));
        return Keyboard.from(rows);
    }

    private Keyboard complexKeyboard(List<String> selected) {
        List<List<Keyboard.Button>> rows = new ArrayList<>();
        for (var entry : catalogs.forClient(null).complex().entrySet()) {
            String mark = selected.contains(entry.getKey()) ? "✓ " : "";
            rows.add(List.of(new Keyboard.Button(mark + entry.getValue(), "cx:" + entry.getKey())));
        }
        rows.add(List.of(new Keyboard.Button("Дальше", "cx:done")));
        return Keyboard.from(rows);
    }

    private static Keyboard sectionKeyboard() {
        return Keyboard.of(
                List.of(new Keyboard.Button("Отделка", "sec:finish")),
                List.of(new Keyboard.Button("Инженерия", "sec:eng")),
                List.of(new Keyboard.Button("Отделка и инженерия", "sec:both")),
                List.of(new Keyboard.Button("Сложная система", "sec:complex")));
    }

    private static Keyboard choiceKeyboard(String prefix, Map<String, String> options, int width) {
        List<List<Keyboard.Button>> rows = new ArrayList<>();
        List<Keyboard.Button> row = new ArrayList<>();
        for (var entry : options.entrySet()) {
            row.add(new Keyboard.Button(entry.getValue(), prefix + entry.getKey()));
            if (row.size() == width) {
                rows.add(List.copyOf(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            rows.add(List.copyOf(row));
        }
        return Keyboard.from(rows);
    }

    private static Keyboard qtyKeyboard(String prefix) {
        return Keyboard.of(
                List.of(button(prefix, 0), button(prefix, 4), button(prefix, 8)),
                List.of(button(prefix, 12), button(prefix, 20), new Keyboard.Button("Своё число", prefix + "x")));
    }

    private static Keyboard.Button button(String prefix, int number) {
        return new Keyboard.Button(Integer.toString(number), prefix + number);
    }

    private static void putCount(Session session, String field, int count) {
        switch (field) {
            case "electro" -> session.electro = count;
            case "water" -> session.water = count;
            case "cameras" -> session.cameras = count;
            case "lan" -> session.lan = count;
            default -> throw new IllegalArgumentException(field);
        }
    }

    public static Double parseArea(String text) {
        String cleaned = text.replace(" ", "").replace(",", ".");
        try {
            double value = Double.parseDouble(cleaned);
            if (!Double.isFinite(value) || value <= 0 || value > 50_000) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer parseCount(String text) {
        String cleaned = text.strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        for (int i = 0; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (ch < '0' || ch > '9') {
                return null;
            }
        }
        try {
            int value = Integer.parseInt(cleaned);
            if (value < 0 || value > 5_000) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String command(String text) {
        if (text == null || !text.startsWith("/")) {
            return null;
        }
        String first = text.split("\\s+", 2)[0];
        int at = first.indexOf('@');
        if (at >= 0) {
            first = first.substring(0, at);
        }
        if (first.length() < 2) {
            return null;
        }
        String name = first.substring(1).toLowerCase(Locale.ROOT);
        return switch (name) {
            case "start", "cancel", "help", "myid" -> name;
            default -> null;
        };
    }

    private static String value(String data) {
        int colon = data.indexOf(':');
        return colon < 0 ? data : data.substring(colon + 1);
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }

    private enum Step {
        NONE, SECTION, OBJECT, CONDITION, CITY, GRADE, AREA, FINISH_MODE, WORKS,
        ELECTRO, ELECTRO_N, WATER, WATER_N, CAMERAS, CAMERAS_N, LAN, LAN_N,
        CHASE, HEIGHT, COMPLEX_PICK, COMPLEX_TEXT, NAME, PHONE, CONFIRM
    }

    private static final class Session {
        final long chatId;
        final List<Out> pending = new ArrayList<>();
        Step step = Step.NONE;
        long userId;
        String username;
        String clientId;
        String section;
        String objectType;
        String condition;
        String city;
        String grade;
        Double floorArea;
        String finishMode;
        List<String> works;
        Integer electro;
        Integer water;
        Integer cameras;
        Integer lan;
        Boolean chase;
        Double heightM;
        List<String> complexItems;
        String complexNote;
        String estimateText;
        String name;
        String phone;
        String card;
        boolean submitted;

        Session(long chatId) {
            this.chatId = chatId;
        }

        void remember(long userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        Estimator.Job job() {
            return new Estimator.Job(
                    section, objectType, condition, city, grade, floorArea, finishMode,
                    works == null ? null : List.copyOf(works),
                    electro, water, cameras, lan, chase, heightM,
                    complexItems == null ? null : List.copyOf(complexItems),
                    complexNote);
        }

        Map<String, Object> lead() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tg_id", userId);
            row.put("tg_username", username);
            row.put("name", name);
            row.put("phone", phone);
            row.put("section", section);
            row.put("object_type", objectType);
            row.put("condition", condition);
            row.put("city", city);
            row.put("grade", grade);
            row.put("floor_area", floorArea);
            row.put("finish_mode", finishMode);
            row.put("works", works);
            row.put("electro", electro);
            row.put("water", water);
            row.put("cameras", cameras);
            row.put("lan", lan);
            row.put("chase", chase);
            row.put("height_m", heightM);
            row.put("complex_items", complexItems);
            row.put("complex_note", complexNote);
            row.put("card", card);
            return row;
        }
    }
}
