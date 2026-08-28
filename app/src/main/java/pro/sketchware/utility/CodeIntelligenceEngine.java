package pro.sketchware.utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeIntelligenceEngine powers Type-Aware completion, Hover information,
 * Signature Help, Breadcrumbs, and Resource Intellisense across Java, Kotlin, and XML.
 */
public class CodeIntelligenceEngine {

    public static class MethodSignature {
        public final String name;
        public final String returnType;
        public final List<String> parameters;

        public MethodSignature(String name, String returnType, List<String> parameters) {
            this.name = name;
            this.returnType = returnType == null ? "void" : returnType;
            this.parameters = parameters == null ? Collections.emptyList() : parameters;
        }

        public String toDisplayString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append("(");
            for (int i = 0; i < parameters.size(); i++) {
                sb.append(parameters.get(i));
                if (i < parameters.size() - 1) sb.append(", ");
            }
            sb.append("): ").append(returnType);
            return sb.toString();
        }
    }

    public static class ParameterHint {
        public final String methodName;
        public final List<String> parameters;
        public final int activeIndex;

        public ParameterHint(String methodName, List<String> parameters, int activeIndex) {
            this.methodName = methodName;
            this.parameters = parameters;
            this.activeIndex = activeIndex;
        }
    }

    public static class HoverInfo {
        public final String title;
        public final String category;
        public final String details;
        public final String location;

        public HoverInfo(String title, String category, String details, String location) {
            this.title = title;
            this.category = category;
            this.details = details;
            this.location = location;
        }
    }

    private static final Map<String, List<String>> CLASS_MEMBERS = new HashMap<>();
    private static final Map<String, List<String>> STATIC_MEMBERS = new HashMap<>();
    private static final Map<String, List<MethodSignature>> COMMON_SIGNATURES = new HashMap<>();

    static {
        // TextView & EditText members
        List<String> tvMembers = List.of(
                "setText(CharSequence text)", "getText()", "setTextColor(int color)",
                "setTextSize(float size)", "setVisibility(int visibility)",
                "setOnClickListener(OnClickListener l)", "setPadding(int left, int top, int right, int bottom)",
                "requestFocus()", "setHint(CharSequence hint)", "setEnabled(boolean enabled)"
        );
        CLASS_MEMBERS.put("TextView", tvMembers);
        CLASS_MEMBERS.put("EditText", tvMembers);
        CLASS_MEMBERS.put("Button", tvMembers);
        CLASS_MEMBERS.put("View", tvMembers);

        // Intent members
        List<String> intentMembers = List.of(
                "putExtra(String name, String value)", "getStringExtra(String name)",
                "getIntExtra(String name, int defaultValue)", "setFlags(int flags)",
                "setClass(Context packageContext, Class<?> cls)", "setAction(String action)", "setData(Uri data)"
        );
        CLASS_MEMBERS.put("Intent", intentMembers);

        // Toast static members
        STATIC_MEMBERS.put("Toast", List.of("makeText(Context context, CharSequence text, int duration)", "LENGTH_SHORT", "LENGTH_LONG"));
        // Math static members
        STATIC_MEMBERS.put("Math", List.of("max(a, b)", "min(a, b)", "abs(a)", "round(a)", "floor(a)", "ceil(a)", "pow(a, b)", "sqrt(a)", "random()", "PI", "E"));
        // Color static members
        STATIC_MEMBERS.put("Color", List.of("parseColor(String colorString)", "rgb(red, green, blue)", "argb(alpha, red, green, blue)", "RED", "GREEN", "BLUE", "BLACK", "WHITE"));
        // Log static members
        STATIC_MEMBERS.put("Log", List.of("d(String tag, String msg)", "e(String tag, String msg)", "i(String tag, String msg)", "w(String tag, String msg)", "v(String tag, String msg)"));

        // Signatures setup
        COMMON_SIGNATURES.put("makeText", List.of(new MethodSignature("makeText", "Toast", List.of("Context context", "CharSequence text", "int duration"))));
        COMMON_SIGNATURES.put("showMessage", List.of(new MethodSignature("showMessage", "void", List.of("String message"))));
        COMMON_SIGNATURES.put("findViewById", List.of(new MethodSignature("findViewById", "<T extends View> T", List.of("int id"))));
        COMMON_SIGNATURES.put("setText", List.of(new MethodSignature("setText", "void", List.of("CharSequence text"))));
        COMMON_SIGNATURES.put("setOnClickListener", List.of(new MethodSignature("setOnClickListener", "void", List.of("View.OnClickListener listener"))));
    }

    public static String resolveVariableType(String code, String varName, String languageName) {
        if (code == null || varName == null || varName.trim().isEmpty()) {
            return "Object";
        }
        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));

        if (isKotlin) {
            // val varName: Type
            Matcher m = Pattern.compile("\\b(?:val|var)\\s+" + Pattern.quote(varName) + "\\s*:\\s*([A-Za-z0-9_<>?]+)").matcher(code);
            if (m.find()) return m.group(1);
        } else {
            // Type varName;
            Matcher m = Pattern.compile("\\b([A-Z][A-Za-z0-9_<>?]*)\\s+" + Pattern.quote(varName) + "\\b").matcher(code);
            if (m.find()) return m.group(1);
        }

        // Common naming heuristics if not explicitly declared
        String lower = varName.toLowerCase(Locale.US);
        if (lower.contains("text") || lower.contains("title") || lower.contains("label")) return "TextView";
        if (lower.contains("edit") || lower.contains("input")) return "EditText";
        if (lower.contains("btn") || lower.contains("button")) return "Button";
        if (lower.contains("intent")) return "Intent";
        if (lower.contains("file")) return "File";
        if (lower.contains("img") || lower.contains("image")) return "ImageView";

        return "Object";
    }

    public static List<String> getTypeMembers(String typeName, boolean isStatic) {
        if (typeName == null) return Collections.emptyList();
        String cleanType = typeName.replace("?", "").trim();
        if (isStatic && STATIC_MEMBERS.containsKey(cleanType)) {
            return STATIC_MEMBERS.get(cleanType);
        }
        if (CLASS_MEMBERS.containsKey(cleanType)) {
            return CLASS_MEMBERS.get(cleanType);
        }
        return Collections.emptyList();
    }

    public static ParameterHint getParameterHint(String linePrefix) {
        if (linePrefix == null || linePrefix.isEmpty()) return null;
        int openParen = linePrefix.lastIndexOf('(');
        if (openParen < 0) return null;

        // Extract method name before '('
        int start = openParen - 1;
        while (start >= 0 && (Character.isJavaIdentifierPart(linePrefix.charAt(start)) || linePrefix.charAt(start) == '.')) {
            start--;
        }
        String fullCall = linePrefix.substring(start + 1, openParen).trim();
        int dot = fullCall.lastIndexOf('.');
        String methodName = dot >= 0 ? fullCall.substring(dot + 1) : fullCall;

        if (methodName.isEmpty()) return null;

        // Calculate active parameter index by counting commas outside parens
        String argsPart = linePrefix.substring(openParen + 1);
        int commaCount = 0;
        int depth = 0;
        for (int i = 0; i < argsPart.length(); i++) {
            char c = argsPart.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            else if (c == ')' || c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) commaCount++;
        }

        List<MethodSignature> sigs = COMMON_SIGNATURES.get(methodName);
        List<String> params = (sigs != null && !sigs.isEmpty()) ? sigs.get(0).parameters : List.of("Object arg" + (commaCount + 1));

        return new ParameterHint(methodName, params, commaCount);
    }

    public static HoverInfo getHoverInfo(String symbol, String code, File file, String languageName) {
        if (symbol == null || symbol.trim().isEmpty()) return null;
        String clean = SymbolIndexEngine.sanitizeSymbol(symbol);

        // Check if defined in project index
        List<SymbolIndexEngine.SymbolDefinition> defs = SymbolIndexEngine.findDefinitions(file == null ? null : file.getParentFile(), clean);
        if (!defs.isEmpty()) {
            SymbolIndexEngine.SymbolDefinition d = defs.get(0);
            return new HoverInfo(
                    d.name,
                    d.kind,
                    d.snippet,
                    d.file.getName() + " (Line " + (d.line + 1) + ")"
            );
        }

        // Standard library / Android type info
        if (CLASS_MEMBERS.containsKey(clean) || STATIC_MEMBERS.containsKey(clean)) {
            return new HoverInfo(
                    clean,
                    "Android / Java Class",
                    "android.view." + clean + " or android.widget." + clean,
                    "Android SDK"
            );
        }

        // Local variable lookup
        String resolvedType = resolveVariableType(code, clean, languageName);
        if (!"Object".equals(resolvedType)) {
            return new HoverInfo(
                    clean,
                    "Local Symbol (" + resolvedType + ")",
                    resolvedType + " " + clean,
                    file == null ? "Current Document" : file.getName()
            );
        }

        return new HoverInfo(clean, "Symbol", symbol, file == null ? "Document" : file.getName());
    }

    public static List<String> getBreadcrumbs(String code, int cursorLine) {
        List<String> breadcrumbs = new ArrayList<>();
        if (code == null || code.isEmpty()) return breadcrumbs;

        String[] lines = code.split("\n", -1);
        int targetLine = Math.max(0, Math.min(cursorLine, lines.length - 1));

        String currentClass = null;
        String currentMethod = null;

        Pattern classP = Pattern.compile("\\b(class|interface|enum|object)\\s+([A-Za-z_][A-Za-z0-9_]*)");
        Pattern methodP = Pattern.compile("\\b(?:fun|void|int|String|boolean|View|public|private|protected)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

        for (int i = 0; i <= targetLine; i++) {
            String line = lines[i];
            Matcher cm = classP.matcher(line);
            if (cm.find()) {
                currentClass = cm.group(2);
            }
            Matcher mm = methodP.matcher(line);
            if (mm.find()) {
                String mName = mm.group(1);
                if (!mName.equals("if") && !mName.equals("for") && !mName.equals("while") && !mName.equals("switch")) {
                    currentMethod = mName + "()";
                }
            }
        }

        if (currentClass != null) breadcrumbs.add(currentClass);
        if (currentMethod != null) breadcrumbs.add(currentMethod);

        return breadcrumbs;
    }

    public static List<String> fetchResourceSymbols(File projectRoot, String resPrefix) {
        List<String> resSymbols = new ArrayList<>();
        if (projectRoot == null) return resSymbols;

        String category = "id";
        if (resPrefix.startsWith("R.")) {
            String[] parts = resPrefix.split("\\.");
            if (parts.length > 1) {
                category = parts[1].toLowerCase(Locale.US);
            }
        }

        List<SymbolIndexEngine.SymbolDefinition> defs = SymbolIndexEngine.findDefinitions(projectRoot, "");
        for (SymbolIndexEngine.SymbolDefinition def : SymbolIndexEngine.findDefinitions(projectRoot, "id")) {
            resSymbols.add("R.id." + def.name);
        }

        if (resSymbols.isEmpty()) {
            resSymbols.add("R.id.button1");
            resSymbols.add("R.id.textView1");
            resSymbols.add("R.id.editText1");
            resSymbols.add("R.id.recyclerView1");
            resSymbols.add("R.id.layout_root");
        }
        return resSymbols;
    }
}
