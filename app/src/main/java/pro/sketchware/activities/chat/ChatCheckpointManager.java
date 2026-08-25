package pro.sketchware.activities.chat;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import pro.sketchware.util.FileChangeTracker;
import pro.sketchware.util.ProjectPathResolver;
import pro.sketchware.util.SketchwareFileEncryptor;

public class ChatCheckpointManager {
    private static final String ROOT_DIR = "chat_checkpoints";

    private final Context context;

    public static class CheckpointEntry {
        public final String id;
        public final String scId;
        public final String toolId;
        public final String toolName;
        public final String filePath;
        public final String beforeContent;
        public final boolean existedBefore;
        public final long createdAt;

        public CheckpointEntry(String id, String scId, String toolId, String toolName, String filePath,
                               String beforeContent, boolean existedBefore, long createdAt) {
            this.id = id;
            this.scId = scId;
            this.toolId = toolId;
            this.toolName = toolName;
            this.filePath = filePath;
            this.beforeContent = beforeContent;
            this.existedBefore = existedBefore;
            this.createdAt = createdAt;
        }

        public JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("scId", scId);
                object.put("toolId", toolId);
                object.put("toolName", toolName);
                object.put("filePath", filePath);
                object.put("beforeContent", beforeContent);
                object.put("existedBefore", existedBefore);
                object.put("createdAt", createdAt);
            } catch (Exception ignored) {
            }
            return object;
        }

        public ChatMessage toChatMessage() {
            ChatMessage message = new ChatMessage(
                    "Checkpoint: " + filePath,
                    ChatMessage.TYPE_CHECKPOINT,
                    createdAt,
                    "Checkpoint"
            );
            message.setCheckpointId(id);
            message.setCheckpointType("tool_edit");
            JSONObject snapshots = new JSONObject();
            try {
                JSONObject snapshot = new JSONObject();
                snapshot.put("toolId", toolId);
                snapshot.put("toolName", toolName);
                snapshot.put("filePath", filePath);
                snapshot.put("beforeContent", beforeContent);
                snapshot.put("existedBefore", existedBefore);
                snapshots.put(filePath, snapshot);
            } catch (Exception ignored) {
            }
            message.setCheckpointSnapshotsJson(snapshots.toString());
            return message;
        }

        @Nullable
        public static CheckpointEntry fromJson(String json) {
            try {
                JSONObject object = new JSONObject(json);
                return new CheckpointEntry(
                        object.optString("id"),
                        object.optString("scId"),
                        object.optString("toolId"),
                        object.optString("toolName"),
                        object.optString("filePath"),
                        object.optString("beforeContent"),
                        object.optBoolean("existedBefore", true),
                        object.optLong("createdAt")
                );
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static class RollbackResult {
        public final boolean success;
        public final String message;
        @Nullable
        public final CheckpointEntry entry;

        public RollbackResult(boolean success, String message, @Nullable CheckpointEntry entry) {
            this.success = success;
            this.message = message;
            this.entry = entry;
        }
    }

    public ChatCheckpointManager(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    public CheckpointEntry createCheckpoint(String scId, String toolId, String toolName, String filePath,
                                            String beforeContent, boolean existedBefore) {
        if (scId == null || scId.trim().isEmpty() || filePath == null || filePath.trim().isEmpty()) {
            return null;
        }

        long createdAt = System.currentTimeMillis();
        String id = "cp_" + createdAt + "_" + UUID.randomUUID().toString().replace("-", "");
        CheckpointEntry entry = new CheckpointEntry(
                id,
                scId,
                toolId,
                toolName,
                filePath,
                beforeContent == null ? "" : beforeContent,
                existedBefore,
                createdAt
        );

        saveEntry(entry);
        return entry;
    }

    private void saveEntry(CheckpointEntry entry) {
        try {
            File projectDir = getProjectDir(entry.scId);
            if (!projectDir.exists()) projectDir.mkdirs();
            File file = new File(projectDir, entry.id + ".json");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(entry.toJson().toString());
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasCheckpoint(String scId) {
        return getLatestCheckpoint(scId) != null;
    }

    @Nullable
    public CheckpointEntry getLatestCheckpoint(String scId) {
        File projectDir = getProjectDir(scId);
        File[] files = projectDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return null;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File file : files) {
            CheckpointEntry entry = readEntry(file);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    public RollbackResult rollbackLatestCheckpoint(String scId) {
        CheckpointEntry entry = getLatestCheckpoint(scId);
        if (entry == null) {
            return new RollbackResult(false, "Nenhum checkpoint disponivel para rollback.", null);
        }

        try {
            ProjectPathResolver.ResolvedPath resolvedPath = ProjectPathResolver.resolveForWrite(scId, entry.filePath);
            if (resolvedPath == null) {
                return new RollbackResult(false, "Nao foi possivel resolver o arquivo do checkpoint.", entry);
            }

            File file = resolvedPath.getFile();
            boolean restored;
            if (!entry.existedBefore) {
                restored = !file.exists() || file.delete();
            } else {
                restored = SketchwareFileEncryptor.encryptAndSaveFile(scId, entry.filePath, entry.beforeContent);
            }

            if (!restored) {
                return new RollbackResult(false, "Falha ao restaurar o arquivo do checkpoint.", entry);
            }

            // Sync with FileChangeTracker
            if (!entry.existedBefore) {
                FileChangeTracker.acceptChange(scId, entry.filePath);
            } else {
                FileChangeTracker.trackChange(scId, entry.filePath, entry.beforeContent, entry.beforeContent);
                FileChangeTracker.acceptChange(scId, entry.filePath);
            }

            deleteEntryFile(entry);
            return new RollbackResult(true, "Rollback aplicado em " + entry.filePath + ".", entry);
        } catch (Exception e) {
            return new RollbackResult(false, "Erro no rollback: " + e.getMessage(), entry);
        }
    }

    public RollbackResult rollbackLatestCheckpoint(String scId, List<ChatMessage> messages) {
        ChatMessage checkpoint = findLatestCheckpointMessage(messages);
        if (checkpoint == null) {
            return rollbackLatestCheckpoint(scId);
        }
        try {
            JSONObject snapshots = new JSONObject(checkpoint.getCheckpointSnapshotsJson());
            JSONArray names = snapshots.names();
            if (names == null || names.length() == 0) {
                return new RollbackResult(false, "Checkpoint sem snapshots para rollback.", null);
            }

            CheckpointEntry lastEntry = null;
            for (int i = 0; i < names.length(); i++) {
                String filePath = names.optString(i, "");
                JSONObject snapshot = snapshots.optJSONObject(filePath);
                if (snapshot == null) {
                    continue;
                }
                CheckpointEntry entry = new CheckpointEntry(
                        checkpoint.getCheckpointId(),
                        scId,
                        snapshot.optString("toolId", ""),
                        snapshot.optString("toolName", ""),
                        snapshot.optString("filePath", filePath),
                        snapshot.optString("beforeContent", ""),
                        snapshot.optBoolean("existedBefore", true),
                        checkpoint.getTimestamp()
                );
                RollbackResult result = restoreEntry(entry);
                if (!result.success) {
                    return result;
                }
                lastEntry = entry;
            }
            return new RollbackResult(true, "Rollback aplicado ao checkpoint.", lastEntry);
        } catch (Exception e) {
            return new RollbackResult(false, "Erro no rollback: " + e.getMessage(), null);
        }
    }

    @Nullable
    private ChatMessage findLatestCheckpointMessage(List<ChatMessage> messages) {
        for (int i = messages == null ? -1 : messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message != null
                    && message.isCheckpoint()
                    && ChatMessage.hasVisibleText(message.getCheckpointSnapshotsJson())) {
                return message;
            }
        }
        return null;
    }

    private RollbackResult restoreEntry(CheckpointEntry entry) {
        try {
            ProjectPathResolver.ResolvedPath resolvedPath = ProjectPathResolver.resolveForWrite(entry.scId, entry.filePath);
            if (resolvedPath == null) {
                return new RollbackResult(false, "Nao foi possivel resolver o arquivo do checkpoint.", entry);
            }

            File file = resolvedPath.getFile();
            boolean restored;
            if (!entry.existedBefore) {
                restored = !file.exists() || file.delete();
            } else {
                restored = SketchwareFileEncryptor.encryptAndSaveFile(entry.scId, entry.filePath, entry.beforeContent);
            }

            if (!restored) {
                return new RollbackResult(false, "Falha ao restaurar o arquivo do checkpoint.", entry);
            }

            // Sync with FileChangeTracker
            if (!entry.existedBefore) {
                FileChangeTracker.acceptChange(entry.scId, entry.filePath);
            } else {
                FileChangeTracker.trackChange(entry.scId, entry.filePath, entry.beforeContent, entry.beforeContent);
                FileChangeTracker.acceptChange(entry.scId, entry.filePath);
            }

            return new RollbackResult(true, "Rollback aplicado em " + entry.filePath + ".", entry);
        } catch (Exception e) {
            return new RollbackResult(false, "Erro no rollback: " + e.getMessage(), entry);
        }
    }

    private void deleteEntryFile(CheckpointEntry entry) {
        File file = new File(getProjectDir(entry.scId), entry.id + ".json");
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private File getProjectDir(String scId) {
        return new File(new File(context.getFilesDir(), ROOT_DIR), scId);
    }

    @Nullable
    private CheckpointEntry readEntry(File file) {
        String text = readText(file);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return CheckpointEntry.fromJson(text);
    }

    @Nullable
    private String readText(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

}
