package pro.sketchware.utility;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.rosemoe.sora.text.Content;

import io.github.rosemoe.sora.widget.CodeEditor;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.activities.studio.AndroidStudioProjectActivity;
import mod.hey.studios.code.SrcCodeEditor;

/**
 * CodeNavigationHelper manages "Go to Definition" and "Find References"
 * actions, popups, and line jumps inside Sketchware CodeEditor.
 */
public class CodeNavigationHelper {

    public static boolean hasSelection(CodeEditor editor) {
        return editor != null && editor.getCursor() != null && editor.getCursor().isSelected();
    }

    public static String getSymbolAtCursor(CodeEditor editor) {
        if (editor == null || editor.getText() == null) {
            return "";
        }
        if (hasSelection(editor)) {
            int leftLine = editor.getCursor().getLeftLine();
            int leftCol = editor.getCursor().getLeftColumn();
            int rightLine = editor.getCursor().getRightLine();
            int rightCol = editor.getCursor().getRightColumn();
            CharSequence lineSeq = editor.getText().getLine(leftLine);
            if (lineSeq != null && leftLine == rightLine) {
                int start = Math.max(0, Math.min(leftCol, lineSeq.length()));
                int end = Math.max(start, Math.min(rightCol, lineSeq.length()));
                String selected = lineSeq.subSequence(start, end).toString();
                if (!selected.trim().isEmpty()) {
                    return SymbolIndexEngine.sanitizeSymbol(selected);
                }
            }
        }
        int line = editor.getCursor().getLeftLine();
        int column = editor.getCursor().getLeftColumn();
        if (line < 0 || line >= editor.getLineCount()) {
            return "";
        }
        Content content = editor.getText();
        CharSequence lineSequence = content.getLine(line);
        if (lineSequence == null || lineSequence.length() == 0) {
            return "";
        }
        if (column > lineSequence.length()) {
            column = lineSequence.length();
        }

        int start = column;
        while (start > 0) {
            char c = lineSequence.charAt(start - 1);
            if (Character.isJavaIdentifierPart(c) || c == '@' || c == '.') {
                start--;
            } else {
                break;
            }
        }

        int end = column;
        while (end < lineSequence.length()) {
            char c = lineSequence.charAt(end);
            if (Character.isJavaIdentifierPart(c)) {
                end++;
            } else {
                break;
            }
        }

        if (start < end) {
            String raw = lineSequence.subSequence(start, end).toString();
            return SymbolIndexEngine.sanitizeSymbol(raw);
        }
        return "";
    }

