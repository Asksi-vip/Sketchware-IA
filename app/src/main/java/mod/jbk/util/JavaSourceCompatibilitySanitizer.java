package mod.jbk.util;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

public final class JavaSourceCompatibilitySanitizer {
    private static final String TAG = "JavaSourceSanitizer";
    private static final Pattern AMBIGUOUS_BIG_LARGE_ICON_NULL = Pattern.compile(
            "\\.bigLargeIcon\\s*\\(\\s*(?:null|nulo)\\s*\\)");

    private JavaSourceCompatibilitySanitizer() {
    }

    public static int sanitizeDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return 0;
        }
        return sanitizeRecursively(directory);
    }

    private static int sanitizeRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return 0;
            }
            int changed = 0;
            for (File child : children) {
                changed += sanitizeRecursively(child);
            }
            return changed;
        }
        if (!file.isFile() || !file.getName().endsWith(".java")) {
            return 0;
        }
        return sanitizeJavaFile(file);
    }

    private static int sanitizeJavaFile(File file) {
        String content = FileUtil.readFileIfExist(file.getAbsolutePath());
        Matcher matcher = AMBIGUOUS_BIG_LARGE_ICON_NULL.matcher(content);
        if (!matcher.find()) {
            return 0;
        }

        String updated = matcher.replaceAll(".bigLargeIcon((android.graphics.Bitmap) null)");
        FileUtil.writeFile(file.getAbsolutePath(), updated);
        LogUtil.d(TAG, "Normalized ambiguous bigLargeIcon(null) call in " + file.getAbsolutePath());
        return 1;
    }
}
