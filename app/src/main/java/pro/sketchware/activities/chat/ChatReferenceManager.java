package pro.sketchware.activities.chat;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

import pro.sketchware.util.ProjectPathResolver;

public final class ChatReferenceManager {
    private static final int MAX_REFERENCE_OPTIONS = 700;
    private static final int MAX_FILE_CONTEXT_CHARS = 4200;
    private static final int MAX_FOLDER_CONTEXT_CHARS = 3000;
    private static final int MAX_MULTIMODAL_IMAGES = 8;
    private static final int MAX_MULTIMODAL_IMAGE_EDGE = 1280;
    private static final int MULTIMODAL_IMAGE_JPEG_QUALITY = 82;

    private static final List<String> TEXT_EXTENSIONS = Arrays.asList(
            "java", "kt", "xml", "gradle", "json", "txt", "md", "css", "html", "js", "ts",
            "tsx", "jsx", "properties", "pro", "yaml", "yml", "toml", "svg"
    );

    private ChatReferenceManager() {
    }

    public static final class ReferenceOption {
        public final ChatReference reference;
        public final String displayText;
        public final String filterText;

        private ReferenceOption(ChatReference reference, String displayText, String filterText) {
            this.reference = reference;
            this.displayText = displayText;
            this.filterText = filterText;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    public static List<ReferenceOption> getProjectReferenceOptions(String scId) {
        List<ReferenceOption> options = new ArrayList<>();
        try {
            for (File root : ProjectPathResolver.getReadableRoots(scId)) {
                if (root == null || !root.exists()) {
                    continue;
                }
                collectOptions(root, root, options);
                if (options.size() >= MAX_REFERENCE_OPTIONS) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return options;
    }

    public static ChatReference fromImageUri(Context context, Uri uri) {
        String label = getDisplayName(context, uri);
        if (label.trim().isEmpty()) {
            label = "reference-image";
        }
        String mimeType = resolveMimeType(context, uri, label);
        long size = getSize(context, uri);
        return ChatReference.image(label, uri, mimeType, size);
    }

    /**
     * Builds the LLM {@code content} for a user turn (Void separates this from {@code displayContent}).
     */
    public static String buildLlmUserContent(String displayText, String contextPayload) {
        StringBuilder builder = new StringBuilder();
        if (ChatMessage.hasVisibleText(displayText)) {
            builder.append(displayText.trim());
        }
        if (ChatMessage.hasVisibleText(contextPayload)) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(contextPayload.trim());
        }
        return builder.toString();
    }

    public static String buildContextPayload(Context context, List<ChatReference> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<attached_context>\n");
        builder.append("The user selected these references before sending the message. Use them as first-class context.\n");

        for (ChatReference reference : references) {
            if (reference == null) {
                continue;
            }
            if (reference.getType() == ChatReference.TYPE_FILE) {
                appendFileReference(builder, reference);
            } else if (reference.getType() == ChatReference.TYPE_FOLDER) {
                appendFolderReference(builder, reference);
            } else if (reference.getType() == ChatReference.TYPE_CODE_SELECTION) {
                appendCodeSelectionReference(builder, reference);
            } else if (reference.getType() == ChatReference.TYPE_IMAGE) {
                appendImageReference(context, builder, reference);
            }
        }

        builder.append("</attached_context>");
        return builder.toString();
    }

    public static String summarizeReferences(List<ChatReference> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(references.get(i).mentionText());
        }
        return builder.toString();
    }

    public static List<ChatReference> getImageReferences(List<ChatReference> references) {
        List<ChatReference> images = new ArrayList<>();
        if (references == null) {
            return images;
        }
        for (ChatReference reference : references) {
            if (reference != null && reference.getType() == ChatReference.TYPE_IMAGE) {
                images.add(reference);
            }
        }
        return images;
    }

    public static JSONArray buildOpenAiImageContentParts(Context context, List<ChatReference> references) {
        JSONArray content = new JSONArray();
        for (EncodedImage image : encodeImages(context, references)) {
            try {
                JSONObject imageUrl = new JSONObject()
                        .put("url", "data:" + image.mediaType + ";base64," + image.base64)
                        .put("detail", "auto");
                content.put(new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", imageUrl));
            } catch (Exception ignored) {
            }
        }
        return content;
    }

    public static JSONArray buildAnthropicImageContentParts(Context context, List<ChatReference> references) {
        JSONArray content = new JSONArray();
        for (EncodedImage image : encodeImages(context, references)) {
            try {
                JSONObject source = new JSONObject()
                        .put("type", "base64")
                        .put("media_type", image.mediaType)
                        .put("data", image.base64);
                content.put(new JSONObject()
                        .put("type", "image")
                        .put("source", source));
            } catch (Exception ignored) {
            }
        }
        return content;
    }

    private static void collectOptions(File root, File item, List<ReferenceOption> options) {
        if (item == null || options.size() >= MAX_REFERENCE_OPTIONS) {
            return;
        }

        String label = relativeLabel(root, item);
        if (item.isDirectory()) {
            if (!DirectoryTreeService.shouldExcludeDirectory(item.getName())) {
                String display = "folder  " + label;
                options.add(new ReferenceOption(ChatReference.folder(label, item.getAbsolutePath()), display, display.toLowerCase(Locale.US)));
            }

            File[] children = item.listFiles();
            if (children == null) {
                return;
            }
            Arrays.sort(children, Comparator
                    .comparing((File file) -> !file.isDirectory())
                    .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File child : children) {
                if (options.size() >= MAX_REFERENCE_OPTIONS) {
                    break;
                }
                if (child.isDirectory() && DirectoryTreeService.shouldExcludeDirectory(child.getName())) {
                    continue;
                }
                collectOptions(root, child, options);
            }
            return;
        }

        String display = "file    " + label;
        options.add(new ReferenceOption(ChatReference.file(label, item.getAbsolutePath()), display, display.toLowerCase(Locale.US)));
    }

    private static String relativeLabel(File root, File item) {
        try {
            String relative = root.toPath().toAbsolutePath().normalize()
                    .relativize(item.toPath().toAbsolutePath().normalize())
                    .toString()
                    .replace(File.separatorChar, '/');
            return relative.isEmpty() ? root.getName() : relative;
        } catch (Exception ignored) {
            return item.getName();
        }
    }

    private static void appendCodeSelectionReference(StringBuilder builder, ChatReference reference) {
        File file = new File(reference.getPath());
        builder.append("\n<reference type=\"code_selection\">\n");
        builder.append("label: ").append(reference.getLabel()).append('\n');
        builder.append("path: ").append(reference.getPath()).append('\n');
        builder.append("language: ").append(reference.getLanguage()).append('\n');
        builder.append("range: ").append(reference.getStartLine()).append('-').append(reference.getEndLine()).append('\n');
        if (file.exists() && file.isFile() && isLikelyTextFile(file)) {
            builder.append("preview:\n");
            builder.append(readLineRangePreview(file, reference.getStartLine(), reference.getEndLine())).append('\n');
        } else {
            builder.append("preview: selection unavailable\n");
        }
        builder.append("</reference>\n");
    }

    private static String readLineRangePreview(File file, int startLine, int endLine) {
        if (startLine < 1) {
            startLine = 1;
        }
        if (endLine < startLine) {
            endLine = startLine;
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (lineNumber >= startLine && lineNumber <= endLine) {
                    builder.append(lineNumber).append("| ").append(line).append('\n');
                }
                if (lineNumber > endLine) {
                    break;
                }
                lineNumber++;
            }
        } catch (Exception ignored) {
            return "unavailable";
        }
        return trimToChars(builder.toString(), MAX_FILE_CONTEXT_CHARS);
    }

    private static void appendFileReference(StringBuilder builder, ChatReference reference) {
        File file = new File(reference.getPath());
        builder.append("\n<reference type=\"file\">\n");
        builder.append("label: ").append(reference.getLabel()).append('\n');
        builder.append("path: ").append(reference.getPath()).append('\n');
        if (file.exists() && file.isFile()) {
            builder.append("size: ").append(formatSize(file.length())).append('\n');
            if (isLikelyTextFile(file)) {
                builder.append("preview:\n");
                builder.append(readTextPreview(file, MAX_FILE_CONTEXT_CHARS)).append('\n');
            } else {
                builder.append("preview: binary or unsupported text preview\n");
            }
        } else {
            builder.append("preview: file not found\n");
        }
        builder.append("</reference>\n");
    }

    private static void appendFolderReference(StringBuilder builder, ChatReference reference) {
        File folder = new File(reference.getPath());
        builder.append("\n<reference type=\"folder\">\n");
        builder.append("label: ").append(reference.getLabel()).append('\n');
        builder.append("path: ").append(reference.getPath()).append('\n');
        try {
            if (folder.exists() && folder.isDirectory()) {
                builder.append(trimToChars(DirectoryTreeService.getDirectoryStrTool(folder), MAX_FOLDER_CONTEXT_CHARS)).append('\n');
            } else {
                builder.append("directory tree: folder not found\n");
            }
        } catch (Exception ignored) {
            builder.append("directory tree: unavailable\n");
        }
        builder.append("</reference>\n");
    }

    private static void appendImageReference(Context context, StringBuilder builder, ChatReference reference) {
        ImageInfo imageInfo = readImageInfo(context, reference.getUri());
        builder.append("\n<reference type=\"image\">\n");
        builder.append("label: ").append(reference.getLabel()).append('\n');
        builder.append("uri: ").append(reference.getPath()).append('\n');
        builder.append("mime_type: ").append(reference.getMimeType() == null ? "image/*" : reference.getMimeType()).append('\n');
        if (reference.getSizeBytes() > 0) {
            builder.append("size: ").append(formatSize(reference.getSizeBytes())).append('\n');
        }
        if (imageInfo.width > 0 && imageInfo.height > 0) {
            builder.append("dimensions: ").append(imageInfo.width).append("x").append(imageInfo.height).append('\n');
        }
        builder.append("note: selected as a visual reference from the chat attach button.\n");
        builder.append("</reference>\n");
    }

    private static boolean isLikelyTextFile(File file) {
        String extension = extensionOf(file.getName());
        return TEXT_EXTENSIONS.contains(extension);
    }

    private static String readTextPreview(File file, int maxChars) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1 && builder.length() < maxChars) {
                int count = Math.min(read, maxChars - builder.length());
                builder.append(buffer, 0, count);
            }
        } catch (Exception ignored) {
            return "unavailable";
        }
        if (builder.length() >= maxChars) {
            builder.append("\n...truncated...");
        }
        return builder.toString();
    }

    private static String getDisplayName(Context context, Uri uri) {
        if (context == null || uri == null) {
            return "";
        }
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    String name = cursor.getString(nameIndex);
                    if (name != null && !name.trim().isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String lastPath = uri.getLastPathSegment();
        return lastPath == null ? "" : lastPath;
    }

    private static long getSize(Context context, Uri uri) {
        if (context == null || uri == null) {
            return 0L;
        }
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static String resolveMimeType(Context context, Uri uri, String fallbackName) {
        try {
            String mime = context.getContentResolver().getType(uri);
            if (mime != null && !mime.trim().isEmpty()) {
                return mime;
            }
        } catch (Exception ignored) {
        }
        String extension = extensionOf(fallbackName);
        String fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return fromExtension == null ? "image/*" : fromExtension;
    }

    private static ImageInfo readImageInfo(Context context, @Nullable Uri uri) {
        if (context == null || uri == null) {
            return new ImageInfo(0, 0);
        }
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            return new ImageInfo(options.outWidth, options.outHeight);
        } catch (Exception ignored) {
            return new ImageInfo(0, 0);
        }
    }

    private static List<EncodedImage> encodeImages(Context context, List<ChatReference> references) {
        List<EncodedImage> encodedImages = new ArrayList<>();
        if (context == null || references == null) {
            return encodedImages;
        }
        for (ChatReference reference : references) {
            if (encodedImages.size() >= MAX_MULTIMODAL_IMAGES) {
                break;
            }
            if (reference == null || reference.getType() != ChatReference.TYPE_IMAGE || reference.getUri() == null) {
                continue;
            }
            EncodedImage encoded = encodeImage(context, reference.getUri());
            if (encoded != null) {
                encodedImages.add(encoded);
            }
        }
        return encodedImages;
    }

    @Nullable
    private static EncodedImage encodeImage(Context context, Uri uri) {
        Bitmap bitmap = decodeScaledBitmap(context, uri);
        if (bitmap == null) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, MULTIMODAL_IMAGE_JPEG_QUALITY, output)) {
                return null;
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            return new EncodedImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP));
        } catch (Exception ignored) {
            return null;
        } finally {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    @Nullable
    private static Bitmap decodeScaledBitmap(Context context, Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(inputStream, null, bounds);
        } catch (Exception ignored) {
            return null;
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateSampleSize(bounds, MAX_MULTIMODAL_IMAGE_EDGE);
        Bitmap decoded;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(inputStream, null, decodeOptions);
        } catch (Exception ignored) {
            return null;
        }
        if (decoded == null) {
            return null;
        }

        int width = decoded.getWidth();
        int height = decoded.getHeight();
        int longestEdge = Math.max(width, height);
        if (longestEdge <= MAX_MULTIMODAL_IMAGE_EDGE) {
            return decoded;
        }

        float scale = MAX_MULTIMODAL_IMAGE_EDGE / (float) longestEdge;
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, scaledWidth, scaledHeight, true);
        if (scaled != decoded && !decoded.isRecycled()) {
            decoded.recycle();
        }
        return scaled;
    }

    private static int calculateSampleSize(BitmapFactory.Options options, int maxEdge) {
        int sampleSize = 1;
        int longestEdge = Math.max(options.outWidth, options.outHeight);
        while (longestEdge / sampleSize > maxEdge) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    private static String trimToChars(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n...truncated...";
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format(Locale.US, "%.1f KiB", kib);
        }
        return String.format(Locale.US, "%.1f MiB", kib / 1024.0);
    }

    private static final class ImageInfo {
        final int width;
        final int height;

        ImageInfo(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class EncodedImage {
        final String mediaType;
        final String base64;

        EncodedImage(String mediaType, String base64) {
            this.mediaType = mediaType;
            this.base64 = base64;
        }
    }
}
