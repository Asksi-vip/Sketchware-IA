package pro.sketchware.utility;

import android.os.Bundle;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.CodeEditor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, intelligent code completion engine for Java, Kotlin, XML,
 * and local document symbols integrated with Sora CodeEditor.
 */
public class SmartCodeCompletionEngine {

    private static final String[] JAVA_KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
            "while", "yield", "record", "var", "true", "false", "@Override", "@Deprecated", "@SuppressWarnings",
            "@NonNull", "@Nullable"
    };

    private static final String[] JAVA_TYPES = {
            "String", "Object", "System", "Math", "Integer", "Boolean", "Long", "Double", "Float", "Byte",
            "Short", "Character", "StringBuilder", "StringBuffer", "Exception", "Throwable", "Runnable", "Thread",
            "List", "ArrayList", "LinkedList", "Map", "HashMap", "Set", "HashSet", "Context", "Activity", "Bundle",
            "Intent", "View", "ViewGroup", "TextView", "EditText", "Button", "ImageView", "LinearLayout",
            "RelativeLayout", "FrameLayout", "RecyclerView", "Toast", "Log", "R", "File", "Uri", "Color",
            "SharedPreferences", "LayoutInflater", "Toolbar", "DrawerLayout", "FloatingActionButton"
    };

    private static final String[] KOTLIN_KEYWORDS = {
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion", "const",
            "constructor", "continue", "crossinline", "data", "delegate", "do", "else", "enum", "expect", "external",
            "false", "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
            "inline", "inner", "interface", "internal", "is", "it", "lateinit", "noinline", "null", "object",
            "open", "operator", "out", "override", "package", "private", "protected", "public", "reified", "return",
            "sealed", "set", "super", "suspend", "tailrec", "this", "throw", "true", "try", "typealias",
            "val", "var", "vararg", "when", "where", "while"
    };

    private static final String[] KOTLIN_TYPES = {
            "String", "Int", "Boolean", "Long", "Double", "Float", "Char", "Byte", "Short", "Any",
            "Unit", "Nothing", "Array", "List", "ArrayList", "Map", "HashMap", "Set", "HashSet", "Context",
            "Activity", "Bundle", "Intent", "View", "TextView", "EditText", "Button", "ImageView", "Toast", "Log", "File"
    };

    private static final String[] XML_KEYWORDS = {
            "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
            "android:id=\"@+id/\"",
            "android:layout_width=\"match_parent\"",
            "android:layout_height=\"wrap_content\"",
            "android:layout_width=\"wrap_content\"",
            "android:layout_height=\"match_parent\"",
            "android:text=\"\"",
            "android:textSize=\"14sp\"",
            "android:textColor=\"#000000\"",
            "android:background=\"\"",
            "android:padding=\"16dp\"",
            "android:margin=\"8dp\"",
            "android:gravity=\"center\"",
            "android:layout_gravity=\"center\"",
            "android:visibility=\"visible\"",
            "android:visibility=\"gone\"",
            "android:orientation=\"vertical\"",
            "android:orientation=\"horizontal\"",
            "match_parent", "wrap_content", "LinearLayout", "RelativeLayout", "FrameLayout",
            "TextView", "EditText", "Button", "ImageView", "RecyclerView", "ScrollView", "ConstraintLayout", "View"
    };

    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    public static void computeCompletions(@NonNull ContentReference content,
                                         @NonNull CharPosition position,
                                         @NonNull CompletionPublisher publisher,
                                         @NonNull String languageName) {
        try {
            int line = position.line;
            int column = position.column;
            if (line < 0 || line >= content.getLineCount()) {
                return;
            }

            CharSequence lineSequence = content.getLine(line);
            if (column < 0 || column > lineSequence.length()) {
                return;
            }

            // Extract prefix before cursor
            int prefixStart = column;
            while (prefixStart > 0) {
                char c = lineSequence.charAt(prefixStart - 1);
                if (Character.isJavaIdentifierPart(c) || c == ':' || c == '@') {
                    prefixStart--;
                } else {
                    break;
                }
            }

            String prefix = lineSequence.subSequence(prefixStart, column).toString();
            int prefixLength = prefix.length();
            String prefixLower = prefix.toLowerCase(Locale.US);

            Set<String> addedCandidates = new HashSet<>();
            List<CompletionItem> items = new ArrayList<>();

            String lang = languageName == null ? "java" : languageName.toLowerCase(Locale.US);

            // Select Keyword & Type pools based on language
            String[] keywords = (lang.contains("kt") || lang.contains("kotlin")) ? KOTLIN_KEYWORDS :
                               ((lang.contains("xml")) ? XML_KEYWORDS : JAVA_KEYWORDS);
            String[] types = (lang.contains("kt") || lang.contains("kotlin")) ? KOTLIN_TYPES :
                            ((lang.contains("xml")) ? new String[0] : JAVA_TYPES);

            // Dot Member Intellisense (e.g. textView., Math., R.id.)
            int dotIdx = lineSequence.subSequence(0, column).toString().lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < column) {
                String fullLine = content.toString();
                String beforeDot = lineSequence.subSequence(0, dotIdx).toString().trim();
                int wordStart = beforeDot.length() - 1;
                while (wordStart >= 0 && (Character.isJavaIdentifierPart(beforeDot.charAt(wordStart)) || beforeDot.charAt(wordStart) == '.')) {
                    wordStart--;
                }
                String targetSymbol = beforeDot.substring(wordStart + 1).trim();
                String memberPrefix = lineSequence.subSequence(dotIdx + 1, column).toString().toLowerCase(Locale.US);

                if (!targetSymbol.isEmpty()) {
                    boolean isStatic = Character.isUpperCase(targetSymbol.charAt(0)) || targetSymbol.startsWith("R.");
                    String typeName = isStatic ? targetSymbol : CodeIntelligenceEngine.resolveVariableType(fullLine, targetSymbol, lang);
                    List<String> members = CodeIntelligenceEngine.getTypeMembers(typeName, isStatic);

                    for (String m : members) {
                        if (m.toLowerCase(Locale.US).startsWith(memberPrefix) && addedCandidates.add(m)) {
                            items.add(new SmartCompletionItem(m, "Member (" + typeName + ")", memberPrefix.length()));
                        }
                    }
                }
            }

            // 1. Keywords Match
            for (String kw : keywords) {
                String kwLower = kw.toLowerCase(Locale.US);
                if (kwLower.startsWith(prefixLower) && addedCandidates.add(kw)) {
                    items.add(new SmartCompletionItem(kw, "Keyword", prefixLength));
                }
            }

            // 2. Types Match
            for (String type : types) {
                String typeLower = type.toLowerCase(Locale.US);
                if (typeLower.startsWith(prefixLower) && addedCandidates.add(type)) {
                    items.add(new SmartCompletionItem(type, "Class / Type", prefixLength));
                }
            }

            // 3. Document Identifiers Match
            Set<String> docWords = extractDocumentWords(content);
            for (String word : docWords) {
                if (word.length() > 1 && word.toLowerCase(Locale.US).startsWith(prefixLower) && addedCandidates.add(word)) {
                    items.add(new SmartCompletionItem(word, "Local Symbol", prefixLength));
                }
            }

            // Limit maximum suggestions for smooth rendering
            int maxResults = Math.min(items.size(), 35);
            for (int i = 0; i < maxResults; i++) {
                publisher.addItem(items.get(i));
            }
            if (maxResults > 0) {
                publisher.updateList(true);
            }
        } catch (Throwable ignored) {
            // Non-blocking fallback
        }
    }

    private static Set<String> extractDocumentWords(ContentReference content) {
        Set<String> words = new HashSet<>();
        try {
            int lineCount = Math.min(content.getLineCount(), 500);
            for (int i = 0; i < lineCount; i++) {
                CharSequence line = content.getLine(i);
                Matcher matcher = WORD_PATTERN.matcher(line);
                while (matcher.find()) {
                    words.add(matcher.group());
                }
            }
        } catch (Throwable ignored) {
        }
        return words;
    }

    public static class SmartCompletionItem extends CompletionItem {
        private final String itemText;
        private final int prefixLength;

        public SmartCompletionItem(String itemText, String category, int prefixLength) {
            super(itemText, category);
            this.itemText = itemText;
            this.prefixLength = prefixLength;
        }

        @Override
        public void performCompletion(@NonNull CodeEditor editor, @NonNull Content text, int line, int column) {
            if (itemText == null || itemText.isEmpty()) {
                return;
            }
            int startColumn = Math.max(0, column - prefixLength);
            if (prefixLength > 0 && column >= prefixLength) {
                text.delete(line, startColumn, line, column);
            }
            text.insert(line, startColumn, itemText);
            int newOffset = text.getCharIndex(line, startColumn) + itemText.length();
            CharPosition end = text.getIndexer().getCharPosition(newOffset);
            editor.setSelection(end.line, end.column);
        }
    }
}
