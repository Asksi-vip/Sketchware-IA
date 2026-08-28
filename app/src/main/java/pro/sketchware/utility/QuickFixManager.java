package pro.sketchware.utility;

import android.app.Activity;
import android.widget.Toast;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

import java.util.List;

/**
 * QuickFixManager executes safe, undoable code fixes for diagnostics
 * in Java, Kotlin, and XML files.
 */
public class QuickFixManager {

    public static boolean applyQuickFix(Activity activity, CodeEditor editor, RealtimeDiagnosticEngine.DiagnosticItem item) {
        if (activity == null || editor == null || editor.getText() == null || item == null) {
            return false;
        }
        if (item.quickFixType == null || item.quickFixType.isEmpty()) {
            Toast.makeText(activity, "No automatic Quick Fix available for this item", Toast.LENGTH_SHORT).show();
            return false;
        }

        Content content = editor.getText();
        switch (item.quickFixType) {
            case "ADD_IMPORT":
                return applyAddImportFix(activity, editor, item.quickFixPayload);
            case "ADD_SEMICOLON":
                return applyAddSemicolonFix(activity, editor, item.line);
            case "ADD_BRACKET":
                return applyAddBracketFix(activity, editor, item.line, item.quickFixPayload);
            default:
                Toast.makeText(activity, "Quick Fix suggestion: " + item.message, Toast.LENGTH_SHORT).show();
                return false;
        }
    }

    private static boolean applyAddImportFix(Activity activity, CodeEditor editor, String fullImport) {
        if (fullImport == null || fullImport.isEmpty()) return false;
        Content content = editor.getText();
        String fullText = content.toString();

        if (fullText.contains("import " + fullImport)) {
            Toast.makeText(activity, "Import already present", Toast.LENGTH_SHORT).show();
            return true;
        }

        // Find package statement line to insert import below
        int insertLine = 0;
        for (int i = 0; i < content.getLineCount(); i++) {
            String line = content.getLine(i).toString().trim();
            if (line.startsWith("package ")) {
                insertLine = i + 1;
                break;
            }
        }

        String importStatement = "import " + fullImport + ";\n";
        content.insert(insertLine, 0, importStatement);
        Toast.makeText(activity, "Added import: " + fullImport, Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean applyAddSemicolonFix(Activity activity, CodeEditor editor, int line) {
        Content content = editor.getText();
        if (line < 0 || line >= content.getLineCount()) return false;
        int col = content.getColumnCount(line);
        content.insert(line, col, ";");
        Toast.makeText(activity, "Added missing ';'", Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean applyAddBracketFix(Activity activity, CodeEditor editor, int line, String bracket) {
        Content content = editor.getText();
        if (line < 0 || line >= content.getLineCount()) {
            line = content.getLineCount() - 1;
        }
        int col = content.getColumnCount(line);
        content.insert(line, col, bracket == null ? "}" : bracket);
        Toast.makeText(activity, "Added '" + (bracket == null ? "}" : bracket) + "'", Toast.LENGTH_SHORT).show();
        return true;
    }
}
