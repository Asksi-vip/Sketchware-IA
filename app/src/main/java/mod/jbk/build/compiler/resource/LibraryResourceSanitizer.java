package mod.jbk.build.compiler.resource;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.jbk.util.LogUtil;
import pro.sketchware.utility.FileUtil;

public final class LibraryResourceSanitizer {
    private static final String TAG = "LibraryResourceSanitizer";
    private static final Pattern TRACK_CORNER_RADIUS_PERCENT = Pattern.compile(
            "(<item\\s+name\\s*=\\s*\"trackCornerRadius\"\\s*>\\s*)50%(\\s*</item>)");

    private LibraryResourceSanitizer() {
    }

    public static int sanitizeResourceDirectory(File resourceDirectory) {
        if (resourceDirectory == null || !resourceDirectory.exists()) {
            return 0;
        }
        return sanitizeRecursively(resourceDirectory);
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

        if (!shouldSanitizeValuesFile(file)) {
            return 0;
        }
        return sanitizeValuesFile(file);
    }

    private static boolean shouldSanitizeValuesFile(File file) {
        File parent = file.getParentFile();
        return file.isFile()
                && file.getName().endsWith(".xml")
                && parent != null
                && parent.getName().startsWith("values");
    }

    private static int sanitizeValuesFile(File file) {
        String content = FileUtil.readFileIfExist(file.getAbsolutePath());
        if (!content.contains("trackCornerRadius") || !content.contains("50%")) {
            return 0;
        }

        Matcher matcher = TRACK_CORNER_RADIUS_PERCENT.matcher(content);
        StringBuffer sanitized = new StringBuffer();
        int changes = 0;
        while (matcher.find()) {
            changes++;
            // Material 1.13.0 uses a fraction here, but this compiler path expects a dimension.
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(matcher.group(1) + "2dp" + matcher.group(2)));
        }
        matcher.appendTail(sanitized);

        if (changes > 0) {
            FileUtil.writeFile(file.getAbsolutePath(), sanitized.toString());
            LogUtil.d(TAG, "Normalized " + changes + " trackCornerRadius percentage value(s) in " + file.getAbsolutePath());
        }
        return changes;
    }
}
