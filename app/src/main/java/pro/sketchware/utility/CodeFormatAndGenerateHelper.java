package pro.sketchware.utility;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for triggering Format Code and Generate Code options,
 * dialogs, and undoable text insertion in Sora CodeEditor.
 */
public class CodeFormatAndGenerateHelper {

    public static void formatCurrentFileOrSelection(Activity activity, CodeEditor editor, String languageName) {
        if (activity == null || editor == null || editor.getText() == null) {
            return;
        }
        Content content = editor.getText();
        String fullCode = content.toString();
        if (fullCode.trim().isEmpty()) {
            return;
        }

        if (editor.getCursor() != null && editor.getCursor().isSelected()) {
            int startLine = editor.getCursor().getLeftLine();
            int endLine = editor.getCursor().getRightLine();
            String formattedFull = CodeFormatterEngine.formatSelection(fullCode, startLine, endLine, languageName);
            replaceFullTextUndoable(editor, formattedFull);
            Toast.makeText(activity, "Selection Formatted", Toast.LENGTH_SHORT).show();
        } else {
            String formattedFull = CodeFormatterEngine.formatCode(fullCode, languageName);
            replaceFullTextUndoable(editor, formattedFull);
            Toast.makeText(activity, "Code Formatted", Toast.LENGTH_SHORT).show();
        }
    }

