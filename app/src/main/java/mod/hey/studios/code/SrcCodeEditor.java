package mod.hey.studios.code;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import a.a.a.Lx;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import pro.sketchware.R;
import pro.sketchware.activities.chat.port.VoidPortAiAutocompleteLanguage;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.CodeEditorHsBinding;
import pro.sketchware.utility.CodeFormatAndGenerateHelper;
import pro.sketchware.utility.CodeNavigationHelper;
import pro.sketchware.utility.ProblemsPanelDialog;
import pro.sketchware.utility.RealtimeDiagnosticEngine;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;
import pro.sketchware.utility.TranslationFunction;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;

import io.github.rosemoe.sora.text.ContentListener;

public class SrcCodeEditor extends BaseAppCompatActivity {
    public static final String FLAG_FROM_ANDROID_MANIFEST = "from_android_manifest";
    public static final List<Pair<String, Class<? extends EditorColorScheme>>> KNOWN_COLOR_SCHEMES = List.of(
            new Pair<>("Default", EditorColorScheme.class),
            new Pair<>("GitHub", SchemeGitHub.class),
            new Pair<>("Eclipse", SchemeEclipse.class),
            new Pair<>("Darcula", SchemeDarcula.class),
            new Pair<>("VS2019", SchemeVS2019.class),
            new Pair<>("NotepadXX", SchemeNotepadXX.class)
    );
    public static SharedPreferences pref;
    public static int languageId;
    private String beforeContent = "";
    private CodeEditorHsBinding binding;
    private boolean fromAndroidManifest;
    private String scId;
    private String activityName;

    // Real-time diagnostics debounce
    private final Handler diagnosticsHandler = new Handler(Looper.getMainLooper());
    private Runnable diagnosticsRunnable;
    private static final long DIAGNOSTICS_DELAY_MS = 1200;
    private String currentLanguageName = "java";
    private File currentProjectFile;

    public static void loadCESettings(Context c, CodeEditor ed, String prefix) {
        loadCESettings(c, ed, prefix, false);
    }

    public static void loadCESettings(Context c, CodeEditor ed, String prefix, boolean loadTheme) {
        pref = c.getSharedPreferences("hsce", Activity.MODE_PRIVATE);

        int text_size = pref.getInt(prefix + "_ts", 12);
        int theme = pref.getInt(prefix + "_theme", 3);
        boolean word_wrap = pref.getBoolean(prefix + "_ww", false);
        boolean auto_c = pref.getBoolean(prefix + "_ac", true);
        boolean auto_complete_symbol_pairs = pref.getBoolean(prefix + "_acsp", true);

        if (loadTheme) selectTheme(ed, theme);
        ed.setTextSize(text_size);
        ed.setWordwrap(word_wrap);
        ed.getProps().symbolPairAutoCompletion = auto_complete_symbol_pairs;
        ed.getComponent(EditorAutoCompletion.class).setEnabled(auto_c);
    }

    public static void selectTheme(CodeEditor ed, int which) {
        if (!(ed.getColorScheme() instanceof TextMateColorScheme)) {
            EditorColorScheme scheme = switch (which) {
                case 1 -> new SchemeGitHub();
                case 2 -> new SchemeEclipse();
                case 3 -> new SchemeDarcula();
                case 4 -> new SchemeVS2019();
                case 5 -> new SchemeNotepadXX();
                default -> new EditorColorScheme();
            };

            ed.setColorScheme(scheme);
        }
    }

