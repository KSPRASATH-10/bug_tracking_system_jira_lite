package com.enterprise.bts.service;

import com.enterprise.bts.config.BtsProperties;
import com.enterprise.bts.exception.FileRejectedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

/**
 * Upload gatekeeper. The file's real type comes from its leading bytes (Apache Tika magic detection),
 * never from the client-supplied name or Content-Type; those are only cross-checked against it.
 * Rejected: empty/oversized files, dangerous or double extensions, hidden files, types outside the
 * allow-list (images, PDF, plain text), and any mismatch between content, extension and declared MIME.
 */
@Service
public class FileValidationService {

    /** Result of a successful validation; filename is already path-stripped and safe to store and display. */
    public record ValidatedUpload(String filename, String contentType, byte[] content, String sha256) {
    }

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "sh", "bash", "zsh", "csh", "ksh", "exe", "dll", "bat", "cmd", "com", "msi", "scr", "lnk", "hta",
            "jsp", "jspx", "php", "phtml", "asp", "aspx", "cgi", "html", "htm", "xhtml", "svg", "xml",
            "js", "mjs", "jar", "war", "class", "py", "pl", "rb", "ps1", "psm1", "vbs", "wsf", "apk", "dmg");

    /** Detected MIME type -> extensions allowed for it. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "image/png", Set.of("png"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/gif", Set.of("gif"),
            "image/webp", Set.of("webp"),
            "application/pdf", Set.of("pdf"),
            "text/plain", Set.of("txt", "log", "csv", "json", "md"));

    /** Browsers label plain-text-family files in several ways; only these are accepted for text/plain content. */
    private static final Set<String> TEXT_DECLARED_TYPES = Set.of(
            "text/plain", "text/csv", "application/json", "text/markdown", "text/x-log", "application/octet-stream");

    private static final Map<String, String> TEXT_CANONICAL_TYPES = Map.of(
            "csv", "text/csv", "json", "application/json", "md", "text/markdown");

    private final Tika tika = new Tika();
    private final long maxBytes;

    public FileValidationService(BtsProperties properties) {
        this.maxBytes = properties.storage().maxFileBytes();
    }

    public ValidatedUpload validate(String originalFilename, String declaredContentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new FileRejectedException("The file is empty");
        }
        if (content.length > maxBytes) {
            throw new FileRejectedException("The file exceeds the maximum size of " + (maxBytes / 1_048_576) + " MB");
        }

        String filename = sanitizeFilename(originalFilename);
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.startsWith(".")) {
            throw new FileRejectedException("Hidden files are not allowed");
        }
        String[] parts = lower.split("\\.", -1);
        if (parts.length < 2 || parts[parts.length - 1].isEmpty()) {
            throw new FileRejectedException("The file must have an allowed extension");
        }
        // Every extension segment is checked, so "shell.sh.png" and "page.html.txt" are rejected too.
        for (int i = 1; i < parts.length; i++) {
            if (BLOCKED_EXTENSIONS.contains(parts[i])) {
                throw new FileRejectedException("This file type is not allowed");
            }
        }
        String extension = parts[parts.length - 1];

        String detected = tika.detect(content);                       // magic bytes only: no filename hint
        Set<String> extensionsForType = ALLOWED.get(detected);
        if (extensionsForType == null) {
            throw new FileRejectedException("This file type is not allowed");
        }
        if (!extensionsForType.contains(extension)) {
            throw new FileRejectedException("The file extension does not match the file content");
        }
        String declared = normalise(declaredContentType);
        boolean declaredMatches = "text/plain".equals(detected)
                ? TEXT_DECLARED_TYPES.contains(declared)
                : detected.equals(declared);
        if (!declaredMatches) {
            throw new FileRejectedException("The declared content type does not match the file content");
        }

        String storedType = "text/plain".equals(detected)
                ? TEXT_CANONICAL_TYPES.getOrDefault(extension, "text/plain")
                : detected;
        return new ValidatedUpload(filename, storedType, content, sha256(content));
    }

    /** Removes any path, control/bidi characters and header-hostile characters; caps the length at 255. */
    static String sanitizeFilename(String original) {
        if (original == null) {
            throw new FileRejectedException("The file name is missing");
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cc}\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]", "");
        name = name.replaceAll("[<>\"|?*]", "_").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new FileRejectedException("The file name is invalid");
        }
        if (name.length() > 255) {
            int dot = name.lastIndexOf('.');
            String extension = (dot > 0 && name.length() - dot <= 20) ? name.substring(dot) : "";
            name = name.substring(0, 255 - extension.length()) + extension;
        }
        return name;
    }

    private static String normalise(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return base.strip().toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Kept for symmetry with storage code that needs a charset-safe name. */
    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
