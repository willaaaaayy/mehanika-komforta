package ru.mehanikakomforta.clients;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.leads.ApiError;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
class ClientFieldController {

    private final ClientFields fields;
    private final ClientRepository clients;
    private final CrmProperties properties;

    ClientFieldController(ClientFields fields, ClientRepository clients, CrmProperties properties) {
        this.fields = fields;
        this.clients = clients;
        this.properties = properties;
    }

    @GetMapping("/api/client-layout")
    ResponseEntity<?> layout(@RequestParam(required = false) String type) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canReadClients()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        return ResponseEntity.ok(fields.layout(type));
    }

    @PostMapping("/api/client-fields")
    ResponseEntity<?> create(@RequestBody(required = false) ClientFields.FieldBody body) {
        if (!ClientAccess.signedIn()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (body == null || body.title() == null || body.title().isBlank() || body.key() == null || body.key().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("field"));
        }
        ClientFields.FieldView created = fields.create(body);
        if (created == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("duplicate"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/api/client-fields/{id:\\d+}")
    ResponseEntity<?> change(@PathVariable long id, @RequestBody(required = false) ClientFields.FieldBody body) {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(new ApiError("field"));
        }
        ClientFields.FieldView saved = fields.change(id, body);
        if (saved == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/api/client-fields/{id:\\d+}")
    ResponseEntity<?> remove(@PathVariable long id) {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        String result = fields.remove(id);
        if ("missing".equals(result)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        if ("system".equals(result)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("system"));
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/client-fields/order")
    ResponseEntity<?> order(@RequestBody(required = false) OrderBody body) {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (body == null || body.type() == null) {
            return ResponseEntity.badRequest().body(new ApiError("type"));
        }
        fields.order(body.type(), body.blocks(), body.fields());
        return ResponseEntity.ok(fields.layout(body.type()));
    }

    @PostMapping(value = "/api/clients/{id}/files", consumes = "multipart/form-data")
    ResponseEntity<?> file(
            @PathVariable long id,
            @RequestParam(required = false) String key,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws java.io.IOException {
        if (!ClientAccess.canConvert()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("role"));
        }
        if (clients.findById(id).isEmpty() || key == null || key.isBlank() || file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("file"));
        }
        String name = file.getOriginalFilename() == null ? "file" : Path.of(file.getOriginalFilename()).getFileName().toString();
        Path root = Path.of(properties.contractDir());
        Path parent = root.getParent() == null ? root : root.getParent();
        Path dir = parent.resolve("client-files").resolve(String.valueOf(id));
        Files.createDirectories(dir);
        Files.write(dir.resolve(key), file.getBytes());
        fields.saveValues(id, Map.of(key, name));
        return ResponseEntity.noContent().build();
    }

    record OrderBody(String type, java.util.List<ClientFields.BlockView> blocks, java.util.List<ClientFields.FieldView> fields) {
    }
}
