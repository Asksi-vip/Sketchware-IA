package pro.sketchware.util;

import pro.sketchware.network.AiProviderService;

/**
 * Edits project files through the current AI Settings provider.
 */
public class SketchwareFileEditor {

    public static class EditResult {
        public final boolean success;
        public final String initialContent;
        public final String editedContent;
        public final String errorMessage;

        private EditResult(boolean success, String initialContent, String editedContent, String errorMessage) {
            this.success = success;
            this.initialContent = initialContent;
            this.editedContent = editedContent;
            this.errorMessage = errorMessage;
        }

        public static EditResult success(String initialContent, String editedContent) {
            return new EditResult(true, initialContent, editedContent, null);
        }

        public static EditResult error(String errorMessage) {
            return new EditResult(false, null, null, errorMessage);
        }

        public static EditResult error(String initialContent, String errorMessage) {
            return new EditResult(false, initialContent, null, errorMessage);
        }
    }

    public static EditResult editFile(String scId, String filePath, String instructions, String codeEdit) {
        try {
            String initialContent = SketchwareFileDecryptor.decryptFile(scId, filePath);
            if (initialContent == null || initialContent.isEmpty()) {
                return EditResult.error("Erro: arquivo nao encontrado ou vazio: " + filePath);
            }

            String editedContent = cleanEditedContent(AiProviderService.getInstance().sendTextMessage(
                    buildSystemPrompt(),
                    buildUserPrompt(filePath, initialContent, instructions, codeEdit)
            ));

            if (editedContent == null || editedContent.isEmpty()) {
                return EditResult.error(initialContent, "Erro: a IA nao retornou conteudo editado para: " + filePath);
            }

            boolean saved = SketchwareFileEncryptor.encryptAndSaveFile(scId, filePath, editedContent);
            if (!saved) {
                return EditResult.error(initialContent, "Erro: nao foi possivel salvar o arquivo modificado: " + filePath);
            }

            FileChangeTracker.trackChange(scId, filePath, initialContent, editedContent);
            return EditResult.success(initialContent, editedContent);
        } catch (Exception e) {
            e.printStackTrace();
            return EditResult.error("Erro ao modificar arquivo: " + e.getMessage());
        }
    }

    public static EditResult editFile(String scId, String filePath, String instructions) {
        return editFile(scId, filePath, instructions, "");
    }

    private static String buildSystemPrompt() {
        return "You are editing a Sketchware project file. "
                + "Return only the complete updated file content. "
                + "Do not wrap the result in Markdown. Do not explain the change.";
    }

    private static String buildUserPrompt(String filePath, String initialContent, String instructions, String codeEdit) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("File path:\n")
                .append(filePath == null ? "" : filePath)
                .append("\n\nInstructions:\n")
                .append(instructions == null ? "" : instructions)
                .append("\n\nCurrent file content:\n")
                .append(initialContent == null ? "" : initialContent);

        if (codeEdit != null && !codeEdit.trim().isEmpty()) {
            prompt.append("\n\nProposed edit context:\n")
                    .append(codeEdit);
        }

        prompt.append("\n\nReturn the full edited file content only.");
        return prompt.toString();
    }

    private static String cleanEditedContent(String response) {
        if (response == null) {
            return "";
        }
        String content = response.trim();
        if (content.startsWith("```")) {
            int firstLineEnd = content.indexOf('\n');
            int lastFence = content.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                content = content.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return content;
    }
}
