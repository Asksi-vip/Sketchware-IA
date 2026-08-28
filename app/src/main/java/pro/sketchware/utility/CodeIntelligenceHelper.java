package pro.sketchware.utility;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * CodeIntelligenceHelper provides UI overlays and dialogs for Hover Information,
 * Parameter Hints, Signature Help, Breadcrumbs, and Go to Symbol.
 */
public class CodeIntelligenceHelper {

    public static void showHoverInfo(Activity activity, CodeEditor editor, File file, String languageName) {
        if (activity == null || editor == null) return;
        String symbol = CodeNavigationHelper.getSymbolAtCursor(editor);
        if (symbol.isEmpty()) {
            return;
        }

        CodeIntelligenceEngine.HoverInfo info = CodeIntelligenceEngine.getHoverInfo(symbol, editor.getText().toString(), file, languageName);
        if (info == null) return;

        new MaterialAlertDialogBuilder(activity)
                .setTitle("💡 Hover Info: " + info.title)
                .setMessage("Category: " + info.category + "\n" +
                             "Location: " + info.location + "\n\n" +
                             "Details:\n" + info.details)
                .setPositiveButton("OK", null)
                .show();
    }

    public static void showParameterHints(Activity activity, CodeEditor editor) {
        if (activity == null || editor == null || editor.getText() == null) return;
        int line = editor.getCursor().getLeftLine();
        int col = editor.getCursor().getLeftColumn();
        if (line < 0 || line >= editor.getLineCount()) return;

        CharSequence lineSeq = editor.getText().getLine(line);
        String prefix = lineSeq.subSequence(0, Math.min(col, lineSeq.length())).toString();

        CodeIntelligenceEngine.ParameterHint hint = CodeIntelligenceEngine.getParameterHint(prefix);
        if (hint == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Method: ").append(hint.methodName).append("(\n");
        for (int i = 0; i < hint.parameters.size(); i++) {
            if (i == hint.activeIndex) {
                sb.append("  👉 ").append(hint.parameters.get(i)).append("  (Active Parameter)\n");
            } else {
                sb.append("     ").append(hint.parameters.get(i)).append("\n");
            }
        }
        sb.append(")");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Signature Help: " + hint.methodName)
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    public static void showBreadcrumbs(Activity activity, CodeEditor editor) {
        if (activity == null || editor == null || editor.getText() == null) return;
        int line = editor.getCursor().getLeftLine();
        List<String> crumbs = CodeIntelligenceEngine.getBreadcrumbs(editor.getText().toString(), line);

        if (crumbs.isEmpty()) {
            crumbs = List.of("Global Scope");
        }

        StringBuilder path = new StringBuilder();
        for (int i = 0; i < crumbs.size(); i++) {
            path.append(crumbs.get(i));
            if (i < crumbs.size() - 1) path.append("  >  ");
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Breadcrumbs Scope")
                .setMessage("Current Scope:\n" + path.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    public static void showGoToSymbolDialog(Activity activity, CodeEditor editor, File projectRoot) {
        if (activity == null || editor == null) return;

        List<SymbolIndexEngine.SymbolDefinition> defs = SymbolIndexEngine.findDefinitions(projectRoot, "");
        if (defs.isEmpty()) {
            SymbolIndexEngine.indexProjectIfNeeded(projectRoot);
            defs = SymbolIndexEngine.findDefinitions(projectRoot, "");
        }

        List<SymbolIndexEngine.SymbolDefinition> allSymbols = new ArrayList<>(defs);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        for (SymbolIndexEngine.SymbolDefinition d : allSymbols) {
            adapter.add("[" + d.kind + "] " + d.name + " (" + d.file.getName() + ":" + (d.line + 1) + ")");
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("🔍 Search Symbol (" + allSymbols.size() + ")")
                .setAdapter(adapter, (dialog, which) -> {
                    SymbolIndexEngine.SymbolDefinition target = allSymbols.get(which);
                    CodeNavigationHelper.jumpToTarget(activity, target.file, target.line, target.column, target.name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
