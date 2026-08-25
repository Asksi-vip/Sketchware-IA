package pro.sketchware.ai.fix;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AiFixSessionStore {

    private static final String DIRECTORY_NAME = "ai_fix_sessions";

    public static String save(Context context, AiFixSession session) throws Exception {
        if (session.sessionId == null || session.sessionId.trim().isEmpty()) {
            session.sessionId = UUID.randomUUID().toString();
        }
        if (session.createdAt <= 0L) {
            session.createdAt = System.currentTimeMillis();
        }

        File directory = getDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Failed to create AI fix session directory.");
        }

        File file = getFile(context, session.sessionId);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(session.toJson().toString().getBytes(StandardCharsets.UTF_8));
        }
        return session.sessionId;
    }

    public static AiFixSession read(Context context, String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }

        File file = getFile(context, sessionId);
        if (!file.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return AiFixSession.fromJson(new JSONObject(builder.toString()));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void delete(Context context, String sessionId) {
        File file = getFile(context, sessionId);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static File getDirectory(Context context) {
        return new File(context.getCacheDir(), DIRECTORY_NAME);
    }

    private static File getFile(Context context, String sessionId) {
        return new File(getDirectory(context), sessionId + ".json");
    }
}
