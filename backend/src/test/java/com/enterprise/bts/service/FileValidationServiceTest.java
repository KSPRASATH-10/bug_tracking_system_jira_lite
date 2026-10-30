package com.enterprise.bts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.bts.config.BtsProperties;
import com.enterprise.bts.exception.FileRejectedException;
import com.enterprise.bts.service.FileValidationService.ValidatedUpload;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FileValidationServiceTest {

    private static final long LIMIT = 5L * 1024 * 1024;

    private final FileValidationService service = new FileValidationService(new BtsProperties(
            new BtsProperties.Security("unit-test-secret-unit-test-secret-0123", 30, true, "BTS_AUTH",
                    "http://localhost:5173"),
            new BtsProperties.RateLimit(5, 60),
            new BtsProperties.Storage("./var/uploads", LIMIT)));

    // ------------------------------------------------------------ fixtures (real magic bytes)

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] png() {
        return bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0, 0x90, 0x77, 0x53, 0xDE);
    }

    private static byte[] jpeg() {
        return bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01, 0x01, 0x00, 0x00, 0x01,
                0x00, 0x01, 0x00, 0x00, 0xFF, 0xD9);
    }

    private static byte[] gif() {
        return bytes('G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0, 0, 0x3B);
    }

    private static byte[] pdf() {
        return ascii("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n");
    }

    private static byte[] text() {
        return ascii("Steps to reproduce:\n1. Open the page\n2. Click Save\n");
    }

    private static byte[] elf() {
        return bytes(0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0x3E, 0);
    }

    private static byte[] zip() {
        return bytes('P', 'K', 3, 4, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    // ------------------------------------------------------------ accepted files

    static Stream<Arguments> acceptedFiles() {
        return Stream.of(
                Arguments.of("screenshot.png", "image/png", png(), "image/png"),
                Arguments.of("PHOTO.JPG", "image/jpeg", jpeg(), "image/jpeg"),
                Arguments.of("photo.jpeg", "IMAGE/JPEG", jpeg(), "image/jpeg"),
                Arguments.of("anim.gif", "image/gif", gif(), "image/gif"),
                Arguments.of("spec.pdf", "application/pdf", pdf(), "application/pdf"),
                Arguments.of("notes.txt", "text/plain", text(), "text/plain"),
                Arguments.of("notes.txt", "text/plain; charset=UTF-8", text(), "text/plain"),
                Arguments.of("debug.log", "application/octet-stream", text(), "text/plain"));
    }

    @ParameterizedTest(name = "accepts {0} ({1})")
    @MethodSource("acceptedFiles")
    void genuineFilesWithMatchingExtensionAndTypeAreAccepted(String filename, String declared, byte[] content,
                                                              String expectedType) {
        ValidatedUpload upload = service.validate(filename, declared, content);
        assertThat(upload.contentType()).isEqualTo(expectedType);
        assertThat(upload.filename()).isEqualTo(filename);
        assertThat(upload.sha256()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(upload.content()).isEqualTo(content);
    }

    @Test
    void sha256IsComputedOverTheStoredBytes() {
        ValidatedUpload upload = service.validate("abc.txt", "text/plain", ascii("abc"));
        assertThat(upload.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void aFileExactlyAtTheSizeLimitIsAccepted() {
        byte[] content = new byte[(int) LIMIT];
        System.arraycopy(png(), 0, content, 0, png().length);
        assertThat(service.validate("big.png", "image/png", content).content()).hasSize((int) LIMIT);
    }

    // ------------------------------------------------------------ rejected files

    static Stream<Arguments> rejectedFiles() {
        byte[] script = ascii("echo hi\n");
        return Stream.of(
                // size / emptiness
                Arguments.of("empty.txt", "text/plain", new byte[0], "empty"),
                Arguments.of("big.png", "image/png", oversizedPng(), "maximum size"),
                // names
                Arguments.of(".hidden.png", "image/png", png(), "Hidden files"),
                Arguments.of("README", "text/plain", text(), "allowed extension"),
                Arguments.of("trailingdot.", "text/plain", text(), "allowed extension"),
                // dangerous extensions: the brief names .sh .exe .jsp .html explicitly
                Arguments.of("run.sh", "text/plain", script, "not allowed"),
                Arguments.of("setup.exe", "application/octet-stream", script, "not allowed"),
                Arguments.of("shell.jsp", "text/plain", script, "not allowed"),
                Arguments.of("page.html", "text/html", script, "not allowed"),
                Arguments.of("page.HTM", "text/html", script, "not allowed"),
                Arguments.of("logo.svg", "image/svg+xml", script, "not allowed"),
                Arguments.of("payload.php", "text/plain", script, "not allowed"),
                Arguments.of("hook.js", "text/javascript", script, "not allowed"),
                Arguments.of("tool.bat", "text/plain", script, "not allowed"),
                // double extensions: every segment is inspected
                Arguments.of("shell.sh.png", "image/png", png(), "not allowed"),
                Arguments.of("page.html.txt", "text/plain", text(), "not allowed"),
                Arguments.of("archive.exe.pdf", "application/pdf", pdf(), "not allowed"),
                // content does not match the extension
                Arguments.of("photo.jpg", "image/jpeg", png(), "does not match the file content"),
                Arguments.of("pic.png", "image/png", text(), "does not match the file content"),
                Arguments.of("doc.pdf", "application/pdf", png(), "does not match the file content"),
                // declared MIME type does not match the content
                Arguments.of("shot.png", "text/html", png(), "declared content type"),
                Arguments.of("shot.png", "application/pdf", png(), "declared content type"),
                Arguments.of("shot.png", null, png(), "declared content type"),
                Arguments.of("notes.txt", "image/png", text(), "declared content type"),
                // disguised payloads are caught by magic bytes
                Arguments.of("tool.png", "image/png", elf(), "not allowed"),
                Arguments.of("doc.pdf", "application/pdf", zip(), "not allowed"),
                Arguments.of("notes.txt", "text/plain",
                        ascii("<!DOCTYPE html><html><body><script>alert(1)</script></body></html>"), "not allowed"));
    }

    private static byte[] oversizedPng() {
        byte[] content = new byte[(int) LIMIT + 1];
        System.arraycopy(png(), 0, content, 0, png().length);
        return content;
    }

    @ParameterizedTest(name = "rejects {0} ({1}) -> {3}")
    @MethodSource("rejectedFiles")
    void hostileOrMismatchedFilesAreRejected(String filename, String declared, byte[] content, String fragment) {
        assertThatThrownBy(() -> service.validate(filename, declared, content))
                .isInstanceOf(FileRejectedException.class)
                .hasMessageContaining(fragment);
    }

    // ------------------------------------------------------------ filename sanitisation

    @Test
    void pathComponentsAreStrippedFromFilenames() {
        assertThat(FileValidationService.sanitizeFilename("../../etc/passwd.png")).isEqualTo("passwd.png");
        assertThat(FileValidationService.sanitizeFilename("C:\\Users\\bob\\shot.png")).isEqualTo("shot.png");
        assertThat(service.validate("../../../tmp/evil.png", "image/png", png()).filename()).isEqualTo("evil.png");
    }

    @Test
    void controlBidiAndHeaderHostileCharactersAreRemoved() {
        assertThat(FileValidationService.sanitizeFilename("inv\u202Eoice.png")).isEqualTo("invoice.png");
        assertThat(FileValidationService.sanitizeFilename("a\r\nb.png")).isEqualTo("ab.png");
        assertThat(FileValidationService.sanitizeFilename("a\"b<c>.png")).isEqualTo("a_b_c_.png");
    }

    @Test
    void invalidNamesAreRejected() {
        assertThatThrownBy(() -> FileValidationService.sanitizeFilename(null)).isInstanceOf(FileRejectedException.class);
        assertThatThrownBy(() -> FileValidationService.sanitizeFilename("   ")).isInstanceOf(FileRejectedException.class);
        assertThatThrownBy(() -> FileValidationService.sanitizeFilename("..")).isInstanceOf(FileRejectedException.class);
        assertThatThrownBy(() -> FileValidationService.sanitizeFilename("folder/")).isInstanceOf(FileRejectedException.class);
    }

    @Test
    void overlongNamesAreTruncatedButKeepTheirExtension() {
        String name = FileValidationService.sanitizeFilename("a".repeat(300) + ".png");
        assertThat(name).hasSize(255).endsWith(".png");
    }
}
