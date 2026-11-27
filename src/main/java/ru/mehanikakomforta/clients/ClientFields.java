package ru.mehanikakomforta.clients;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Order(0)
public class ClientFields implements ApplicationRunner {

    static final List<String> TYPES = List.of("person", "sole", "company");
    static final List<String> KINDS = List.of(
            "text", "number", "money", "date", "select", "multiselect", "checkbox", "phone", "email", "file", "link");
    private static final List<String> BLOCKS = List.of("contacts", "requisites", "contracts", "objects", "finance", "lead");
    private static final Map<String, String> BLOCK_TITLES = Map.of(
            "contacts", "Контакты",
            "requisites", "Реквизиты",
            "contracts", "Договоры",
            "objects", "Объекты",
            "finance", "Финансы",
            "lead", "Исходная заявка"
    );
    private static final Set<String> COLUMNS = Set.of("name", "phone", "email", "inn", "address");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ClientFieldRepository fields;
    private final ClientFieldValueRepository values;
    private final ClientLayoutRepository layouts;

    public ClientFields(ClientFieldRepository fields, ClientFieldValueRepository values, ClientLayoutRepository layouts) {
        this.fields = fields;
        this.values = values;
        this.layouts = layouts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (fields.count() > 0) {
            return;
        }
        ClientFieldEntity name = fields.save(system("name", "Имя", "text", "client.name"));
        ClientFieldEntity phone = fields.save(system("phone", "Телефон", "phone", null));
        ClientFieldEntity email = fields.save(custom("email", "Почта", "email"));
        ClientFieldEntity inn = fields.save(system("inn", "ИНН", "text", "client.inn"));
        ClientFieldEntity address = custom("address", "Адрес", "text");
        address.update("Адрес", "text", null, null, false, null, "client.address", false, false, null);
        address = fields.save(address);
        List<Slot> slots = List.of(
                new Slot(name.getId(), "contacts", 0),
                new Slot(phone.getId(), "contacts", 1),
                new Slot(email.getId(), "contacts", 2),
                new Slot(inn.getId(), "requisites", 0),
                new Slot(address.getId(), "requisites", 1)
        );
        for (String type : TYPES) {
            layouts.save(new ClientLayoutEntity(type, write(new Layout(defaultBlocks(), slots))));
        }
    }

