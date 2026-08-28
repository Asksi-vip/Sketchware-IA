package pro.sketchware.utility;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.InputSource;

import pro.sketchware.activities.chat.port.VoidPortMarkerCheckService;

/**
 * RealtimeDiagnosticEngine performs lightweight real-time code analysis for Java,
 * Kotlin, and XML files, merging real-time diagnostics with build output diagnostics.
 */
public class RealtimeDiagnosticEngine {

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public static class DiagnosticItem {
        public final String id;
        public final Severity severity;
        public final String message;
        public final File file;
        public final int line; // 0-indexed
        public final int column; // 0-indexed
        public final String token;
        public final String quickFixType; // e.g. "ADD_IMPORT", "ADD_SEMICOLON", "ADD_BRACKET"
        public final String quickFixPayload;

        public DiagnosticItem(String id, Severity severity, String message, File file, int line, int column, String token, String quickFixType, String quickFixPayload) {
            this.id = id;
            this.severity = severity;
            this.message = message;
            this.file = file;
            this.line = line;
            this.column = column;
            this.token = token == null ? "" : token;
            this.quickFixType = quickFixType;
            this.quickFixPayload = quickFixPayload;
        }
    }

    private static final Map<String, String> COMMON_JAVA_IMPORTS = new HashMap<>();

    static {
        COMMON_JAVA_IMPORTS.put("Intent", "android.content.Intent");
        COMMON_JAVA_IMPORTS.put("Context", "android.content.Context");
        COMMON_JAVA_IMPORTS.put("View", "android.view.View");
        COMMON_JAVA_IMPORTS.put("ViewGroup", "android.view.ViewGroup");
        COMMON_JAVA_IMPORTS.put("TextView", "android.widget.TextView");
        COMMON_JAVA_IMPORTS.put("EditText", "android.widget.EditText");
        COMMON_JAVA_IMPORTS.put("Button", "android.widget.Button");
        COMMON_JAVA_IMPORTS.put("ImageView", "android.widget.ImageView");
        COMMON_JAVA_IMPORTS.put("Toast", "android.widget.Toast");
        COMMON_JAVA_IMPORTS.put("Bundle", "android.os.Bundle");
        COMMON_JAVA_IMPORTS.put("Activity", "android.app.Activity");
        COMMON_JAVA_IMPORTS.put("Color", "android.graphics.Color");
        COMMON_JAVA_IMPORTS.put("Log", "android.util.Log");
        COMMON_JAVA_IMPORTS.put("File", "java.io.File");
        COMMON_JAVA_IMPORTS.put("Uri", "android.net.Uri");
        COMMON_JAVA_IMPORTS.put("List", "java.util.List");
        COMMON_JAVA_IMPORTS.put("ArrayList", "java.util.ArrayList");
        COMMON_JAVA_IMPORTS.put("Map", "java.util.Map");
        COMMON_JAVA_IMPORTS.put("HashMap", "java.util.HashMap");
        COMMON_JAVA_IMPORTS.put("SharedPreferences", "android.content.SharedPreferences");
        COMMON_JAVA_IMPORTS.put("LayoutInflater", "android.view.LayoutInflater");
    }

    public static List<DiagnosticItem> analyzeFile(File file, String content, String scId) {
        if (content == null) {
            return Collections.emptyList();
        }
        List<DiagnosticItem> diagnostics = new ArrayList<>();
        String fileName = file == null ? "file.java" : file.getName().toLowerCase(Locale.US);

        if (fileName.endsWith(".xml")) {
            analyzeXmlContent(file, content, diagnostics);
        } else if (fileName.endsWith(".kt") || fileName.endsWith(".kts")) {
            analyzeKotlinContent(file, content, diagnostics);
        } else {
            analyzeJavaContent(file, content, diagnostics);
        }

        // Merge Build Errors if available
        if (scId != null && !scId.isEmpty() && file != null) {
            mergeBuildErrors(scId, file, diagnostics);
        }

        return diagnostics;
    }

