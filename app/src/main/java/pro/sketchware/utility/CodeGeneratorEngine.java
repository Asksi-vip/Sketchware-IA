package pro.sketchware.utility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeGeneratorEngine provides Java & Kotlin code generation utilities:
 * Constructor, Getters & Setters, Override Methods, Custom Methods, Listeners,
 * and findViewById bindings.
 */
public class CodeGeneratorEngine {

    public static class FieldModel {
        public final String name;
        public final String type;

        public FieldModel(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private static final Pattern JAVA_FIELD_PATTERN =
            Pattern.compile("\\b(?:private|protected|public)\\s+([A-Za-z0-9_<>?\\[\\]]+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");

    private static final Pattern KOTLIN_FIELD_PATTERN =
            Pattern.compile("\\b(?:val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([A-Za-z0-9_<>?]+)");

    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");

    public static List<FieldModel> parseFields(String code, String languageName) {
        List<FieldModel> fields = new ArrayList<>();
        if (code == null || code.isEmpty()) return fields;

        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));
        if (isKotlin) {
            Matcher m = KOTLIN_FIELD_PATTERN.matcher(code);
            while (m.find()) {
                String name = m.group(1);
                String type = m.group(2);
                fields.add(new FieldModel(name, type));
            }
        } else {
            Matcher m = JAVA_FIELD_PATTERN.matcher(code);
            while (m.find()) {
                String type = m.group(1);
                String name = m.group(2);
                fields.add(new FieldModel(name, type));
            }
        }
        return fields;
    }

    public static String parseClassName(String code) {
        if (code == null) return "MyClass";
        Matcher m = CLASS_NAME_PATTERN.matcher(code);
        if (m.find()) {
            return m.group(1);
        }
        return "MyClass";
    }

