package pro.sketchware.activities.chat.port;

import java.util.ArrayList;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.util.CompileErrorCapture;
import pro.sketchware.util.ProjectPathResolver;

/**
 * Android diagnostic summary inspired by browser/_markerCheckService.ts.
 */
public final class VoidPortMarkerCheckService {
    private static final int MAX_ERRORS = 8;

    private VoidPortMarkerCheckService() {
    }

    public static class LintError {
        public final String code;
        public final String message;
        public final int startLineNumber;
        public final int endLineNumber;

        public LintError(String code, String message, int startLineNumber, int endLineNumber) {
            this.code = code != null ? code : "";
            this.message = message != null ? message : "";
            this.startLineNumber = startLineNumber;
            this.endLineNumber = endLineNumber;
        }
    }

    public static boolean hasErrors(String scId) {
        return CompileErrorCapture.hasCompileErrors(scId);
    }

    public static String buildErrorContext(String scId) {
        if (!hasErrors(scId)) {
            return "";
        }
        String raw = CompileErrorCapture.getLastCompileErrors(scId);
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }

        String summary = CompileErrorCapture.extractErrorSummary(raw).trim();
        if (summary.isEmpty()) {
            summary = raw.trim();
        }
        String compact = limitLines(summary, MAX_ERRORS);
        return "Recent diagnostics:\n" + compact;
    }

    public static List<String> topErrors(String scId) {
        String context = buildErrorContext(scId);
        List<String> errors = new ArrayList<>();
        if (context.isEmpty()) {
            return errors;
        }
        String[] lines = context.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && errors.size() < MAX_ERRORS) {
                errors.add(trimmed);
            }
        }
        return errors;
    }

    private static String limitLines(String text, int maxLines) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            builder.append(lines[i]).append('\n');
        }
        if (lines.length > maxLines) {
            builder.append("... diagnostics truncated ...\n");
        }
        return builder.toString().trim();
    }

    public static List<LintError> getLintErrors(String scId, String filePath) {
        List<LintError> errors = new ArrayList<>();
        
        String raw = CompileErrorCapture.getLastCompileErrors(scId);
        if (raw == null || raw.trim().isEmpty()) {
            return errors;
        }

        String requestedPath = normalizePath(filePath);
        ProjectPathResolver.ResolvedPath resolved = ProjectPathResolver.resolveForRead(scId, filePath == null ? "" : filePath);
        String absolutePath = resolved == null ? "" : normalizePath(resolved.getFile().getAbsolutePath());

        Pattern filePattern = Pattern.compile("(?m)^(.+?):(\\d+):\\s*(error|warning|erro|aviso):\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher fileMatcher = filePattern.matcher(raw);
        while (fileMatcher.find() && errors.size() < MAX_ERRORS) {
            String sourceFile = normalizePath(fileMatcher.group(1));
            if (!matchesRequestedFile(sourceFile, requestedPath, absolutePath)) {
                continue;
            }
            int lineNumber = parseInt(fileMatcher.group(2), 1);
            String severity = fileMatcher.group(3).toLowerCase();
            String message = fileMatcher.group(4).trim();
            errors.add(new LintError("warning".equals(severity) || "aviso".equals(severity)
                    ? "compile_warning"
                    : "compile_error", message, lineNumber, lineNumber));
        }

        if (!errors.isEmpty()) {
            return errors;
        }

        Pattern genericPattern = Pattern.compile("(?i)(?:error|warning|erro|aviso)\\s*:?\\s*([^\\n]+)");
        Matcher genericMatcher = genericPattern.matcher(raw);
        while (genericMatcher.find() && errors.size() < MAX_ERRORS) {
            String line = genericMatcher.group(0).trim();
            if (!requestedPath.isEmpty() && !normalizePath(line).contains(requestedPath)) {
                continue;
            }
            int lineNumber = parseLineNumber(line);
            errors.add(new LintError(line.toLowerCase().contains("warning") || line.toLowerCase().contains("aviso")
                    ? "compile_warning"
                    : "compile_error", genericMatcher.group(1).trim(), lineNumber, lineNumber));
        }

        return errors;
    }

    private static boolean matchesRequestedFile(String sourceFile, String requestedPath, String absolutePath) {
        if (requestedPath.isEmpty() && absolutePath.isEmpty()) {
            return true;
        }
        if (!absolutePath.isEmpty() && (sourceFile.equals(absolutePath) || sourceFile.endsWith("/" + absolutePath))) {
            return true;
        }
        if (!requestedPath.isEmpty()) {
            return sourceFile.equals(requestedPath)
                    || sourceFile.endsWith("/" + requestedPath)
                    || requestedPath.endsWith("/" + new File(sourceFile).getName());
        }
        return false;
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replace("\\", "/").replaceAll("/{2,}", "/");
    }

    private static int parseLineNumber(String text) {
        Matcher matcher = Pattern.compile(":(\\d+)(?::|\\s)").matcher(text == null ? "" : text);
        if (matcher.find()) {
            return parseInt(matcher.group(1), 1);
        }
        return 1;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
