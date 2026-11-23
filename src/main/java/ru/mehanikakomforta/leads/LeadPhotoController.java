package ru.mehanikakomforta.leads;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mehanikakomforta.CrmProperties;
import ru.mehanikakomforta.SecretMatch;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class LeadPhotoController {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_FILES = 10;

    private final LeadRepository leads;
    private final LeadPhotoRepository photos;
    private final LeadProfileRepository profiles;
    private final PhotoStore store;
    private final CrmProperties properties;

    public LeadPhotoController(
            LeadRepository leads,
            LeadPhotoRepository photos,
            LeadProfileRepository profiles,
            PhotoStore store,
            CrmProperties properties
    ) {
        this.leads = leads;
        this.photos = photos;
        this.profiles = profiles;
        this.store = store;
        this.properties = properties;
    }

    @PostMapping(value = "/api/leads/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @PathVariable long id,
            @RequestHeader(value = "X-Lead-Token", required = false) String token,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws IOException {
        if (!SecretMatch.same(properties.leadToken(), token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("token"));
        }
        if (leads.findById(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        if (!acceptable(file) || photos.countByLeadId(id) >= MAX_FILES) {
            return ResponseEntity.badRequest().body(new ApiError("photo"));
        }
        byte[] body = file.getBytes();
        String contentType = type(file.getContentType());
        if (!matches(contentType, file.getOriginalFilename(), body)) {
            return ResponseEntity.badRequest().body(new ApiError("photo"));
        }
        LeadPhotoEntity saved = photos.save(new LeadPhotoEntity(id, contentType, body.length, Instant.now()));
        store.write(id, saved.getId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
    }

    @PostMapping(value = "/api/leads/{id}/telegram-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> profile(
            @PathVariable long id,
            @RequestHeader(value = "X-Lead-Token", required = false) String token,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws IOException {
        if (!SecretMatch.same(properties.leadToken(), token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("token"));
        }
        if (leads.findById(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        if (!acceptable(file)) {
            return ResponseEntity.badRequest().body(new ApiError("photo"));
        }
        byte[] body = file.getBytes();
        String contentType = type(file.getContentType());
        String filename = file.getOriginalFilename();
        if (filename != null && (filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0)) {
            return ResponseEntity.badRequest().body(new ApiError("photo"));
        }
        if (!typed(contentType, body)) {
            return ResponseEntity.badRequest().body(new ApiError("photo"));
        }
        LeadProfileEntity saved = profiles.findByLeadId(id)
                .map(existing -> {
                    existing.replace(contentType, body.length, Instant.now());
                    return profiles.save(existing);
                })
                .orElseGet(() -> profiles.save(new LeadProfileEntity(id, contentType, body.length, Instant.now())));
        store.writeProfile(id, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
    }

    @GetMapping("/api/leads/{id}/telegram-photo")
    public ResponseEntity<?> openProfile(@PathVariable long id) {
        if (!owner()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        return profiles.findByLeadId(id)
                .map(photo -> {
                    try {
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .contentType(MediaType.parseMediaType(photo.getContentType()))
                                .body(store.readProfile(id));
                    } catch (IOException exception) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id")));
    }

    @GetMapping("/api/leads/{id}/photos")
    public ResponseEntity<?> list(@PathVariable long id) {
        if (!owner()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        if (leads.findById(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
        }
        List<Map<String, Long>> body = photos.findByLeadIdOrderByIdAsc(id).stream()
                .map(photo -> Map.of("id", photo.getId()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/api/leads/{id}/photos/{photoId}")
    public ResponseEntity<?> open(@PathVariable long id, @PathVariable long photoId) {
        if (!owner()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("auth"));
        }
        return photos.findById(photoId)
                .filter(photo -> photo.getLeadId().equals(id))
                .map(photo -> {
                    try {
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .contentType(MediaType.parseMediaType(photo.getContentType()))
                                .body(store.read(id, photoId));
                    } catch (IOException exception) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id"));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("id")));
    }

    private static boolean acceptable(MultipartFile file) {
        return file != null && !file.isEmpty() && file.getSize() <= MAX_BYTES;
    }

    private static boolean matches(String contentType, String filename, byte[] body) {
        if (contentType == null || filename == null || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> "photo.jpg".equals(filename) && jpeg(body);
            case "image/png" -> "photo.png".equals(filename) && png(body);
            case "image/webp" -> "photo.webp".equals(filename) && webp(body);
            default -> false;
        };
    }

    private static boolean typed(String contentType, byte[] body) {
        return switch (contentType) {
            case "image/jpeg" -> jpeg(body);
            case "image/png" -> png(body);
            case "image/webp" -> webp(body);
            default -> false;
        };
    }

    private static String type(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase();
    }

    private static boolean jpeg(byte[] body) {
        return body.length >= 3 && (body[0] & 0xff) == 0xff && (body[1] & 0xff) == 0xd8 && (body[2] & 0xff) == 0xff;
    }

    private static boolean png(byte[] body) {
        return body.length >= 4 && (body[0] & 0xff) == 0x89 && body[1] == 0x50 && body[2] == 0x4e && body[3] == 0x47;
    }

    private static boolean webp(byte[] body) {
        return body.length >= 12
                && body[0] == 'R' && body[1] == 'I' && body[2] == 'F' && body[3] == 'F'
                && body[8] == 'W' && body[9] == 'E' && body[10] == 'B' && body[11] == 'P';
    }

    private static boolean owner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
