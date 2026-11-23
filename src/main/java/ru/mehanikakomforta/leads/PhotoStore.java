package ru.mehanikakomforta.leads;

import org.springframework.stereotype.Component;
import ru.mehanikakomforta.CrmProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PhotoStore {

    private final Path root;

    public PhotoStore(CrmProperties properties) {
        this.root = Path.of(properties.photoDir());
    }

    public void write(long leadId, long photoId, byte[] body) throws IOException {
        Path file = path(leadId, photoId);
        Files.createDirectories(file.getParent());
        Files.write(file, body);
    }

    public byte[] read(long leadId, long photoId) throws IOException {
        return Files.readAllBytes(path(leadId, photoId));
    }

    public void writeProfile(long leadId, byte[] body) throws IOException {
        Path file = profile(leadId);
        Files.createDirectories(file.getParent());
        Files.write(file, body);
    }

    public byte[] readProfile(long leadId) throws IOException {
        return Files.readAllBytes(profile(leadId));
    }

    private Path profile(long leadId) {
        return root.resolve("profile").resolve(Long.toString(leadId));
    }

    private Path path(long leadId, long photoId) {
        return root.resolve(Long.toString(leadId)).resolve(Long.toString(photoId));
    }
}