    public static void showNavigationMenu(Activity activity, CodeEditor editor, File projectRoot) {
        if (activity == null || editor == null) return;
        String symbol = getSymbolAtCursor(editor);
        String formatLabel = hasSelection(editor) ? "🧹 Format Selection" : "🧹 Format Code";

        CharSequence[] options = symbol.isEmpty() ? new CharSequence[]{
                "💡 Hover Info",
                "💬 Signature Help",
                "🧭 Breadcrumbs Scope",
                "🔍 Go to Symbol",
                "🚨 Problems Panel",
                "➡️ Next Error",
                formatLabel,
                "⚡ Generate Code..."
        } : new CharSequence[]{
                "📍 Go to Definition (" + symbol + ")",
                "🔎 Find References (" + symbol + ")",
                "💡 Hover Info (" + symbol + ")",
                "💬 Signature Help",
                "🧭 Breadcrumbs Scope",
                "🔍 Go to Symbol",
                "🚨 Problems Panel",
                "➡️ Next Error",
                formatLabel,
                "⚡ Generate Code..."
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle(symbol.isEmpty() ? "Code Intelligence Tools" : "Code Intelligence: " + symbol)
                .setItems(options, (dialog, which) -> {
                    if (symbol.isEmpty()) {
                        if (which == 0) {
                            CodeIntelligenceHelper.showHoverInfo(activity, editor, projectRoot, null);
                        } else if (which == 1) {
                            CodeIntelligenceHelper.showParameterHints(activity, editor);
                        } else if (which == 2) {
                            CodeIntelligenceHelper.showBreadcrumbs(activity, editor);
                        } else if (which == 3) {
                            CodeIntelligenceHelper.showGoToSymbolDialog(activity, editor, projectRoot);
                        } else if (which == 4) {
                            ProblemsPanelDialog.showProblemsPanel(activity, editor, projectRoot, null);
                        } else if (which == 5) {
                            ProblemsPanelDialog.navigateError(activity, editor, projectRoot, null, true);
                        } else if (which == 6) {
                            CodeFormatAndGenerateHelper.formatCurrentFileOrSelection(activity, editor, null);
                        } else if (which == 7) {
                            CodeFormatAndGenerateHelper.showGenerateCodeMenu(activity, editor, null);
                        }
                    } else {
                        if (which == 0) {
                            goToDefinition(activity, projectRoot, symbol);
                        } else if (which == 1) {
                            findReferences(activity, projectRoot, symbol);
                        } else if (which == 2) {
                            CodeIntelligenceHelper.showHoverInfo(activity, editor, projectRoot, null);
                        } else if (which == 3) {
                            CodeIntelligenceHelper.showParameterHints(activity, editor);
                        } else if (which == 4) {
                            CodeIntelligenceHelper.showBreadcrumbs(activity, editor);
                        } else if (which == 5) {
                            CodeIntelligenceHelper.showGoToSymbolDialog(activity, editor, projectRoot);
                        } else if (which == 6) {
                            ProblemsPanelDialog.showProblemsPanel(activity, editor, projectRoot, null);
                        } else if (which == 7) {
                            ProblemsPanelDialog.navigateError(activity, editor, projectRoot, null, true);
                        } else if (which == 8) {
                            CodeFormatAndGenerateHelper.formatCurrentFileOrSelection(activity, editor, null);
                        } else if (which == 9) {
                            CodeFormatAndGenerateHelper.showGenerateCodeMenu(activity, editor, null);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void goToDefinition(Activity activity, File projectRoot, String rawSymbol) {
        String symbol = SymbolIndexEngine.sanitizeSymbol(rawSymbol);
        if (symbol.isEmpty()) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            List<SymbolIndexEngine.SymbolDefinition> defs = SymbolIndexEngine.findDefinitions(projectRoot, symbol);
            activity.runOnUiThread(() -> {
                if (defs.isEmpty()) {
                    Toast.makeText(activity, "Definition not found for '" + symbol + "'", Toast.LENGTH_SHORT).show();
                } else if (defs.size() == 1) {
                    SymbolIndexEngine.SymbolDefinition def = defs.get(0);
                    jumpToTarget(activity, def.file, def.line, def.column, def.name);
                } else {
                    showDefinitionsDialog(activity, symbol, defs);
                }
            });
        });
    }

    public static void findReferences(Activity activity, File projectRoot, String rawSymbol) {
        String symbol = SymbolIndexEngine.sanitizeSymbol(rawSymbol);
        if (symbol.isEmpty()) return;

        Toast.makeText(activity, "Searching references for '" + symbol + "'...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<SymbolIndexEngine.SymbolReference> refs = SymbolIndexEngine.findReferences(projectRoot, symbol);
            activity.runOnUiThread(() -> {
                if (refs.isEmpty()) {
                    Toast.makeText(activity, "No references found for '" + symbol + "'", Toast.LENGTH_SHORT).show();
                } else {
                    showReferencesDialog(activity, symbol, refs);
                }
            });
        });
    }

    private static void showDefinitionsDialog(Activity activity, String symbol, List<SymbolIndexEngine.SymbolDefinition> defs) {
        String[] items = new String[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            SymbolIndexEngine.SymbolDefinition d = defs.get(i);
            items[i] = "[" + d.kind + "] " + d.file.getName() + ":" + (d.line + 1) + "\n" + d.snippet;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Definitions for '" + symbol + "' (" + defs.size() + ")")
                .setItems(items, (dialog, which) -> {
                    SymbolIndexEngine.SymbolDefinition def = defs.get(which);
                    jumpToTarget(activity, def.file, def.line, def.column, def.name);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static void showReferencesDialog(Activity activity, String symbol, List<SymbolIndexEngine.SymbolReference> refs) {
        ArrayAdapter<SymbolIndexEngine.SymbolReference> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                refs
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);

                SymbolIndexEngine.SymbolReference ref = getItem(position);
                if (ref != null) {
                    text1.setText((ref.isDefinition ? "📍 [DEF] " : "📄 ") + ref.file.getName() + " (Line " + (ref.line + 1) + ")");
                    text2.setText(ref.snippet);
                }
                return view;
            }
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle("References for '" + symbol + "' (" + refs.size() + ")")
                .setAdapter(adapter, (dialog, which) -> {
                    SymbolIndexEngine.SymbolReference ref = refs.get(which);
                    jumpToTarget(activity, ref.file, ref.line, ref.column, ref.name);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    public static void jumpToTarget(Activity activity, File file, int line, int column, String symbolName) {
        if (activity == null || file == null) return;

        if (activity instanceof AndroidStudioProjectActivity studioActivity) {
            studioActivity.openFileAndJumpToPosition(file, line, column, symbolName);
        } else if (activity instanceof SrcCodeEditor srcEditor) {
            srcEditor.jumpToPositionInFile(file, line, column, symbolName);
        }
    }
}
