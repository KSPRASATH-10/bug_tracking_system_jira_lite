package com.enterprise.bts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class MarkdownSanitizerTest {

    private final MarkdownSanitizer sanitizer = new MarkdownSanitizer();

    // ------------------------------------------------------------ raw HTML / XSS vectors

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<svg/onload=alert(1)>",
            "<iframe src=javascript:alert(1)>",
            "<a href=\"javascript:alert(1)\">x</a>",
            "<body onload=alert(1)>",
            "<!-- hidden -->",
            "<?php echo 1 ?>",
            "</div>",
            "text <b>bold</b> text",
            "[<img src=x onerror=alert(1)>](https://example.com)",
            "> <script>quote</script>",
            "- item <style>*{display:none}</style>"})
    void noTagLikeSequenceSurvivesInProse(String hostile) {
        String result = sanitizer.sanitizeMarkdown(hostile);
        assertThat(result).doesNotContainPattern("<[A-Za-z/!?]");
    }

    @Test
    void scriptTagIsRewrittenAsAnEntityNotDeleted() {
        assertThat(sanitizer.sanitizeMarkdown("Hello <script>alert(1)</script> world"))
                .isEqualTo("Hello &lt;script>alert(1)&lt;/script> world");
    }

    @Test
    void backslashEscapedTagOpenerIsStillNeutralised() {
        assertThat(sanitizer.sanitizeMarkdown("\\<script>alert(1)")).isEqualTo("&lt;script>alert(1)");
    }

    @Test
    void harmlessAngleBracketsInProseAreLeftAlone() {
        assertThat(sanitizer.sanitizeMarkdown("a < b and 1<2 and <3 love")).isEqualTo("a < b and 1<2 and <3 love");
        assertThat(sanitizer.sanitizeMarkdown("> a block quote")).isEqualTo("> a block quote");
    }

    // ------------------------------------------------------------ link / image destinations

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "vbscript:msgbox(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "&#106;avascript:alert(1)",
            "&#x6A;avascript:alert(1)"})
    void dangerousLinkDestinationsAreBlocked(String destination) {
        String result = sanitizer.sanitizeMarkdown("[click](" + destination + ")");
        assertThat(result).contains("(#blocked").doesNotContainIgnoringCase("script:").doesNotContain("file:");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/path?q=1&r=2",
            "http://example.com",
            "mailto:ops@example.com",
            "/relative/path",
            "relative/path.md",
            "#section",
            "?page=2",
            "../up/one.png",
            "docs/a-b.md#intro"})
    void safeLinkDestinationsAreUntouched(String destination) {
        String input = "[label](" + destination + ")";
        assertThat(sanitizer.sanitizeMarkdown(input)).isEqualTo(input);
    }

    @Test
    void imagesAngleBracketDestinationsAndReferenceDefinitionsAreCovered() {
        assertThat(sanitizer.sanitizeMarkdown("![alt](javascript:alert(1))")).contains("(#blocked");
        assertThat(sanitizer.sanitizeMarkdown("[x](<javascript:alert(1)>)")).isEqualTo("[x](<#blocked>)");
        assertThat(sanitizer.sanitizeMarkdown("[ref]: javascript:alert(1)")).isEqualTo("[ref]: #blocked");
        assertThat(sanitizer.sanitizeMarkdown("[ref]: https://example.com")).isEqualTo("[ref]: https://example.com");
    }

    @Test
    void safeDestinationHelperHandlesEdgeCases() {
        assertThat(MarkdownSanitizer.safeDestination("")).isEmpty();
        assertThat(MarkdownSanitizer.safeDestination("https://a.example")).isEqualTo("https://a.example");
        assertThat(MarkdownSanitizer.safeDestination("javascript:void(0)")).isEqualTo("#blocked");
        assertThat(MarkdownSanitizer.safeDestination("<data:text/html,x>")).isEqualTo("<#blocked>");
    }

    // ------------------------------------------------------------ code is preserved verbatim

    @Test
    void inlineCodeSpansAreNotModified() {
        assertThat(sanitizer.sanitizeMarkdown("Use `<script>` carefully")).isEqualTo("Use `<script>` carefully");
        assertThat(sanitizer.sanitizeMarkdown("``a ` <b>``")).isEqualTo("``a ` <b>``");
    }

    @Test
    void proseAroundACodeSpanIsStillSanitised() {
        assertThat(sanitizer.sanitizeMarkdown("`ok` <b>bold</b>")).isEqualTo("`ok` &lt;b>bold&lt;/b>");
    }

    @Test
    void fencedCodeBlocksAreVerbatimAndSurroundingProseIsSanitised() {
        String input = "Intro <b>x</b>\n```html\n<b>kept</b>\n```\nOutro <i>y</i>";
        String expected = "Intro &lt;b>x&lt;/b>\n```html\n<b>kept</b>\n```\nOutro &lt;i>y&lt;/i>";
        assertThat(sanitizer.sanitizeMarkdown(input)).isEqualTo(expected);
    }

    @Test
    void tildeFencesLongFencesAndUnclosedFencesAreHandled() {
        assertThat(sanitizer.sanitizeMarkdown("~~~\n<b>kept</b>\n~~~")).isEqualTo("~~~\n<b>kept</b>\n~~~");
        assertThat(sanitizer.sanitizeMarkdown("````\n```\n<b>kept</b>\n````")).isEqualTo("````\n```\n<b>kept</b>\n````");
        // CommonMark: an unclosed fence runs to the end of the document, so it is still code (never HTML).
        assertThat(sanitizer.sanitizeMarkdown("```\n<script>x</script>")).isEqualTo("```\n<script>x</script>");
    }

    // ------------------------------------------------------------ characters and normalisation

    @Test
    void controlAndBidiOverrideCharactersAreRemoved() {
        assertThat(sanitizer.sanitizeMarkdown("a\u0000b\u202Ec\u200Bd\uFEFFe")).isEqualTo("abcde");
        assertThat(sanitizer.sanitizeMarkdown("evil\u202Etxt.exe")).isEqualTo("eviltxt.exe");
    }

    @Test
    void newlinesAreNormalisedAndUnicodeTextAndEmojiSequencesSurvive() {
        assertThat(sanitizer.sanitizeMarkdown("a\r\nb\rc")).isEqualTo("a\nb\nc");
        assertThat(sanitizer.sanitizeMarkdown("Größe café 日本語")).isEqualTo("Größe café 日本語");
        String family = "\uD83D\uDC68\u200D\uD83D\uDC69"; // man + ZWJ + woman: ZWJ must survive
        assertThat(sanitizer.sanitizeMarkdown(family)).isEqualTo(family);
    }

    @Test
    void nullBecomesEmptyAndOuterWhitespaceIsTrimmed() {
        assertThat(sanitizer.sanitizeMarkdown(null)).isEmpty();
        assertThat(sanitizer.sanitizeMarkdown("  hi  ")).isEqualTo("hi");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "[x](javascript:alert(1))",
            "\\<img src=x onerror=1>",
            "`code <b>` and <i>prose</i>",
            "```\n<b>\n```"})
    void sanitisingIsIdempotent(String input) {
        String once = sanitizer.sanitizeMarkdown(input);
        assertThat(sanitizer.sanitizeMarkdown(once)).isEqualTo(once);
    }

    // ------------------------------------------------------------ plain text and length rules

    @Test
    void plainTextCollapsesWhitespaceAndStripsControlCharacters() {
        assertThat(sanitizer.sanitizePlainText("  Hello\n\tWorld  ", 200, "Title")).isEqualTo("Hello World");
        assertThat(sanitizer.sanitizePlainText("Hello\u0000World", 200, "Title")).isEqualTo("HelloWorld");
        assertThat(sanitizer.sanitizePlainText("evil\u202Etxt.exe", 200, "Title")).isEqualTo("eviltxt.exe");
    }

    @Test
    void blankPlainTextIsRejectedWithBadRequest() {
        assertThatThrownBy(() -> sanitizer.sanitizePlainText("   \n ", 200, "Title"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getReason()).isEqualTo("Title must not be blank");
                });
        assertThatThrownBy(() -> sanitizer.sanitizePlainText("\u0000\u202E", 200, "Title"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sanitizer.sanitizePlainText(null, 200, "Title"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void overlongPlainTextIsRejected() {
        assertThat(sanitizer.sanitizePlainText("a".repeat(200), 200, "Title")).hasSize(200);
        assertThatThrownBy(() -> sanitizer.sanitizePlainText("a".repeat(201), 200, "Title"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getReason()).contains("at most 200"));
    }

    @Test
    void lengthLimitAppliesAfterSanitisationBecauseEntitiesAreLonger() {
        String raw = "<a".repeat(3000);                    // 6000 chars raw, 15000 once '<' becomes "&lt;"
        assertThat(raw.length()).isLessThanOrEqualTo(6000);
        assertThatThrownBy(() -> sanitizer.sanitizeMarkdown(raw, 5000, "Description"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo("Description must be at most 5000 characters"));
        assertThat(sanitizer.sanitizeMarkdown("fine", 5000, "Description")).isEqualTo("fine");
    }
}