    private static void analyzeJavaContent(File file, String content, List<DiagnosticItem> diagnostics) {
        String[] lines = content.split("\n", -1);
        Set<String> declaredImports = extractImports(lines);

        // 1. Bracket & Parentheses balance check
        Stack<CharPos> stack = new Stack<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean inString = false;
            boolean inChar = false;

            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == '/' && col + 1 < line.length() && line.charAt(col + 1) == '/') {
                    break;
                }
                if (c == '"' && !inChar) inString = !inString;
                else if (c == '\'' && !inString) inChar = !inChar;

                if (!inString && !inChar) {
                    if (c == '(' || c == '{' || c == '[') {
                        stack.push(new CharPos(c, i, col));
                    } else if (c == ')' || c == '}' || c == ']') {
                        if (stack.isEmpty()) {
                            diagnostics.add(new DiagnosticItem(
                                    "JAVA_UNMATCHED_BRACKET",
                                    Severity.ERROR,
                                    "Unmatched '" + c + "'",
                                    file, i, col, String.valueOf(c), null, null
                            ));
                        } else {
                            char top = stack.peek().ch;
                            if (isMatchingPair(top, c)) {
                                stack.pop();
                            } else {
                                diagnostics.add(new DiagnosticItem(
                                        "JAVA_MISMATCHED_BRACKET",
                                        Severity.ERROR,
                                        "Mismatched bracket '" + c + "' for '" + top + "'",
                                        file, i, col, String.valueOf(c), null, null
                                ));
                            }
                        }
                    }
                }
            }

            // Semicolon check in Java
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")
                    && !trimmed.endsWith("{") && !trimmed.endsWith("}") && !trimmed.endsWith(";") && !trimmed.endsWith(":")
                    && !trimmed.startsWith("@") && !trimmed.startsWith("package ") && !trimmed.startsWith("import ")) {
                if (trimmed.startsWith("return ") || trimmed.startsWith("int ") || trimmed.startsWith("String ") ||
                    trimmed.startsWith("boolean ") || trimmed.contains(" = ") || trimmed.endsWith(")")) {
                    diagnostics.add(new DiagnosticItem(
                            "JAVA_MISSING_SEMICOLON",
                            Severity.ERROR,
                            "Missing ';'",
                            file, i, Math.max(0, line.length() - 1), ";", "ADD_SEMICOLON", String.valueOf(i)
                    ));
                }
            }
        }

        while (!stack.isEmpty()) {
            CharPos unclosed = stack.pop();
            char expected = getMatchingClose(unclosed.ch);
            diagnostics.add(new DiagnosticItem(
                    "JAVA_UNCLOSED_BRACKET",
                    Severity.ERROR,
                    "'" + expected + "' expected",
                    file, unclosed.line, unclosed.col, String.valueOf(unclosed.ch), "ADD_BRACKET", String.valueOf(expected)
            ));
        }

        // 2. Missing Imports Check
        Pattern identifierPattern = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("package ") || line.trim().startsWith("import ") || line.trim().startsWith("//")) {
                continue;
            }
            Matcher matcher = identifierPattern.matcher(line);
            while (matcher.find()) {
                String symbol = matcher.group(1);
                if (COMMON_JAVA_IMPORTS.containsKey(symbol)) {
                    String fullImport = COMMON_JAVA_IMPORTS.get(symbol);
                    if (!declaredImports.contains(symbol) && !declaredImports.contains(fullImport)) {
                        diagnostics.add(new DiagnosticItem(
                                "JAVA_MISSING_IMPORT",
                                Severity.ERROR,
                                "Cannot resolve symbol '" + symbol + "' (Missing import)",
                                file, i, matcher.start(1), symbol, "ADD_IMPORT", fullImport
                        ));
                    }
                }
            }
        }
    }

    private static void analyzeKotlinContent(File file, String content, List<DiagnosticItem> diagnostics) {
        String[] lines = content.split("\n", -1);
        Set<String> declaredImports = extractImports(lines);

        Stack<CharPos> stack = new Stack<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == '(' || c == '{' || c == '[') {
                    stack.push(new CharPos(c, i, col));
                } else if (c == ')' || c == '}' || c == ']') {
                    if (!stack.isEmpty() && isMatchingPair(stack.peek().ch, c)) {
                        stack.pop();
                    } else if (stack.isEmpty()) {
                        diagnostics.add(new DiagnosticItem(
                                "KT_UNMATCHED_BRACKET",
                                Severity.ERROR,
                                "Unmatched '" + c + "'",
                                file, i, col, String.valueOf(c), null, null
                        ));
                    }
                }
            }
        }

        while (!stack.isEmpty()) {
            CharPos unclosed = stack.pop();
            char expected = getMatchingClose(unclosed.ch);
            diagnostics.add(new DiagnosticItem(
                    "KT_UNCLOSED_BRACKET",
                    Severity.ERROR,
                    "'" + expected + "' expected",
                    file, unclosed.line, unclosed.col, String.valueOf(unclosed.ch), "ADD_BRACKET", String.valueOf(expected)
            ));
        }

        Pattern identifierPattern = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("package ") || line.trim().startsWith("import ") || line.trim().startsWith("//")) {
                continue;
            }
            Matcher matcher = identifierPattern.matcher(line);
            while (matcher.find()) {
                String symbol = matcher.group(1);
                if (COMMON_JAVA_IMPORTS.containsKey(symbol)) {
                    String fullImport = COMMON_JAVA_IMPORTS.get(symbol);
                    if (!declaredImports.contains(symbol) && !declaredImports.contains(fullImport)) {
                        diagnostics.add(new DiagnosticItem(
                                "KT_MISSING_IMPORT",
                                Severity.ERROR,
                                "Unresolved reference: " + symbol,
                                file, i, matcher.start(1), symbol, "ADD_IMPORT", fullImport
                        ));
                    }
                }
            }
        }
    }

    private static void analyzeXmlContent(File file, String content, List<DiagnosticItem> diagnostics) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
        } catch (Throwable t) {
            String msg = t.getMessage() == null ? "XML parsing error" : t.getMessage();
            int line = extractXmlErrorLine(msg);
            diagnostics.add(new DiagnosticItem(
                    "XML_PARSE_ERROR",
                    Severity.ERROR,
                    "XML parsing error: " + msg,
                    file, line, 0, "", null, null
            ));
        }
    }

    private static void mergeBuildErrors(String scId, File file, List<DiagnosticItem> diagnostics) {
        try {
            List<VoidPortMarkerCheckService.LintError> lintErrors = VoidPortMarkerCheckService.getLintErrors(scId, file.getAbsolutePath());
            for (VoidPortMarkerCheckService.LintError err : lintErrors) {
                int line = Math.max(0, err.startLineNumber - 1);
                Severity sev = "compile_warning".equals(err.code) ? Severity.WARNING : Severity.ERROR;
                diagnostics.add(new DiagnosticItem(
                        "BUILD_DIAGNOSTIC",
                        sev,
                        err.message,
                        file, line, 0, "", null, null
                ));
            }
        } catch (Throwable ignored) {
        }
    }

    private static Set<String> extractImports(String[] lines) {
        Set<String> imports = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                String imp = trimmed.substring(7).replace(";", "").trim();
                imports.add(imp);
                int lastDot = imp.lastIndexOf('.');
                if (lastDot >= 0 && lastDot < imp.length() - 1) {
                    imports.add(imp.substring(lastDot + 1));
                }
            }
        }
        return imports;
    }

    private static boolean isMatchingPair(char open, char close) {
        return (open == '(' && close == ')') || (open == '{' && close == '}') || (open == '[' && close == ']');
    }

    private static char getMatchingClose(char open) {
        if (open == '(') return ')';
        if (open == '{') return '}';
        if (open == '[') return ']';
        return '}';
    }

    private static int extractXmlErrorLine(String msg) {
        Matcher m = Pattern.compile("lineNumber:\\s*(\\d+)").matcher(msg);
        if (m.find()) {
            try {
                return Math.max(0, Integer.parseInt(m.group(1)) - 1);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static class CharPos {
        final char ch;
        final int line;
        final int col;

        CharPos(char ch, int line, int col) {
            this.ch = ch;
            this.line = line;
            this.col = col;
        }
    }
}
