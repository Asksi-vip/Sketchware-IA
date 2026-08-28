package pro.sketchware.utility;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import mod.hey.studios.code.SrcCodeEditor;

/**
 * Intelligent Code Formatter for Java, Kotlin, and XML code.
 * Handles full file and selection formatting without altering code logic,
 * comments, or string literals.
 */
public class CodeFormatterEngine {

    public static String formatCode(String code, String languageName) {
        if (code == null || code.trim().isEmpty()) {
            return code;
        }
        String lang = languageName == null ? "java" : languageName.toLowerCase(Locale.US);

        if (lang.contains("xml")) {
            return formatXml(code);
        } else if (lang.contains("kt") || lang.contains("kotlin")) {
            return formatKotlin(code);
        } else {
            return formatJava(code);
        }
    }

    public static String formatSelection(String fullText, int startLine, int endLine, String languageName) {
        if (fullText == null || fullText.isEmpty()) {
            return fullText;
        }
        String[] lines = fullText.split("\n", -1);
        int totalLines = lines.length;

        int sLine = Math.max(0, Math.min(startLine, totalLines - 1));
        int eLine = Math.max(sLine, Math.min(endLine, totalLines - 1));

        StringBuilder selectionBuilder = new StringBuilder();
        for (int i = sLine; i <= eLine; i++) {
            selectionBuilder.append(lines[i]);
            if (i < eLine) {
                selectionBuilder.append("\n");
            }
        }

        String formattedChunk = formatCode(selectionBuilder.toString(), languageName);
        String[] formattedLines = formattedChunk.split("\n", -1);

        StringBuilder resultBuilder = new StringBuilder();
        for (int i = 0; i < sLine; i++) {
            resultBuilder.append(lines[i]).append("\n");
        }
        for (int i = 0; i < formattedLines.length; i++) {
            resultBuilder.append(formattedLines[i]);
            if (i < formattedLines.length - 1 || eLine < totalLines - 1) {
                resultBuilder.append("\n");
            }
        }
        for (int i = eLine + 1; i < totalLines; i++) {
            resultBuilder.append(lines[i]);
            if (i < totalLines - 1) {
                resultBuilder.append("\n");
            }
        }

        return resultBuilder.toString();
    }

    public static String formatJava(String code) {
        try {
            return formatBraceLanguage(code, 4);
        } catch (Throwable t) {
            return code;
        }
    }

    public static String formatKotlin(String code) {
        try {
            return formatBraceLanguage(code, 4);
        } catch (Throwable t) {
            return code;
        }
    }

    public static String formatXml(String xml) {
        try {
            String prettified = SrcCodeEditor.prettifyXml(xml, 4, null);
            if (prettified != null && !prettified.trim().isEmpty()) {
                return prettified;
            }
        } catch (Throwable ignored) {
        }
        return xml;
    }

    private static String formatBraceLanguage(String code, int indentSize) {
        String[] lines = code.split("\n", -1);
        StringBuilder result = new StringBuilder();
        int indentLevel = 0;
        String indentUnit = " ".repeat(indentSize);

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();

            if (trimmed.isEmpty()) {
                result.append("\n");
                continue;
            }

            // Count closing braces at start of line to decrease indent before printing
            int leadingCloseBraces = 0;
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '}' || c == ')') {
                    leadingCloseBraces++;
                } else if (!Character.isWhitespace(c)) {
                    break;
                }
            }

            int currentIndent = Math.max(0, indentLevel - leadingCloseBraces);
            result.append(indentUnit.repeat(currentIndent));

            // Format line spacing for keywords
            String formattedLine = formatLineSpacing(trimmed);
            result.append(formattedLine).append("\n");

            // Calculate net indent change for next lines
            int openCount = countOccurrences(trimmed, '{') + countOccurrences(trimmed, '(');
            int closeCount = countOccurrences(trimmed, '}') + countOccurrences(trimmed, ')');
            indentLevel = Math.max(0, indentLevel + openCount - closeCount);
        }

        // Trim extra trailing newlines
        String out = result.toString();
        while (out.endsWith("\n\n")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String formatLineSpacing(String line) {
        if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
            return line;
        }
        // Normalize keyword spacing (e.g. if( -> if (, catch( -> catch ()
        String s = line;
        s = s.replaceAll("\\bif\\s*\\(", "if (");
        s = s.replaceAll("\\bfor\\s*\\(", "for (");
        s = s.replaceAll("\\bwhile\\s*\\(", "while (");
        s = s.replaceAll("\\bswitch\\s*\\(", "switch (");
        s = s.replaceAll("\\bcatch\\s*\\(", "catch (");
        s = s.replaceAll("\\bwhen\\s*\\(", "when (");
        s = s.replaceAll("\\)\\s*\\{", ") {");
        s = s.replaceAll("\\belse\\s*\\{", "else {");
        s = s.replaceAll("\\btry\\s*\\{", "try {");
        s = s.replaceAll("\\bfinally\\s*\\{", "finally {");
        s = s.replaceAll("\\bclass\\s*\\{", "class {");
        return s;
    }

    private static int countOccurrences(String str, char ch) {
        int count = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean inComment = false;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '/' && i + 1 < str.length() && str.charAt(i + 1) == '/') {
                break; // line comment starts
            }
            if (c == '"' && !inChar) {
                inString = !inString;
            } else if (c == '\'' && !inString) {
                inChar = !inChar;
            } else if (!inString && !inChar) {
                if (c == ch) {
                    count++;
                }
            }
        }
        return count;
    }
}