    public static String generateConstructor(String code, List<FieldModel> selectedFields, String languageName) {
        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));
        String className = parseClassName(code);
        StringBuilder sb = new StringBuilder();

        if (isKotlin) {
            sb.append("    constructor(");
            for (int i = 0; i < selectedFields.size(); i++) {
                FieldModel f = selectedFields.get(i);
                sb.append(f.name).append(": ").append(f.type);
                if (i < selectedFields.size() - 1) sb.append(", ");
            }
            sb.append(") {\n");
            for (FieldModel f : selectedFields) {
                sb.append("        this.").append(f.name).append(" = ").append(f.name).append("\n");
            }
            sb.append("    }\n");
        } else {
            sb.append("    public ").append(className).append("(");
            for (int i = 0; i < selectedFields.size(); i++) {
                FieldModel f = selectedFields.get(i);
                sb.append(f.type).append(" ").append(f.name);
                if (i < selectedFields.size() - 1) sb.append(", ");
            }
            sb.append(") {\n");
            for (FieldModel f : selectedFields) {
                sb.append("        this.").append(f.name).append(" = ").append(f.name).append(";\n");
            }
            sb.append("    }\n");
        }
        return sb.toString();
    }

    public static String generateGettersAndSetters(List<FieldModel> selectedFields, boolean generateGetter, boolean generateSetter, String languageName) {
        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));
        StringBuilder sb = new StringBuilder();

        for (FieldModel f : selectedFields) {
            String capName = capitalize(f.name);

            if (isKotlin) {
                if (generateGetter) {
                    sb.append("    fun get").append(capName).append("(): ").append(f.type).append(" = ").append(f.name).append("\n\n");
                }
                if (generateSetter) {
                    sb.append("    fun set").append(capName).append("(").append(f.name).append(": ").append(f.type).append(") {\n");
                    sb.append("        this.").append(f.name).append(" = ").append(f.name).append("\n");
                    sb.append("    }\n\n");
                }
            } else {
                if (generateGetter) {
                    sb.append("    public ").append(f.type).append(" get").append(capName).append("() {\n");
                    sb.append("        return ").append(f.name).append(";\n");
                    sb.append("    }\n\n");
                }
                if (generateSetter) {
                    sb.append("    public void set").append(capName).append("(").append(f.type).append(" ").append(f.name).append(") {\n");
                    sb.append("        this.").append(f.name).append(" = ").append(f.name).append(";\n");
                    sb.append("    }\n\n");
                }
            }
        }
        return sb.toString();
    }

    public static String generateOverrideMethod(String methodKey, String languageName) {
        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));
        StringBuilder sb = new StringBuilder();

        switch (methodKey) {
            case "onCreate":
                if (isKotlin) {
                    sb.append("    override fun onCreate(savedInstanceState: Bundle?) {\n");
                    sb.append("        super.onCreate(savedInstanceState)\n");
                    sb.append("    }\n");
                } else {
                    sb.append("    @Override\n");
                    sb.append("    protected void onCreate(Bundle savedInstanceState) {\n");
                    sb.append("        super.onCreate(savedInstanceState);\n");
                    sb.append("    }\n");
                }
                break;
            case "onStart":
                if (isKotlin) {
                    sb.append("    override fun onStart() {\n");
                    sb.append("        super.onStart()\n");
                    sb.append("    }\n");
                } else {
                    sb.append("    @Override\n");
                    sb.append("    protected void onStart() {\n");
                    sb.append("        super.onStart();\n");
                    sb.append("    }\n");
                }
                break;
            case "onResume":
                if (isKotlin) {
                    sb.append("    override fun onResume() {\n");
                    sb.append("        super.onResume()\n");
                    sb.append("    }\n");
                } else {
                    sb.append("    @Override\n");
                    sb.append("    protected void onResume() {\n");
                    sb.append("        super.onResume();\n");
                    sb.append("    }\n");
                }
                break;
            case "onPause":
                if (isKotlin) {
                    sb.append("    override fun onPause() {\n");
                    sb.append("        super.onPause()\n");
                    sb.append("    }\n");
                } else {
                    sb.append("    @Override\n");
                    sb.append("    protected void onPause() {\n");
                    sb.append("        super.onPause();\n");
                    sb.append("    }\n");
                }
                break;
            case "onDestroy":
                if (isKotlin) {
                    sb.append("    override fun onDestroy() {\n");
                    sb.append("        super.onDestroy()\n");
                    sb.append("    }\n");
                } else {
                    sb.append("    @Override\n");
                    sb.append("    protected void onDestroy() {\n");
                    sb.append("        super.onDestroy();\n");
                    sb.append("    }\n");
                }
                break;
            case "onClick":
                if (isKotlin) {
                    sb.append("    override fun onClick(v: View?) {\n");
                    sb.append("    }\n");
                } else {
                    sb.append("    @Override\n");
                    sb.append("    public void onClick(View v) {\n");
                    sb.append("    }\n");
                }
                break;
            default:
                if (isKotlin) {
                    sb.append("    override fun ").append(methodKey).append("() {\n");
                    sb.append("        super.").append(methodKey).append("()\n");
                    sb.append("    }\n");
                } else {
                    sb.append("    @Override\n");
                    sb.append("    public void ").append(methodKey).append("() {\n");
                    sb.append("        super.").append(methodKey).append("();\n");
                    sb.append("    }\n");
                }
                break;
        }
        return sb.toString();
    }

    public static String generateCustomMethod(String name, String returnType, String params, String visibility, boolean isStatic, String languageName) {
        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));
        StringBuilder sb = new StringBuilder();
        String vis = visibility == null ? "public" : visibility.toLowerCase(Locale.US);

        if (isKotlin) {
            sb.append("    ").append(vis.equals("package") ? "" : vis).append(" ");
            sb.append("fun ").append(name).append("(").append(params == null ? "" : params).append(")");
            if (returnType != null && !returnType.equalsIgnoreCase("void") && !returnType.equalsIgnoreCase("unit")) {
                sb.append(": ").append(returnType);
            }
            sb.append(" {\n");
            sb.append("    }\n");
        } else {
            sb.append("    ").append(vis).append(" ");
            if (isStatic) sb.append("static ");
            sb.append(returnType == null || returnType.trim().isEmpty() ? "void" : returnType).append(" ");
            sb.append(name).append("(").append(params == null ? "" : params).append(") {\n");
            sb.append("    }\n");
        }
        return sb.toString();
    }

    public static String generateListener(String targetView, String listenerType, String languageName) {
        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));
        StringBuilder sb = new StringBuilder();
        String target = (targetView == null || targetView.trim().isEmpty()) ? "button" : targetView.trim();

        if (isKotlin) {
            if ("OnClickListener".equalsIgnoreCase(listenerType)) {
                sb.append("    ").append(target).append(".setOnClickListener { v ->\n");
                sb.append("    }\n");
            } else if ("OnLongClickListener".equalsIgnoreCase(listenerType)) {
                sb.append("    ").append(target).append(".setOnLongClickListener { v ->\n");
                sb.append("        true\n");
                sb.append("    }\n");
            } else {
                sb.append("    ").append(target).append(".setOnClickListener { v ->\n");
                sb.append("    }\n");
            }
        } else {
            if ("OnClickListener".equalsIgnoreCase(listenerType)) {
                sb.append("    ").append(target).append(".setOnClickListener(v -> {\n");
                sb.append("    });\n");
            } else if ("OnLongClickListener".equalsIgnoreCase(listenerType)) {
                sb.append("    ").append(target).append(".setOnLongClickListener(v -> {\n");
                sb.append("        return true;\n");
                sb.append("    });\n");
            } else {
                sb.append("    ").append(target).append(".setOnClickListener(v -> {\n");
                sb.append("    });\n");
            }
        }
        return sb.toString();
    }

    public static String generateFindViewById(String code, String languageName) {
        boolean isKotlin = languageName != null && (languageName.contains("kt") || languageName.contains("kotlin"));
        List<FieldModel> fields = parseFields(code, languageName);
        StringBuilder sb = new StringBuilder();

        for (FieldModel f : fields) {
            // Check if field is likely a View (e.g. ends with View, Button, Text, Layout, etc.)
            if (isViewType(f.type)) {
                if (isKotlin) {
                    sb.append("    ").append(f.name).append(" = findViewById(R.id.").append(f.name).append(")\n");
                } else {
                    sb.append("    ").append(f.name).append(" = findViewById(R.id.").append(f.name).append(");\n");
                }
            }
        }
        if (sb.length() == 0) {
            // Default placeholder if no fields parsed
            if (isKotlin) {
                sb.append("    myView = findViewById(R.id.my_view)\n");
            } else {
                sb.append("    myView = findViewById(R.id.my_view);\n");
            }
        }
        return sb.toString();
    }

    private static boolean isViewType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase(Locale.US);
        return t.contains("view") || t.contains("button") || t.contains("text") ||
               t.contains("image") || t.contains("layout") || t.contains("list") ||
               t.contains("recycler") || t.contains("card") || t.contains("check");
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
