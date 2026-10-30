package com.enterprise.bts.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Defence-in-depth sanitiser for user text. Rendering contract: the SPA renders Markdown with raw HTML
 * DISABLED and an http/https/mailto link allow-list; this class guarantees the stored text carries
 * no live HTML or script-scheme links even if some other client renders it less carefully.
 *
 * Markdown-aware rules (code is left byte-for-byte so snippets like List&lt;String&gt; survive):
 *  1. Normalise newlines; drop control characters and bidi-override / zero-width spoofing characters.
 *  2. Fenced code blocks and inline code spans are preserved verbatim.
 *  3. In prose, any '<' that could open an HTML tag, comment, PI or autolink is rewritten as the
 *     entity "&lt;" (CommonMark renders it as a literal '<').
 *  4. Link/image/reference destinations are allow-listed (http, https, mailto, relative); anything
 *     scheme-like (javascript:, data:, vbscript:, entity-obfuscated variants) becomes "#blocked".
 */
@Component
public class MarkdownSanitizer {

    private static final String BLOCKED_DESTINATION = "#blocked";

    /** Control chars except \n and \t, plus bidi overrides/isolates, ZWSP, LRM/RLM and BOM. ZWJ/ZWNJ are kept (emoji). */
    private static final Pattern UNSAFE_CHARS = Pattern.compile(
            "[\\p{Cc}&&[^\\n\\t]]|[\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[\\s\\u00A0]+");
    private static final Pattern FENCE_OPEN = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern INLINE_DESTINATION = Pattern.compile("(\\]\\(\\s*)(<[^>\\n]*>|[^\\s)]*)");
    private static final Pattern REFERENCE_DEFINITION =
            Pattern.compile("(?m)^( {0,3}\\[[^\\]\\n]+\\]:\\s*)(<[^>\\n]*>|\\S+)");

    /** Required single-line plain text (titles, names): control characters removed, whitespace collapsed. */
    public String sanitizePlainText(String raw, int maxLength, String label) {
        String text = raw == null ? "" : raw.replaceAll("[\\r\\n\\t]+", " ");
        text = UNSAFE_CHARS.matcher(text).replaceAll("");
        text = WHITESPACE_RUN.matcher(text).replaceAll(" ").strip();
        if (text.isEmpty()) {
            throw badRequest(label + " must not be blank");
        }
        if (text.length() > maxLength) {
            throw badRequest(label + " must be at most " + maxLength + " characters");
        }
        return text;
    }

    /** Markdown with a post-sanitisation length cap (sanitising can lengthen text: '<' becomes '&lt;'). */
    public String sanitizeMarkdown(String raw, int maxLength, String label) {
        String result = sanitizeMarkdown(raw);
        if (result.length() > maxLength) {
            throw badRequest(label + " must be at most " + maxLength + " characters");
        }
        return result;
    }

    public String sanitizeMarkdown(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = UNSAFE_CHARS.matcher(text).replaceAll("");

        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 32);
        char fenceChar = 0;
        int fenceLength = 0;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (fenceChar != 0) {
                out.append(line);                                   // inside a fenced block: verbatim
                if (isClosingFence(line, fenceChar, fenceLength)) {
                    fenceChar = 0;
                }
            } else {
                Matcher open = FENCE_OPEN.matcher(line);
                if (open.matches() && isValidOpeningFence(open)) {
                    fenceChar = open.group(1).charAt(0);
                    fenceLength = open.group(1).length();
                    out.append(line);
                } else {
                    out.append(sanitizeLine(line));
                }
            }
            if (index < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString().strip();
    }

    private static boolean isValidOpeningFence(Matcher open) {
        // A backtick fence's info string may not contain a backtick (otherwise it is an inline code span).
        return open.group(1).charAt(0) != '`' || open.group(2).indexOf('`') < 0;
    }

    private static boolean isClosingFence(String line, char fenceChar, int fenceLength) {
        int i = 0;
        while (i < line.length() && i < 3 && line.charAt(i) == ' ') {
            i++;
        }
        int start = i;
        while (i < line.length() && line.charAt(i) == fenceChar) {
            i++;
        }
        if (i - start < fenceLength) {
            return false;
        }
        return line.substring(i).isBlank();
    }

    /** Splits a line into inline code spans (kept verbatim) and prose (sanitised). */
    private static String sanitizeLine(String line) {
        StringBuilder out = new StringBuilder(line.length() + 16);
        StringBuilder prose = new StringBuilder();
        int length = line.length();
        int i = 0;
        while (i < length) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < length) {                      // backslash escape: keep the pair together
                prose.append(c).append(line.charAt(i + 1));
                i += 2;
            } else if (c == '`') {
                int run = runLength(line, i);
                int close = findClosingRun(line, i + run, run);
                if (close >= 0) {
                    out.append(sanitizeProse(prose.toString()));
                    prose.setLength(0);
                    out.append(line, i, close + run);               // inline code span: verbatim
                    i = close + run;
                } else {
                    prose.append(line, i, i + run);                 // unmatched backticks are literal prose
                    i += run;
                }
            } else {
                prose.append(c);
                i++;
            }
        }
        out.append(sanitizeProse(prose.toString()));
        return out.toString();
    }

    private static int runLength(String s, int from) {
        int end = from;
        while (end < s.length() && s.charAt(end) == '`') {
            end++;
        }
        return end - from;
    }

    private static int findClosingRun(String s, int from, int run) {
        int j = from;
        while (j < s.length()) {
            if (s.charAt(j) == '`') {
                int len = runLength(s, j);
                if (len == run) {
                    return j;
                }
                j += len;
            } else {
                j++;
            }
        }
        return -1;
    }

    private static String sanitizeProse(String prose) {
        if (prose.isEmpty()) {
            return prose;
        }
        String text = replaceDestinations(REFERENCE_DEFINITION, replaceDestinations(INLINE_DESTINATION, prose));
        StringBuilder out = new StringBuilder(text.length() + 8);
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = text.charAt(i + 1);
                if (next == '<') {
                    out.append("&lt;");                              // "\<" renders as '<': keep that, drop the raw char
                } else {
                    out.append(c).append(next);
                }
                i++;
            } else if (c == '<' && i + 1 < n && startsMarkup(text.charAt(i + 1))) {
                out.append("&lt;");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean startsMarkup(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '/' || c == '!' || c == '?';
    }

    private static String replaceDestinations(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(matcher.group(1) + safeDestination(matcher.group(2))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Allow-list: http(s)/mailto, or a relative reference whose first segment cannot be read as a scheme. */
    static String safeDestination(String destination) {
        boolean angle = destination.length() >= 2 && destination.startsWith("<") && destination.endsWith(">");
        String inner = angle ? destination.substring(1, destination.length() - 1) : destination;
        String probe = inner.replaceAll("[\\s\\p{Cc}]", "").toLowerCase(Locale.ROOT);
        if (probe.isEmpty()
                || probe.startsWith("http://") || probe.startsWith("https://") || probe.startsWith("mailto:")) {
            return destination;
        }
        int stop = indexOfAny(probe, '/', '?', '#');
        String head = stop < 0 ? probe : probe.substring(0, stop);
        if (head.indexOf(':') >= 0 || head.indexOf('&') >= 0 || head.indexOf('\\') >= 0) {
            return angle ? "<" + BLOCKED_DESTINATION + ">" : BLOCKED_DESTINATION;
        }
        return destination;
    }

    private static int indexOfAny(String s, char... chars) {
        for (int i = 0; i < s.length(); i++) {
            for (char c : chars) {
                if (s.charAt(i) == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
