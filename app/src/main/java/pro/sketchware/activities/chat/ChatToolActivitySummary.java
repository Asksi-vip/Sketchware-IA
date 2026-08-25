package pro.sketchware.activities.chat;

import java.util.List;
import java.util.Locale;

public final class ChatToolActivitySummary {
    private ChatToolActivitySummary() {
    }

    public static final class Summary {
        public int readCount;
        public int editCount;
        public int searchCount;
        public int listCount;
        public int runCount;
        public int otherCount;
        public int runningCount;
        public int errorCount;

        public int total() {
            return readCount + editCount + searchCount + listCount + runCount + otherCount;
        }

        public String compactLabel() {
            StringBuilder builder = new StringBuilder();
            appendPart(builder, "reads", readCount);
            appendPart(builder, "edits", editCount);
            appendPart(builder, "search", searchCount);
            appendPart(builder, "lists", listCount);
            appendPart(builder, "runs", runCount);
            appendPart(builder, "other", otherCount);
            if (builder.length() == 0) {
                builder.append("No tools yet");
            }
            if (runningCount > 0) {
                builder.append(" | running ").append(runningCount);
            }
            if (errorCount > 0) {
                builder.append(" | errors ").append(errorCount);
            }
            return builder.toString();
        }

        private void appendPart(StringBuilder builder, String label, int count) {
            if (count <= 0) {
                return;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(label).append(' ').append(count);
        }
    }

    public static Summary summarize(List<ChatMessage> messages) {
        Summary summary = new Summary();
        if (messages == null) {
            return summary;
        }
        for (ChatMessage message : messages) {
            if (message == null || !message.isTool()) {
                continue;
            }
            switch (groupKey(message.getToolName())) {
                case "read" -> summary.readCount++;
                case "edit" -> summary.editCount++;
                case "search" -> summary.searchCount++;
                case "list" -> summary.listCount++;
                case "run" -> summary.runCount++;
                default -> summary.otherCount++;
            }
            if (message.isToolRunning()) {
                summary.runningCount++;
            }
            if (message.isToolError() || message.isRejected()) {
                summary.errorCount++;
            }
        }
        return summary;
    }

    public static String groupLabel(String toolName) {
        return switch (groupKey(toolName)) {
            case "read" -> "Read";
            case "edit" -> "Edit";
            case "search" -> "Search";
            case "list" -> "List";
            case "run" -> "Run";
            default -> "Tool";
        };
    }

    private static String groupKey(String toolName) {
        String name = toolName == null ? "" : toolName.toLowerCase(Locale.US);
        if (name.contains("read") || name.contains("decrypt")) {
            return "read";
        }
        if (name.contains("write") || name.contains("edit") || name.contains("rewrite")
                || name.contains("create") || name.contains("delete") || name.contains("encrypt")) {
            return "edit";
        }
        if (name.contains("search") || name.contains("grep") || name.contains("find")) {
            return "search";
        }
        if (name.contains("list") || name.contains("glob") || name.contains("tree")) {
            return "list";
        }
        if (name.contains("run") || name.contains("terminal") || name.contains("command")) {
            return "run";
        }
        return "other";
    }
}
