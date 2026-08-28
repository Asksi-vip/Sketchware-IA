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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.rosemoe.sora.widget.CodeEditor;

import java.io.File;
import java.util.List;

/**
 * ProblemsPanelDialog displays real-time and build diagnostics in a styled
 * Material Dialog panel with click-to-jump and Quick Fix integration.
 */
public class ProblemsPanelDialog {

    private static int currentDiagnosticIndex = -1;

    public static void showProblemsPanel(Activity activity, CodeEditor editor, File file, String scId) {
        if (activity == null || editor == null || editor.getText() == null) {
            return;
        }
        String content = editor.getText().toString();
        List<RealtimeDiagnosticEngine.DiagnosticItem> items = RealtimeDiagnosticEngine.analyzeFile(file, content, scId);

        if (items.isEmpty()) {
            Toast.makeText(activity, "No problems found in current file! ✨", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayAdapter<RealtimeDiagnosticEngine.DiagnosticItem> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                items
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);

                RealtimeDiagnosticEngine.DiagnosticItem item = getItem(position);
                if (item != null) {
                    String prefix = item.severity == RealtimeDiagnosticEngine.Severity.ERROR ? "❌ " :
                                   (item.severity == RealtimeDiagnosticEngine.Severity.WARNING ? "⚠️ " : "ℹ️ ");
                    String location = (item.file == null ? "file" : item.file.getName()) + " (Line " + (item.line + 1) + ":" + (item.column + 1) + ")";
                    String fixBadge = item.quickFixType != null ? "  💡 [Quick Fix]" : "";

                    text1.setText(prefix + location + fixBadge);
                    text2.setText(item.message);
                }
                return view;
            }
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Problems (" + items.size() + ")")
                .setAdapter(adapter, (dialog, which) -> {
                    RealtimeDiagnosticEngine.DiagnosticItem item = items.get(which);
                    jumpToDiagnostic(activity, editor, item);

                    if (item.quickFixType != null) {
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle("💡 Quick Fix Available")
                                .setMessage(item.message)
                                .setPositiveButton("Apply Quick Fix", (d2, w2) -> {
                                    QuickFixManager.applyQuickFix(activity, editor, item);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .setNeutralButton("Next Error", (dialog, which) -> navigateError(activity, editor, file, scId, true))
                .setNegativeButton("Close", null)
                .show();
    }

    public static void navigateError(Activity activity, CodeEditor editor, File file, String scId, boolean next) {
        if (activity == null || editor == null || editor.getText() == null) return;
        List<RealtimeDiagnosticEngine.DiagnosticItem> items = RealtimeDiagnosticEngine.analyzeFile(file, editor.getText().toString(), scId);
        if (items.isEmpty()) {
            Toast.makeText(activity, "No errors found!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (next) {
            currentDiagnosticIndex = (currentDiagnosticIndex + 1) % items.size();
        } else {
            currentDiagnosticIndex = (currentDiagnosticIndex - 1 + items.size()) % items.size();
        }

        RealtimeDiagnosticEngine.DiagnosticItem target = items.get(currentDiagnosticIndex);
        jumpToDiagnostic(activity, editor, target);
        Toast.makeText(activity, (target.severity == RealtimeDiagnosticEngine.Severity.ERROR ? "❌ " : "⚠️ ") + target.message, Toast.LENGTH_SHORT).show();
    }

    private static void jumpToDiagnostic(Activity activity, CodeEditor editor, RealtimeDiagnosticEngine.DiagnosticItem item) {
        if (editor == null || item == null) return;
        editor.post(() -> {
            int lineCount = editor.getLineCount();
            int safeLine = Math.max(0, Math.min(item.line, lineCount - 1));
            int lineLength = editor.getText().getColumnCount(safeLine);
            int safeColumn = Math.max(0, Math.min(item.column, lineLength));

            int tokLen = item.token.length();
            int endCol = Math.min(lineLength, safeColumn + Math.max(1, tokLen));

            if (endCol > safeColumn) {
                editor.setSelectionRegion(safeLine, safeColumn, safeLine, endCol);
            } else {
                editor.setSelection(safeLine, safeColumn, false);
            }
            editor.ensurePositionVisible(safeLine, safeColumn, true);
        });
    }
}
