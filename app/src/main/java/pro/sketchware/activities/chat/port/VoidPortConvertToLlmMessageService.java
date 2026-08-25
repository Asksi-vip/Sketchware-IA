package pro.sketchware.activities.chat.port;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.activities.chat.ChatMessage;

/**
 * Android port of browser/convertToLLMMessageService.ts primitives.
 */
public final class VoidPortConvertToLlmMessageService {
    public static final String EMPTY_MESSAGE = "(empty message)";

    private VoidPortConvertToLlmMessageService() {
    }

    public static final class SimpleMessage {
        public final String role;
        public final String content;
        public final String reasoning;
        public final String toolName;
        public final String toolArgs;
        public final String toolResult;
        public final String toolId;

        SimpleMessage(String role,
                      String content,
                      String reasoning,
                      String toolName,
                      String toolArgs,
                      String toolResult,
                      String toolId) {
            this.role = safe(role);
            this.content = safe(content);
            this.reasoning = safe(reasoning);
            this.toolName = safe(toolName);
            this.toolArgs = safe(toolArgs);
            this.toolResult = safe(toolResult);
            this.toolId = safe(toolId);
        }
    }

    public static List<SimpleMessage> toSimpleMessages(List<ChatMessage> messages) {
        List<SimpleMessage> simpleMessages = new ArrayList<>();
        if (messages == null) {
            return simpleMessages;
        }
        for (ChatMessage message : messages) {
            if (message == null
                    || message.isCheckpoint()
                    || message.isAwaitingUser()
                    || message.isInterruptedStreamingTool()) {
                continue;
            }
            if (message.isUser()) {
                simpleMessages.add(new SimpleMessage(
                        "user",
                        safe(message.getLlmContent()),
                        "",
                        "",
                        "",
                        "",
                        ""
                ));
            } else if (message.isBot()) {
                simpleMessages.add(new SimpleMessage(
                        "assistant",
                        safe(message.getDisplayContent()),
                        safe(message.getReasoning()),
                        "",
                        "",
                        "",
                        ""
                ));
            } else if (message.isTool()) {
                simpleMessages.add(new SimpleMessage(
                        "tool",
                        safe(message.getToolResult()),
                        "",
                        safe(message.getToolName()),
                        safe(message.getToolArgs()),
                        safe(message.getToolResult()),
                        stableToolId(message)
                ));
            }
        }
        return simpleMessages;
    }

    public static String nonEmptyText(String value) {
        String safeValue = safe(value).trim();
        return safeValue.isEmpty() ? EMPTY_MESSAGE : safeValue;
    }

    public static String trimToApproxTokens(String text, int maxTokens) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        int maxChars = Math.max(0, maxTokens * 4);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 32)).trim()
                + "\n...[trimmed for token budget]";
    }

    public static String buildAssistantContent(String content, String reasoning, boolean includeReasoning) {
        String safeContent = safe(content).trim();
        String safeReasoning = safe(reasoning).trim();
        if (includeReasoning && !safeReasoning.isEmpty()) {
            if (safeContent.isEmpty()) {
                return "<reasoning>\n" + safeReasoning + "\n</reasoning>";
            }
            return "<reasoning>\n" + safeReasoning + "\n</reasoning>\n\n" + safeContent;
        }
        if (!safeContent.isEmpty()) {
            return safeContent;
        }
        if (!safeReasoning.isEmpty()) {
            return safeReasoning;
        }
        return EMPTY_MESSAGE;
    }

    public static String buildXmlToolResult(String toolName, String toolResult) {
        String safeName = safe(toolName).trim();
        if (safeName.isEmpty()) {
            safeName = "tool";
        }
        return "<" + safeName + "_result>\n"
                + nonEmptyText(toolResult)
                + "\n</" + safeName + "_result>";
    }

    public static String stableToolId(ChatMessage message) {
        if (message == null) {
            return "call_" + System.currentTimeMillis();
        }
        String toolId = safe(message.getToolId()).trim();
        return toolId.isEmpty() ? "call_" + message.getTimestamp() : toolId;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
