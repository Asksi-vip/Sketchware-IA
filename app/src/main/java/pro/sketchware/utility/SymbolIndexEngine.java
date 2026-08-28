package pro.sketchware.utility;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SymbolIndexEngine parses and indexes code symbols (Classes, Methods, Functions,
 * Fields, Properties, XML IDs) across Java, Kotlin, and XML files in the project.
 */
public class SymbolIndexEngine {

    public static class SymbolDefinition {
        public final String name;
        public final String kind;
        public final File file;
        public final int line;
        public final int column;
        public final String snippet;
        public final String detail;

        public SymbolDefinition(String name, String kind, File file, int line, int column, String snippet, String detail) {
            this.name = name;
            this.kind = kind;
            this.file = file;
            this.line = line;
            this.column = column;
            this.snippet = snippet == null ? "" : snippet.trim();
            this.detail = detail == null ? "" : detail;
        }
    }

    public static class SymbolReference {
        public final String name;
        public final File file;
        public final int line;
        public final int column;
        public final String snippet;
        public final boolean isDefinition;

        public SymbolReference(String name, File file, int line, int column, String snippet, boolean isDefinition) {
            this.name = name;
            this.file = file;
            this.line = line;
            this.column = column;
            this.snippet = snippet == null ? "" : snippet.trim();
            this.isDefinition = isDefinition;
        }
    }