    public static void selectLanguage(CodeEditor ed, int which) {
        String langName = switch (which) {
            case 1 -> "kotlin";
            case 2 -> "xml";
            default -> "java";
        };
        Language rawLang = switch (which) {
            case 1 -> CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN);
            case 2 -> CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML);
            default -> new JavaLanguage();
        };
        ed.setEditorLanguage(VoidPortAiAutocompleteLanguage.wrap(ed.getContext(), "", "", langName, rawLang));
        languageId = which;
    }

    public static String prettifyXml(String xml, int indentAmount, Intent extras) {
        if (xml == null || xml.trim().isEmpty()) return xml;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
            document.normalize();

            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate(
                    "//text()[normalize-space()='']", document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); ++i) {
                Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
                    String.valueOf(indentAmount));

            boolean omitXmlDecl = extras != null && extras.hasExtra("disableHeader");
            if (omitXmlDecl) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            String result = writer.toString();

            if (!omitXmlDecl && result.startsWith("<?xml")) {
                int endOfDecl = result.indexOf("?>");
                if (endOfDecl != -1 && endOfDecl + 2 < result.length()
                        && result.charAt(endOfDecl + 2) != '\n') {
                    result = result.substring(0, endOfDecl + 2) + "\n"
                            + result.substring(endOfDecl + 2);
                }
            }

            String[] lines = result.split("\n");
            StringBuilder formatted = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("<") && !trimmed.startsWith("<?")
                        && !trimmed.startsWith("<!") && trimmed.contains(" ")
                        && !trimmed.startsWith("</")) {

                    int indentBase = line.indexOf('<');
                    String baseIndent = " ".repeat(Math.max(0, indentBase));
                    String attrIndent = baseIndent + "    "; // 4-space attribute indent

                    boolean selfClosing = trimmed.endsWith("/>");
                    int tagEnd = trimmed.indexOf(' ');

                    if (tagEnd > 0) {
                        String tagName = trimmed.substring(1, tagEnd);
                        String attrPart = trimmed.substring(tagEnd + 1)
                                .replaceAll("/?>$", "").trim();
                        String[] attrs = attrPart.split("\\s+(?=[^=]+\\=)");

                        formatted.append(baseIndent).append("<").append(tagName).append("\n");
                        for (String attr : attrs) {
                            formatted.append(attrIndent).append(attr.trim()).append("\n");
                        }

                        int lastNewline = formatted.lastIndexOf("\n");
                        if (lastNewline != -1) {
                            formatted.delete(lastNewline, formatted.length());
                        }

                        formatted.append(selfClosing ? " />" : ">").append("\n");
                    } else {
                        formatted.append(line).append("\n");
                    }
                } else {
                    formatted.append(line).append("\n");
                }
            }

            return formatted.toString().trim();

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Adds a specified amount of tabs.
     */
    public static void a(StringBuilder code, int tabAmount) {
        for (int i = 0; i < tabAmount; ++i) {
            code.append('\t');
        }
    }

    public static void showSwitchThemeDialog(Activity activity, CodeEditor codeEditor, DialogInterface.OnClickListener listener) {
        EditorColorScheme currentScheme = codeEditor.getColorScheme();
        var knownColorSchemesProperlyOrdered = new ArrayList<>(KNOWN_COLOR_SCHEMES);
        Collections.reverse(knownColorSchemesProperlyOrdered);
        int selectedThemeIndex = knownColorSchemesProperlyOrdered.stream()
                .filter(pair -> pair.second.equals(currentScheme.getClass()))
                .map(KNOWN_COLOR_SCHEMES::indexOf)
                .findFirst()
                .orElse(-1);
        String[] themeItems = KNOWN_COLOR_SCHEMES.stream()
                .map(pair -> pair.first)
                .toArray(String[]::new);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themeItems, selectedThemeIndex, listener)
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    public static void showSwitchLanguageDialog(Activity activity, CodeEditor codeEditor, DialogInterface.OnClickListener listener) {
        CharSequence[] languagesList = {
                "Java",
                "Kotlin",
                "XML"
        };

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Language")
                .setSingleChoiceItems(languagesList, languageId, listener)
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = CodeEditorHsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fromAndroidManifest = getIntent().getBooleanExtra(FLAG_FROM_ANDROID_MANIFEST, false);
        String title = getIntent().getStringExtra("title");
        scId = getIntent().getStringExtra("sc_id");
        activityName = getIntent().getStringExtra("activity_name");

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(16);

        if (fromAndroidManifest) {
            String filePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
            if (FileUtil.isExistFile(filePath)) {
                ArrayList<HashMap<String, Object>> arrayList = getGson()
                        .fromJson(FileUtil.readFile(filePath), Helper.TYPE_MAP_LIST);
                for (int i = 0; i < arrayList.size(); i++) {
                    if (arrayList.get(i).get("name").equals(activityName)) {
                        beforeContent = (String) arrayList.get(i).get("value");
                    }
                }
            }
        }

        if (!fromAndroidManifest)
            beforeContent = FileUtil.readFile(getIntent().getStringExtra("content"));
        binding.editor.setText(beforeContent);

        String languageName = "java";
        if (title.endsWith(".java")) {
            languageId = 0;
            languageName = "java";
        } else if (title.endsWith(".kt")) {
            binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
            languageId = 1;
            languageName = "kotlin";
        } else if (title.endsWith(".xml")) {
            if (ThemeUtils.isDarkThemeEnabled(getApplicationContext())) {
                binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
            } else {
                binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_GITHUB));
            }
            languageId = 2;
            languageName = "xml";
        }
        currentLanguageName = languageName;
        currentProjectFile = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId);
        applyLanguageWithAutocomplete(languageName);
        loadCESettings(this, binding.editor, "act", true);
        setupRealtimeDiagnostics();
        loadToolbar();

        binding.editor.setOnLongClickListener(v -> {
            File projectRoot = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId);
            CodeNavigationHelper.showNavigationMenu(this, binding.editor, projectRoot);
            return true;
        });

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    public void jumpToPositionInFile(File file, int line, int column, String symbolName) {
        CodeEditor editor = binding.editor;
        if (editor != null) {
            editor.post(() -> {
                int safeLine = Math.max(0, Math.min(line, editor.getLineCount() - 1));
                int lineLength = editor.getText().getColumnCount(safeLine);
                int safeColumn = Math.max(0, Math.min(column, lineLength));
                int symLen = symbolName == null ? 0 : symbolName.length();
                int endColumn = Math.min(lineLength, safeColumn + symLen);

                if (symLen > 0 && endColumn > safeColumn) {
                    editor.setSelectionRegion(safeLine, safeColumn, safeLine, endColumn);
                } else {
                    editor.setSelection(safeLine, safeColumn, false);
                }
                editor.ensurePositionVisible(safeLine, safeColumn, true);
            });
        }
    }

    public void save() {
        beforeContent = binding.editor.getText().toString();

        if (fromAndroidManifest) {
            String filePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
            if (FileUtil.isExistFile(filePath)) {
                ArrayList<HashMap<String, Object>> activitiesComponents = getGson()
                        .fromJson(FileUtil.readFile(filePath), Helper.TYPE_MAP_LIST);
                for (int i = 0; i < activitiesComponents.size(); i++) {
                    if (activitiesComponents.get(i).get("name").equals(activityName)) {
                        activitiesComponents.get(i).put("value", beforeContent);
                        FileUtil.writeFile(filePath, getGson().toJson(activitiesComponents));
                        SketchwareUtil.toast("Saved");
                        return;
                    }
                }
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", activityName);
                map.put("value", beforeContent);
                activitiesComponents.add(map);
                FileUtil.writeFile(filePath, getGson().toJson(activitiesComponents));
            } else {
                ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", activityName);
                map.put("value", beforeContent);
                arrayList.add(map);
                FileUtil.writeFile(filePath, getGson().toJson(arrayList));
            }
        } else FileUtil.writeFile(getIntent().getStringExtra("content"), beforeContent);

        SketchwareUtil.toast("Saved");
    }

    @Override
    public void onBackPressed() {
        if (beforeContent.equals(binding.editor.getText().toString())) {
            super.onBackPressed();
        } else {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setIcon(R.drawable.ic_warning_96dp);
            dialog.setTitle(Helper.getResString(R.string.common_word_warning));
            dialog.setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message));

            dialog.setPositiveButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
                v.dismiss();
                finish();
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
            dialog.show();
        }
    }

    private void loadToolbar() {
        {
            String title = getIntent().getStringExtra("title");
            binding.toolbar.setTitle(title);
            SharedPreferences local_pref = getSharedPreferences("hsce", Activity.MODE_PRIVATE);
            Menu toolbarMenu = binding.toolbar.getMenu();
            toolbarMenu.clear();
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Undo").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_undo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Redo").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_redo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Save").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            if (isFileInLayoutFolder() && getIntent().hasExtra("sc_id")) {
                toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Layout Preview");
            }
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Find & Replace");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Word wrap").setCheckable(true).setChecked(local_pref.getBoolean("act_ww", false));
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Pretty print");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Generate code");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Problems");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Select language");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Select theme");
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Auto complete").setCheckable(true).setChecked(local_pref.getBoolean("act_ac", true));
            toolbarMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Auto complete symbol pair").setCheckable(true).setChecked(local_pref.getBoolean("act_acsp", true));

            binding.toolbar.setOnMenuItemClickListener(item -> {
                String title1 = item.getTitle().toString();
                switch (title1) {
                    case "Undo":
                        binding.editor.undo();
                        break;

                    case "Redo":
                        binding.editor.redo();
                        break;

                    case "Save":
                        save();
                        break;

                    case "Pretty print":
                    case "Format code":
                        CodeFormatAndGenerateHelper.formatCurrentFileOrSelection(this, binding.editor, null);
                        break;

                    case "Generate code":
                        CodeFormatAndGenerateHelper.showGenerateCodeMenu(this, binding.editor, null);
                        break;

                    case "Problems":
                        File projectRoot = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId);
                        ProblemsPanelDialog.showProblemsPanel(this, binding.editor, projectRoot, scId);
                        break;

                    case "Select language":
                        showSwitchLanguageDialog(this, binding.editor, (dialog, which) -> {
                            selectLanguage(binding.editor, which);
                            installVoidAiAutocomplete(languageNameFromIndex(which));
                            dialog.dismiss();
                        });
                        break;

                    case "Find & Replace":
                        binding.editor.getSearcher().stopSearch();
                        binding.editor.beginSearchMode();
                        break;

                    case "Select theme":
                        showSwitchThemeDialog(this, binding.editor, (dialog, which) -> {
                            selectTheme(binding.editor, which);
                            pref.edit().putInt("act_theme", which).apply();
                            dialog.dismiss();
                        });
                        break;

                    case "Word wrap":
                        item.setChecked(!item.isChecked());
                        binding.editor.setWordwrap(item.isChecked());

                        pref.edit().putBoolean("act_ww", item.isChecked()).apply();
                        break;

                    case "Auto complete symbol pair":
                        item.setChecked(!item.isChecked());
                        binding.editor.getProps().symbolPairAutoCompletion = item.isChecked();

                        pref.edit().putBoolean("act_acsp", item.isChecked()).apply();
                        break;

                    case "Auto complete":
                        item.setChecked(!item.isChecked());

                        binding.editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                        pref.edit().putBoolean("act_ac", item.isChecked()).apply();
                        break;

                    case "Layout Preview":
                        toLayoutPreview();
                        break;

                    default:
                        return false;
                }
                return true;
            });
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        pref.edit().putInt("act_ts", (int) (binding.editor.getTextSizePx() / scaledDensity)).apply();
    }

    private boolean isFileInLayoutFolder() {
        String content = getIntent().getStringExtra("content");
        if (content != null) {
            File file = new File(content);
            if (content.contains("/resource/layout/")) {
                String layoutFolder = file.getParent();
                return layoutFolder != null && layoutFolder.endsWith("/resource/layout");
            }
        }
        return false;
    }

    /** Sets the raw Language and immediately wraps it with VoidPortAiAutocompleteLanguage */
    private void applyLanguageWithAutocomplete(String langName) {
        io.github.rosemoe.sora.lang.Language rawLang = switch (langName) {
            case "kotlin" -> CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN);
            case "xml"    -> CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML);
            default       -> new JavaLanguage();
        };
        binding.editor.setEditorLanguage(
            VoidPortAiAutocompleteLanguage.wrap(
                this, scId,
                getIntent().getStringExtra("content"),
                langName, rawLang
            )
        );
    }

    /** Installs a debounced ContentListener to run diagnostics after typing stops */
    private void setupRealtimeDiagnostics() {
        binding.editor.getText().addContentListener(new ContentListener() {
            @Override
            public void beforeReplace(io.github.rosemoe.sora.text.Content content) {}

            @Override
            public void afterInsert(io.github.rosemoe.sora.text.Content content,
                                    int startLine, int startColumn,
                                    int endLine, int endColumn,
                                    CharSequence insertedContent) {
                scheduleDiagnostics();
            }

            @Override
            public void afterDelete(io.github.rosemoe.sora.text.Content content,
                                    int startLine, int startColumn,
                                    int endLine, int endColumn,
                                    CharSequence deletedContent) {
                scheduleDiagnostics();
            }
        });
    }

    private void scheduleDiagnostics() {
        if (diagnosticsRunnable != null) {
            diagnosticsHandler.removeCallbacks(diagnosticsRunnable);
        }
        diagnosticsRunnable = () -> {
            if (binding == null || binding.editor == null) return;
            String code = binding.editor.getText().toString();
            String filePath = getIntent().getStringExtra("content");
            File file = (filePath != null) ? new File(filePath) : null;
            java.util.List<RealtimeDiagnosticEngine.DiagnosticItem> diagnostics =
                    RealtimeDiagnosticEngine.analyzeFile(file, code, scId);
            showInlineErrorBadge(diagnostics);
        };
        diagnosticsHandler.postDelayed(diagnosticsRunnable, DIAGNOSTICS_DELAY_MS);
    }

    /** Show first error/warning as a non-blocking Toast badge (lightweight indicator) */
    private void showInlineErrorBadge(java.util.List<RealtimeDiagnosticEngine.DiagnosticItem> items) {
        if (items == null || items.isEmpty()) return;
        long errors   = items.stream().filter(d -> d.severity == RealtimeDiagnosticEngine.Severity.ERROR).count();
        long warnings = items.stream().filter(d -> d.severity == RealtimeDiagnosticEngine.Severity.WARNING).count();
        String badgeText = "";
        if (errors > 0)   badgeText += "❌ " + errors + " error" + (errors > 1 ? "s" : "");
        if (warnings > 0) badgeText += (badgeText.isEmpty() ? "" : "  ") + "⚠️ " + warnings + " warning" + (warnings > 1 ? "s" : "");
        if (!badgeText.isEmpty()) {
            android.widget.Toast.makeText(this, badgeText + "  (tap Problems menu)", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void installVoidAiAutocomplete(String languageName) {
        Language language = binding.editor.getEditorLanguage();
        if (language == null) {
            return;
        }
        binding.editor.setEditorLanguage(VoidPortAiAutocompleteLanguage.wrap(
                this,
                scId,
                getIntent().getStringExtra("content"),
                languageName,
                language
        ));
    }

    private String languageNameFromIndex(int which) {
        return switch (which) {
            case 1 -> "kotlin";
            case 2 -> "xml";
            default -> "java";
        };
    }

    private void toLayoutPreview() {
        Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("xml", binding.editor.getText().toString());
        startActivity(intent);
    }
}