    public static void showGenerateCodeMenu(Activity activity, CodeEditor editor, String languageName) {
        if (activity == null || editor == null) return;

        CharSequence[] options = new CharSequence[]{
                "🏗️ Generate Constructor",
                "🔑 Generate Getter / Setter",
                "🔄 Generate Override Method",
                "⚡ Generate Custom Method",
                "🎯 Generate Listener",
                "🔗 Generate findViewById"
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Generate Code")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showGenerateConstructorDialog(activity, editor, languageName);
                            break;
                        case 1:
                            showGenerateGetterSetterDialog(activity, editor, languageName);
                            break;
                        case 2:
                            showGenerateOverrideDialog(activity, editor, languageName);
                            break;
                        case 3:
                            showGenerateCustomMethodDialog(activity, editor, languageName);
                            break;
                        case 4:
                            showGenerateListenerDialog(activity, editor, languageName);
                            break;
                        case 5:
                            showGenerateFindViewByIdDialog(activity, editor, languageName);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showGenerateConstructorDialog(Activity activity, CodeEditor editor, String languageName) {
        String code = editor.getText().toString();
        List<CodeGeneratorEngine.FieldModel> fields = CodeGeneratorEngine.parseFields(code, languageName);

        if (fields.isEmpty()) {
            String generated = CodeGeneratorEngine.generateConstructor(code, fields, languageName);
            insertCodeUndoable(editor, generated);
            Toast.makeText(activity, "Generated Constructor", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[fields.size()];
        boolean[] checked = new boolean[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            items[i] = fields.get(i).name + ": " + fields.get(i).type;
            checked[i] = true;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Fields for Constructor")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Generate", (dialog, which) -> {
                    List<CodeGeneratorEngine.FieldModel> selected = new ArrayList<>();
                    for (int i = 0; i < fields.size(); i++) {
                        if (checked[i]) selected.add(fields.get(i));
                    }
                    String generated = CodeGeneratorEngine.generateConstructor(code, selected, languageName);
                    insertCodeUndoable(editor, generated);
                    Toast.makeText(activity, "Generated Constructor", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showGenerateGetterSetterDialog(Activity activity, CodeEditor editor, String languageName) {
        String code = editor.getText().toString();
        List<CodeGeneratorEngine.FieldModel> fields = CodeGeneratorEngine.parseFields(code, languageName);

        if (fields.isEmpty()) {
            Toast.makeText(activity, "No fields found in class to generate Getters/Setters", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[fields.size()];
        boolean[] checked = new boolean[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            items[i] = fields.get(i).name + ": " + fields.get(i).type;
            checked[i] = true;
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Fields for Getter / Setter")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Both", (dialog, which) -> {
                    List<CodeGeneratorEngine.FieldModel> selected = new ArrayList<>();
                    for (int i = 0; i < fields.size(); i++) {
                        if (checked[i]) selected.add(fields.get(i));
                    }
                    String generated = CodeGeneratorEngine.generateGettersAndSetters(selected, true, true, languageName);
                    insertCodeUndoable(editor, generated);
                })
                .setNeutralButton("Getter Only", (dialog, which) -> {
                    List<CodeGeneratorEngine.FieldModel> selected = new ArrayList<>();
                    for (int i = 0; i < fields.size(); i++) {
                        if (checked[i]) selected.add(fields.get(i));
                    }
                    String generated = CodeGeneratorEngine.generateGettersAndSetters(selected, true, false, languageName);
                    insertCodeUndoable(editor, generated);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showGenerateOverrideDialog(Activity activity, CodeEditor editor, String languageName) {
        String[] methods = new String[]{
                "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
                "onClick", "onActivityResult", "onCreateOptionsMenu", "onOptionsItemSelected"
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Override Method")
                .setItems(methods, (dialog, which) -> {
                    String methodKey = methods[which];
                    String generated = CodeGeneratorEngine.generateOverrideMethod(methodKey, languageName);
                    insertCodeUndoable(editor, generated);
                    Toast.makeText(activity, "Generated " + methodKey, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showGenerateCustomMethodDialog(Activity activity, CodeEditor editor, String languageName) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText inputName = new EditText(activity);
        inputName.setHint("Method Name (e.g. showMessage)");
        layout.addView(inputName);

        final EditText inputReturn = new EditText(activity);
        inputReturn.setHint("Return Type (e.g. void / String)");
        inputReturn.setText("void");
        layout.addView(inputReturn);

        final EditText inputParams = new EditText(activity);
        inputParams.setHint("Parameters (e.g. String message)");
        layout.addView(inputParams);

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Generate Custom Method")
                .setView(layout)
                .setPositiveButton("Generate", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    if (name.isEmpty()) name = "myMethod";
                    String ret = inputReturn.getText().toString().trim();
                    String params = inputParams.getText().toString().trim();
                    String generated = CodeGeneratorEngine.generateCustomMethod(name, ret, params, "public", false, languageName);
                    insertCodeUndoable(editor, generated);
                    Toast.makeText(activity, "Generated " + name + "()", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showGenerateListenerDialog(Activity activity, CodeEditor editor, String languageName) {
        final EditText inputTarget = new EditText(activity);
        inputTarget.setHint("View Target Variable (e.g. button1)");
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(inputTarget);

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Generate OnClickListener")
                .setView(layout)
                .setPositiveButton("Generate", (dialog, which) -> {
                    String target = inputTarget.getText().toString().trim();
                    if (target.isEmpty()) target = "button1";
                    String generated = CodeGeneratorEngine.generateListener(target, "OnClickListener", languageName);
                    insertCodeUndoable(editor, generated);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showGenerateFindViewByIdDialog(Activity activity, CodeEditor editor, String languageName) {
        String code = editor.getText().toString();
        String generated = CodeGeneratorEngine.generateFindViewById(code, languageName);
        insertCodeUndoable(editor, generated);
        Toast.makeText(activity, "Generated findViewById", Toast.LENGTH_SHORT).show();
    }

    public static void insertCodeUndoable(CodeEditor editor, String textToInsert) {
        if (editor == null || editor.getText() == null || textToInsert == null || textToInsert.isEmpty()) {
            return;
        }
        Content content = editor.getText();

        // Calculate optimal insertion line
        int line = editor.getCursor().getLeftLine();
        if (line < 0 || line >= content.getLineCount()) {
            line = content.getLineCount() - 1;
        }

        // If line is at end of file, find class closing brace if possible
        if (line == content.getLineCount() - 1) {
            for (int i = content.getLineCount() - 1; i >= 0; i--) {
                if (content.getLine(i).toString().contains("}")) {
                    line = i;
                    break;
                }
            }
        }

        int col = content.getColumnCount(line);
        content.insert(line, col, "\n" + textToInsert);
        int newOffset = content.getCharIndex(line, col) + textToInsert.length() + 1;
        CharPosition endPos = content.getIndexer().getCharPosition(newOffset);
        editor.setSelection(endPos.line, endPos.column);
        editor.ensurePositionVisible(endPos.line, endPos.column);
    }

    public static void replaceFullTextUndoable(CodeEditor editor, String newText) {
        if (editor == null || editor.getText() == null || newText == null) {
            return;
        }
        Content content = editor.getText();
        int lastLine = Math.max(0, content.getLineCount() - 1);
        int lastCol = content.getColumnCount(lastLine);

        content.delete(0, 0, lastLine, lastCol);
        content.insert(0, 0, newText);
    }
}