    public Map<String, Object> valuesOf(Long clientId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ClientFieldValueEntity row : values.findByClientId(clientId)) {
            result.put(row.getFieldKey(), readValue(row.getStored()));
        }
        return result;
    }

    @Transactional
    public void saveValues(Long clientId, Map<String, Object> incoming) {
        for (var entry : incoming.entrySet()) {
            if (entry.getKey() == null || COLUMNS.contains(entry.getKey())) {
                continue;
            }
            String stored = writeValue(entry.getValue());
            var key = new ClientFieldValueEntity.Key();
            key.setClientId(clientId);
            key.setFieldKey(entry.getKey());
            ClientFieldValueEntity row = values.findById(key).orElseGet(() -> new ClientFieldValueEntity(clientId, entry.getKey(), stored));
            row.stored(stored);
            values.save(row);
        }
    }

    @Transactional
    public void rememberContract(Long clientId, Map<String, String> byContractKey) {
        for (ClientFieldEntity field : fields.findAll()) {
            if (field.getContractKey() == null || COLUMNS.contains(field.getFieldKey())) {
                continue;
            }
            String incoming = byContractKey.get(field.getContractKey());
            if (incoming == null || incoming.isBlank()) {
                continue;
            }
            var key = new ClientFieldValueEntity.Key();
            key.setClientId(clientId);
            key.setFieldKey(field.getFieldKey());
            if (values.findById(key).isPresent()) {
                continue;
            }
            values.save(new ClientFieldValueEntity(clientId, field.getFieldKey(), writeValue(incoming)));
        }
    }

    public LayoutView layout(String type) {
        Layout stored = read(layouts.findById(type).orElseThrow().getDocument());
        List<BlockView> blocks = new ArrayList<>();
        for (Block block : stored.blocks()) {
            blocks.add(new BlockView(block.key(), BLOCK_TITLES.getOrDefault(block.key(), block.key()), block.enabled(), block.order()));
        }
        List<FieldView> body = new ArrayList<>();
        for (Slot slot : stored.fields()) {
            fields.findById(slot.id()).ifPresent(field -> body.add(view(field, slot.group(), slot.order())));
        }
        return new LayoutView(type, blocks, body);
    }

    @Transactional
    public FieldView create(FieldBody body) {
        String type = type(body.type());
        String key = key(body.key());
        String kind = kind(body.fieldType());
        ClientFieldEntity field = fields.findByFieldKey(key).orElse(null);
        if (field != null && !field.isDeleted()) {
            return null;
        }
        if (field == null) {
            field = new ClientFieldEntity(key, title(body.title()), kind, false);
        }
        field.update(title(body.title()), kind, blank(body.hint()), blank(body.defaultValue()), Boolean.TRUE.equals(body.required()), roles(body.roles()), blank(body.contractKey()), false, false, blank(body.options()));
        field = fields.save(field);
        Layout layout = read(layouts.findById(type).orElseThrow().getDocument());
        long id = field.getId();
        layout.fields().removeIf(slot -> slot.id() == id);
        int order = layout.fields().stream().mapToInt(Slot::order).max().orElse(-1) + 1;
        List<Slot> slots = new ArrayList<>(layout.fields());
        slots.add(new Slot(id, blank(body.group()) == null ? "contacts" : body.group().trim(), order));
        layouts.findById(type).orElseThrow().document(write(new Layout(layout.blocks(), slots)));
        return view(field, blank(body.group()) == null ? "contacts" : body.group().trim(), order);
    }

    @Transactional
    public FieldView change(long id, FieldBody body) {
        ClientFieldEntity field = fields.findById(id).orElse(null);
        if (field == null) {
            return null;
        }
        if (field.isSystemField() && Boolean.TRUE.equals(body.deleted())) {
            return view(field, groupOf(body.type(), field.getId()), orderOf(body.type(), field.getId()));
        }
        field.update(
                body.title() == null ? field.getTitle() : body.title(),
                body.fieldType() == null ? field.getFieldType() : kind(body.fieldType()),
                body.hint() == null ? field.getHint() : blank(body.hint()),
                body.defaultValue() == null ? field.getDefaultValue() : blank(body.defaultValue()),
                body.required() == null ? field.isRequired() : body.required(),
                body.roles() == null ? field.getRoles() : roles(body.roles()),
                body.contractKey() == null ? field.getContractKey() : blank(body.contractKey()),
                body.hidden() == null ? field.isHidden() : body.hidden(),
                field.isSystemField() ? false : (body.deleted() == null ? field.isDeleted() : body.deleted()),
                body.options() == null ? field.getOptions() : blank(body.options())
        );
        fields.save(field);
        String type = body.type() == null ? "person" : type(body.type());
        if (body.group() != null && !body.group().isBlank()) {
            moveGroup(type, field.getId(), body.group().trim());
        }
        return view(field, groupOf(type, field.getId()), orderOf(type, field.getId()));
    }

    @Transactional
    public String remove(long id) {
        ClientFieldEntity field = fields.findById(id).orElse(null);
        if (field == null) {
            return "missing";
        }
        if (field.isSystemField()) {
            return "system";
        }
        field.update(field.getTitle(), field.getFieldType(), field.getHint(), field.getDefaultValue(), field.isRequired(), field.getRoles(), field.getContractKey(), field.isHidden(), true, field.getOptions());
        fields.save(field);
        return "ok";
    }

    @Transactional
    public void order(String type, List<BlockView> blocks, List<FieldView> fieldViews) {
        String wanted = type(type);
        List<Block> storedBlocks = new ArrayList<>();
        if (blocks != null) {
            for (BlockView block : blocks) {
                if (BLOCKS.contains(block.key())) {
                    storedBlocks.add(new Block(block.key(), block.enabled(), block.order()));
                }
            }
        }
        if (storedBlocks.isEmpty()) {
            storedBlocks = defaultBlocks();
        }
        List<Slot> slots = new ArrayList<>();
        if (fieldViews != null) {
            for (FieldView field : fieldViews) {
                if (field.id() == null) {
                    continue;
                }
                slots.add(new Slot(field.id(), field.group() == null ? "contacts" : field.group(), field.order()));
            }
        }
        layouts.findById(wanted).orElseThrow().document(write(new Layout(storedBlocks, slots)));
    }

    private void moveGroup(String type, Long id, String group) {
        Layout layout = read(layouts.findById(type).orElseThrow().getDocument());
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : layout.fields()) {
            slots.add(slot.id() == id ? new Slot(id, group, slot.order()) : slot);
        }
        layouts.findById(type).orElseThrow().document(write(new Layout(layout.blocks(), slots)));
    }

    private String groupOf(String type, Long id) {
        if (type == null) {
            return "contacts";
        }
        Layout layout = read(layouts.findById(type(type)).orElseThrow().getDocument());
        for (Slot slot : layout.fields()) {
            if (slot.id() == id) {
                return slot.group();
            }
        }
        return "contacts";
    }

    private int orderOf(String type, Long id) {
        if (type == null) {
            return 0;
        }
        Layout layout = read(layouts.findById(type(type)).orElseThrow().getDocument());
        for (Slot slot : layout.fields()) {
            if (slot.id() == id) {
                return slot.order();
            }
        }
        return 0;
    }

    private FieldView view(ClientFieldEntity field, String group, int order) {
        return new FieldView(
                field.getId(),
                field.getFieldKey(),
                field.getTitle(),
                field.getFieldType(),
                group,
                order,
                field.isRequired(),
                field.getHint(),
                field.getDefaultValue(),
                split(field.getRoles()),
                field.isHidden(),
                field.isSystemField(),
                field.isDeleted(),
                field.getContractKey(),
                field.getOptions()
        );
    }

    private static List<Block> defaultBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < BLOCKS.size(); i++) {
            blocks.add(new Block(BLOCKS.get(i), true, i));
        }
        return blocks;
    }

    private static ClientFieldEntity system(String key, String title, String kind, String contractKey) {
        ClientFieldEntity field = new ClientFieldEntity(key, title, kind, true);
        field.update(title, kind, null, null, "name".equals(key), null, contractKey, false, false, null);
        return field;
    }

    private static ClientFieldEntity custom(String key, String title, String kind) {
        ClientFieldEntity field = new ClientFieldEntity(key, title, kind, false);
        field.update(title, kind, null, null, false, null, null, false, false, null);
        return field;
    }

    private static String type(String value) {
        if (value != null && TYPES.contains(value)) {
            return value;
        }
        return "person";
    }

    private static String kind(String value) {
        if (value != null && KINDS.contains(value)) {
            return value;
        }
        return "text";
    }

    private static String key(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.matches("[A-Za-z][A-Za-z0-9_]{0,39}")) {
            return "field";
        }
        return trimmed;
    }

    private static String title(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            return "Поле";
        }
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String roles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return String.join(",", roles);
    }

    private static List<String> split(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return List.of(roles.split(","));
    }

    private static String write(Layout layout) {
        return MAPPER.writeValueAsString(layout);
    }

    private static Layout read(String document) {
        return MAPPER.readValue(document, Layout.class);
    }

    private static String writeValue(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    private static Object readValue(String stored) {
        if (stored == null) {
            return null;
        }
        JsonNode node = MAPPER.readTree(stored);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isArray()) {
            List<String> items = new ArrayList<>();
            node.forEach(item -> items.add(item.asString()));
            return items;
        }
        return node.asString();
    }

    public record LayoutView(String type, List<BlockView> blocks, List<FieldView> fields) {
    }

    public record BlockView(String key, String title, boolean enabled, int order) {
    }

    public record FieldView(
            Long id,
            String key,
            String title,
            String fieldType,
            String group,
            int order,
            boolean required,
            String hint,
            String defaultValue,
            List<String> roles,
            boolean hidden,
            boolean system,
            boolean deleted,
            String contractKey,
            String options
    ) {
    }

    public record FieldBody(
            String type,
            String title,
            String key,
            String fieldType,
            String group,
            String hint,
            String defaultValue,
            String contractKey,
            Boolean required,
            Boolean hidden,
            Boolean deleted,
            List<String> roles,
            String options
    ) {
    }

    record Layout(List<Block> blocks, List<Slot> fields) {
    }

    record Block(String key, boolean enabled, int order) {
    }

    record Slot(long id, String group, int order) {
    }
}