    private static final Pattern JAVA_KOTLIN_CLASS_PATTERN =
            Pattern.compile("\\b(class|interface|enum|record|object)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static final Pattern JAVA_METHOD_PATTERN =
            Pattern.compile("\\b(?:public|private|protected|static|final|native|synchronized|abstract|default|\\s)+[\\w<>?\\[\\]\\.]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private static final Pattern KOTLIN_FUN_PATTERN =
            Pattern.compile("\\bfun\\s+(?:<[^>]+>\\s+)?(?:[A-Za-z0-9_]+\\.)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private static final Pattern KOTLIN_PROP_PATTERN =
            Pattern.compile("\\b(val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static final Pattern JAVA_FIELD_PATTERN =
            Pattern.compile("\\b(?:public|private|protected|static|final|volatile|transient)\\s+[\\w<>?\\[\\]\\.]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|[;=])");

    private static final Pattern XML_ID_PATTERN =
            Pattern.compile("android:id=\"@\\+id/([A-Za-z_][A-Za-z0-9_]*)\"");

    private static final Map<String, Map<String, List<SymbolDefinition>>> PROJECT_INDEX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> INDEX_TIMESTAMP = new ConcurrentHashMap<>();

    public static synchronized void indexProjectIfNeeded(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return;
        }
        String rootPath = projectRoot.getAbsolutePath();
        Long lastIndexed = INDEX_TIMESTAMP.get(rootPath);
        long now = System.currentTimeMillis();
        // Re-index at most once every 10 seconds
        if (lastIndexed != null && (now - lastIndexed < 10000) && PROJECT_INDEX_CACHE.containsKey(rootPath)) {
            return;
        }

        Map<String, List<SymbolDefinition>> symbolMap = new HashMap<>();
        List<File> files = collectProjectFiles(projectRoot);

        for (File file : files) {
            indexSingleFile(file, symbolMap);
        }

        PROJECT_INDEX_CACHE.put(rootPath, symbolMap);
        INDEX_TIMESTAMP.put(rootPath, now);
    }

    public static void indexSingleFile(File file, Map<String, List<SymbolDefinition>> symbolMap) {
        if (file == null || !file.isFile()) {
            return;
        }
        String fileName = file.getName().toLowerCase(Locale.US);
        if (!fileName.endsWith(".java") && !fileName.endsWith(".kt") && !fileName.endsWith(".kts") && !fileName.endsWith(".xml")) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    lineNumber++;
                    continue;
                }

                if (fileName.endsWith(".xml")) {
                    Matcher xmlMatcher = XML_ID_PATTERN.matcher(line);
                    while (xmlMatcher.find()) {
                        String idName = xmlMatcher.group(1);
                        int col = xmlMatcher.start(1);
                        addDefinition(symbolMap, idName, new SymbolDefinition(idName, "XML ID", file, lineNumber, col, line, "XML Resource ID"));
                    }
                } else {
                    // Classes & Interfaces & Enums
                    Matcher classMatcher = JAVA_KOTLIN_CLASS_PATTERN.matcher(line);
                    while (classMatcher.find()) {
                        String type = classMatcher.group(1);
                        String className = classMatcher.group(2);
                        int col = classMatcher.start(2);
                        addDefinition(symbolMap, className, new SymbolDefinition(className, type.toUpperCase(Locale.US), file, lineNumber, col, line, type + " definition"));
                    }

                    // Kotlin fun
                    Matcher funMatcher = KOTLIN_FUN_PATTERN.matcher(line);
                    while (funMatcher.find()) {
                        String funName = funMatcher.group(1);
                        int col = funMatcher.start(1);
                        addDefinition(symbolMap, funName, new SymbolDefinition(funName, "FUNCTION", file, lineNumber, col, line, "Kotlin function"));
                    }

                    // Java Method
                    Matcher methodMatcher = JAVA_METHOD_PATTERN.matcher(line);
                    while (methodMatcher.find()) {
                        String methodName = methodMatcher.group(1);
                        int col = methodMatcher.start(1);
                        addDefinition(symbolMap, methodName, new SymbolDefinition(methodName, "METHOD", file, lineNumber, col, line, "Java method"));
                    }

                    // Kotlin Properties (val/var)
                    Matcher propMatcher = KOTLIN_PROP_PATTERN.matcher(line);
                    while (propMatcher.find()) {
                        String propKind = propMatcher.group(1);
                        String propName = propMatcher.group(2);
                        int col = propMatcher.start(2);
                        addDefinition(symbolMap, propName, new SymbolDefinition(propName, "PROPERTY (" + propKind + ")", file, lineNumber, col, line, "Kotlin property"));
                    }

                    // Java Fields
                    Matcher fieldMatcher = JAVA_FIELD_PATTERN.matcher(line);
                    while (fieldMatcher.find()) {
                        String fieldName = fieldMatcher.group(1);
                        int col = fieldMatcher.start(1);
                        addDefinition(symbolMap, fieldName, new SymbolDefinition(fieldName, "FIELD", file, lineNumber, col, line, "Java field"));
                    }
                }
                lineNumber++;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void addDefinition(Map<String, List<SymbolDefinition>> map, String name, SymbolDefinition def) {
        List<SymbolDefinition> list = map.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(def);
    }

    public static List<SymbolDefinition> findDefinitions(File projectRoot, String symbolName) {
        if (symbolName == null || symbolName.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleanSymbol = sanitizeSymbol(symbolName);

        if (projectRoot != null) {
            indexProjectIfNeeded(projectRoot);
            Map<String, List<SymbolDefinition>> symbolMap = PROJECT_INDEX_CACHE.get(projectRoot.getAbsolutePath());
            if (symbolMap != null && symbolMap.containsKey(cleanSymbol)) {
                return symbolMap.get(cleanSymbol);
            }
        }
        return Collections.emptyList();
    }

    public static List<SymbolReference> findReferences(File projectRoot, String symbolName) {
        if (symbolName == null || symbolName.trim().isEmpty() || projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        String cleanSymbol = sanitizeSymbol(symbolName);
        List<SymbolReference> references = new ArrayList<>();
        Pattern refPattern = Pattern.compile("\\b" + Pattern.quote(cleanSymbol) + "\\b");

        List<File> files = collectProjectFiles(projectRoot);
        for (File file : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = refPattern.matcher(line);
                    while (matcher.find()) {
                        int col = matcher.start();
                        boolean isDef = isLineDefinition(line, cleanSymbol);
                        references.add(new SymbolReference(cleanSymbol, file, lineNumber, col, line, isDef));
                    }
                    lineNumber++;
                }
            } catch (Throwable ignored) {
            }
        }
        return references;
    }

    private static boolean isLineDefinition(String line, String symbol) {
        return line.contains("class " + symbol) || line.contains("interface " + symbol) ||
               line.contains("fun " + symbol) || line.contains("void " + symbol) ||
               line.contains("@+id/" + symbol);
    }

    public static String sanitizeSymbol(String rawSymbol) {
        if (rawSymbol == null) return "";
        String s = rawSymbol.trim();
        if (s.startsWith("R.id.")) {
            s = s.substring(5);
        } else if (s.startsWith("@+id/")) {
            s = s.substring(5);
        } else if (s.startsWith("@id/")) {
            s = s.substring(4);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot < s.length() - 1) {
            s = s.substring(dot + 1);
        }
        int paren = s.indexOf('(');
        if (paren >= 0) {
            s = s.substring(0, paren);
        }
        return s.trim();
    }

    public static List<File> collectProjectFiles(File dir) {
        List<File> result = new ArrayList<>();
        collectFilesRecursive(dir, result, 0);
        return result;
    }

    private static void collectFilesRecursive(File dir, List<File> result, int depth) {
        if (dir == null || !dir.exists() || depth > 12) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".") || name.equals("build") || name.equals("bin") || name.equals("temp") || name.equals("bak")) {
                continue;
            }
            if (file.isDirectory()) {
                collectFilesRecursive(file, result, depth + 1);
            } else if (file.isFile()) {
                String lower = name.toLowerCase(Locale.US);
                if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".kts") || lower.endsWith(".xml")) {
                    result.add(file);
                }
            }
        }
    }
}
