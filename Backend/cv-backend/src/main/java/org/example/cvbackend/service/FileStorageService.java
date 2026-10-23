package org.example.cvbackend.service;

import org.example.cvbackend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Saves uploaded photos to disk under random names. The client's file name is never used. */
@Service
public class FileStorageService {

    public record Stored(String filename, String sha256) {}

    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final Map<String, String> CONTENT_TYPES =
            Map.of("jpg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final Path root;

    public FileStorageService(@Value("${app.upload.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload folder " + root, e);
        }
    }

    public Stored store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add a photo of the item.");
        }
        String type = file.getContentType() == null ? null : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = type == null ? null : EXTENSIONS.get(type);
        if (ext == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use a JPG, PNG or WebP photo.");
        }
        try {
            byte[] bytes = file.getBytes();
            // The content type comes from the client, so check the file's real signature too.
            if (!matchesSignature(ext, bytes)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "That file isn't a valid image.");
            }
            String name = UUID.randomUUID() + "." + ext;
            Files.write(root.resolve(name), bytes);
            return new Stored(name, sha256(bytes));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't save the photo. Please try again.");
        }
    }

    public byte[] load(String filename) {
        Path p = root.resolve(filename).normalize();
        if (!p.startsWith(root)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found.");
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found.");
        }
    }

    public String contentTypeOf(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    public void delete(String filename) {
        try {
            Files.deleteIfExists(root.resolve(filename).normalize());
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private static boolean matchesSignature(String ext, byte[] b) {
        return switch (ext) {
            case "jpg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "webp" -> b.length > 12
                    && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}