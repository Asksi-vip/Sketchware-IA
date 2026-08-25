package pro.sketchware.ai.fix;

import android.content.Context;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.Fx;
import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.network.AiProviderService;

public class AiFixSupport {

    private static final Pattern JAVA_ERROR_PATTERN = Pattern.compile("([A-Za-z0-9_]+\\.java):(\\d+):\\s*error:\\s*([^\\r\\n]+)");
    private static final Pattern GENERIC_JAVA_FILE_PATTERN = Pattern.compile("([A-Za-z0-9_]+\\.java)");
    private static final Pattern ECLIPSE_ERROR_PATTERN = Pattern.compile("([A-Za-z0-9_]+\\.java) \\(at line (\\d+)\\)");

    public static AiFixSession buildSession(Context context, String scId, String rawLog) {
        AiFixSession session = new AiFixSession();
        session.scId = scId;
        session.rawLog = rawLog == null ? "" : rawLog;

        ParsedCompileError parsedCompileError = parseCompileError(rawLog);
        session.errorFileName = parsedCompileError.fileName;
        session.errorLine = parsedCompileError.lineNumber;
        session.errorMessage = parsedCompileError.message;

        if (scId == null || scId.trim().isEmpty() || parsedCompileError.fileName.isEmpty() || !parsedCompileError.fileName.endsWith(".java")) {
            return session;
        }

        try {
            ProjectFileBean projectFileBean = findProjectFile(scId, parsedCompileError.fileName);
            if (projectFileBean == null) {
                return session;
            }

            yq sourceGenerator = new yq(context, scId);
            sourceGenerator.a(jC.c(scId), jC.b(scId), jC.a(scId));
            String fullSource = sourceGenerator.getFileSrc(projectFileBean.getJavaName(), jC.b(scId), jC.a(scId), jC.c(scId));
            if (fullSource == null || fullSource.trim().isEmpty()) {
                return session;
            }

            session.targetJavaSource = fullSource;

            HashMap<String, ArrayList<BlockBean>> logicMap = jC.a(scId).b(projectFileBean.getJavaName());
            ArrayList<CandidateEvent> candidateEvents = buildCandidateEvents(scId, projectFileBean);
            CandidateMatch bestMatch = null;
            boolean isViewBindingEnabled = new ProjectSettings(scId)
                    .getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false")
                    .equals("true");

            for (CandidateEvent candidateEvent : candidateEvents) {
                ArrayList<BlockBean> blocks = logicMap.get(candidateEvent.logicKey);
                if ((blocks == null || blocks.isEmpty()) && candidateEvent.alternateLogicKey != null) {
                    blocks = logicMap.get(candidateEvent.alternateLogicKey);
                }
                if (blocks == null || blocks.isEmpty()) {
                    continue;
                }

                String eventCode = new Fx(
                        projectFileBean.getActivityName(),
                        sourceGenerator.N,
                        blocks,
                        isViewBindingEnabled
                ).a();

                if (eventCode == null || eventCode.trim().isEmpty()) {
                    continue;
                }

                ArrayList<int[]> occurrences = findOccurrences(fullSource, eventCode);
                if (occurrences.isEmpty()) {
                    continue;
                }

                for (int[] occurrence : occurrences) {
                    int startLine = lineOfIndex(fullSource, occurrence[0]);
                    int endLine = lineOfIndex(fullSource, occurrence[1]);
                    int score = scoreCandidate(candidateEvent, parsedCompileError, startLine, endLine);

                    if (bestMatch == null || score > bestMatch.score) {
                        bestMatch = new CandidateMatch(candidateEvent, score);
                    }
                }
            }

            if (bestMatch != null) {
                session.targetJavaName = projectFileBean.getJavaName();
                session.targetId = bestMatch.candidateEvent.targetId;
                session.targetEventName = bestMatch.candidateEvent.eventName;
                session.targetEventText = bestMatch.candidateEvent.eventText;
            }
        } catch (Exception ignored) {
        }

        return session;
    }

    public static AiFixSuggestion analyzeFix(
            String scId,
            String targetId,
            String eventName,
            String rawLog,
            String errorMessage,
            String generatedCode,
            String fullJavaSource,
            ArrayList<BlockBean> blocks
    ) throws IOException {
        String prompt = buildPrompt(scId, targetId, eventName, rawLog, errorMessage, generatedCode, fullJavaSource, blocks);
        String response = AiProviderService.getInstance().sendTextMessage(
                "You fix Sketchware generated Java compile errors. Return only valid JSON. No markdown.",
                prompt
        );
        return AiFixSuggestion.fromResponse(response);
    }

    public static String buildExplainPrompt(AiFixSession session) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following Android compilation failure.\n");
        prompt.append("Important rules:\n");
        prompt.append("- Keep the final answer short, with a maximum of 300 characters.\n");
        prompt.append("- The compile log may show only the first reported error.\n");
        prompt.append("- Inspect the full generated Java file when it is provided, not only the log line.\n");
        prompt.append("- Identify the root cause first, then list other likely errors that would remain after the first fix.\n");
        prompt.append("- Clearly separate root-cause errors from cascade/secondary errors.\n");
        prompt.append("- Explain the safest order to fix the problems so the whole sequence is resolved, not just the first reported error.\n");
        prompt.append("- If the issue seems outside the provided Java file, say that explicitly.\n\n");
        prompt.append("Primary error message: ").append(session.errorMessage == null ? "" : session.errorMessage).append("\n");
        prompt.append("Compile log:\n").append(session.rawLog == null ? "" : session.rawLog).append("\n\n");

        if (session.targetJavaName != null && !session.targetJavaName.trim().isEmpty()
                && session.targetJavaSource != null && !session.targetJavaSource.trim().isEmpty()) {
            prompt.append("Generated Java file related to the compile error: ").append(session.targetJavaName).append("\n");
            prompt.append("You must inspect this whole file for additional issues related to the failure.\n");
            prompt.append("Full generated Java:\n").append(session.targetJavaSource).append("\n\n");
        }

        prompt.append("Respond in clear Brazilian Portuguese with:\n");
        prompt.append("1. Root cause\n");
        prompt.append("2. Other likely errors in sequence\n");
        prompt.append("3. Recommended fix order\n");
        prompt.append("4. Example correction when useful\n");
        return prompt.toString();
    }

    private static String buildPrompt(
            String scId,
            String targetId,
            String eventName,
            String rawLog,
            String errorMessage,
            String generatedCode,
            String fullJavaSource,
            ArrayList<BlockBean> blocks
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are fixing Sketchware logic blocks for an Android project.\n");
        prompt.append("Return ONLY valid JSON. Do not use markdown fences.\n");
        prompt.append("JSON schema:\n");
        prompt.append("{");
        prompt.append("\"summary\":\"...\",");
        prompt.append("\"explanation\":\"...\",");
        prompt.append("\"confidence\":0.0,");
        prompt.append("\"can_auto_apply\":true,");
        prompt.append("\"operations\":[");
        prompt.append("{\"type\":\"update_parameter\",\"block_id\":\"123\",\"parameter_index\":0,\"new_value\":\"value\"},");
        prompt.append("{\"type\":\"add_variable\",\"name\":\"myVar\",\"value_type\":\"number\"},");
        prompt.append("{\"type\":\"add_list\",\"name\":\"myList\",\"value_type\":\"string\"},");
        prompt.append("{\"type\":\"manual_only\",\"note\":\"...\"}");
        prompt.append("],");
        prompt.append("\"manual_steps\":[\"...\"]");
        prompt.append("}\n");
        prompt.append("Rules:\n");
        prompt.append("- The compile log may show only the first reported error.\n");
        prompt.append("- You must inspect the generated Java and search for other likely errors beyond the first log entry.\n");
        prompt.append("- Focus on the root cause and the complete fix sequence needed for this target event.\n");
        prompt.append("- If a secondary error will disappear after the root-cause fix, mention that in explanation/manual_steps.\n");
        prompt.append("- If you detect additional issues outside the provided target event, do not auto-apply them; describe them in manual_steps.\n");
        prompt.append("- Only use block ids that already exist in the provided blocks.\n");
        prompt.append("- Use update_parameter only for safe literal or identifier changes.\n");
        prompt.append("- If unsure, set can_auto_apply to false and use manual_only.\n");
        prompt.append("- Keep summary, explanation and manual_steps very short. Aim for 300 characters or less for the human-readable text.\n");
        prompt.append("- Keep explanation concise and practical.\n\n");
        prompt.append("Project ID: ").append(scId).append("\n");
        prompt.append("Target: ").append(targetId).append("_").append(eventName).append("\n");
        prompt.append("Error message: ").append(errorMessage == null ? "" : errorMessage).append("\n\n");
        prompt.append("Compile log:\n").append(rawLog == null ? "" : rawLog).append("\n\n");

        // Prefer the event-specific code as the primary focus; fall back to the full file
        // only when the event snippet is absent. The full file is capped at 6 000 chars to
        // avoid sending thousands of tokens for a single-line fix.
        final int MAX_FULL_SOURCE_CHARS = 6000;
        boolean generatedCodeAvailable = generatedCode != null && !generatedCode.trim().isEmpty();
        boolean fullSourceAvailable = fullJavaSource != null && !fullJavaSource.trim().isEmpty();

        if (generatedCodeAvailable) {
            prompt.append("Generated Java for this event (primary focus):\n").append(generatedCode).append("\n\n");
        }

        if (fullSourceAvailable) {
            String sourceToSend = fullJavaSource.length() > MAX_FULL_SOURCE_CHARS
                    ? fullJavaSource.substring(0, MAX_FULL_SOURCE_CHARS) + "\n// ... (truncated) ..."
                    : fullJavaSource;
            String label = generatedCodeAvailable
                    ? "Full generated Java file (secondary context):\n"
                    : "Generated Java file for this screen:\n";
            prompt.append(label).append(sourceToSend).append("\n\n");
        }

        prompt.append("Blocks JSON:\n").append(serializeBlocks(blocks)).append("\n");
        return prompt.toString();
    }

    private static String serializeBlocks(ArrayList<BlockBean> blocks) {
        JSONArray jsonArray = new JSONArray();
        if (blocks != null) {
            for (BlockBean block : blocks) {
                try {
                    JSONObject blockJson = new JSONObject();
                    blockJson.put("id", block.id);
                    blockJson.put("opCode", block.opCode);
                    blockJson.put("spec", block.spec);
                    blockJson.put("type", block.type);
                    blockJson.put("typeName", block.typeName);
                    blockJson.put("nextBlock", block.nextBlock);
                    blockJson.put("subStack1", block.subStack1);
                    blockJson.put("subStack2", block.subStack2);
                    blockJson.put("parameters", new JSONArray(block.parameters));
                    jsonArray.put(blockJson);
                } catch (Exception ignored) {
                }
            }
        }
        return jsonArray.toString();
    }

    private static int scoreCandidate(CandidateEvent candidateEvent, ParsedCompileError parsedCompileError, int startLine, int endLine) {
        int score = 0;
        if (parsedCompileError.lineNumber >= startLine && parsedCompileError.lineNumber <= endLine) {
            score += 1000;
        } else if (parsedCompileError.lineNumber > 0) {
            int distanceToStart = Math.abs(parsedCompileError.lineNumber - startLine);
            int distanceToEnd = Math.abs(parsedCompileError.lineNumber - endLine);
            score -= Math.min(distanceToStart, distanceToEnd);
        }

        if (parsedCompileError.message.contains(candidateEvent.eventName)) {
            score += 120;
        }
        if (parsedCompileError.message.contains(candidateEvent.targetId)) {
            score += 140;
        }
        if ("initializeLogic".equals(candidateEvent.eventName)) {
            score += 20;
        }
        if ("moreBlock".equals(candidateEvent.eventName)) {
            score += 10;
        }
        return score;
    }

    private static ArrayList<int[]> findOccurrences(String fullSource, String eventCode) {
        ArrayList<int[]> occurrences = new ArrayList<>();
        int searchStart = 0;
        while (searchStart >= 0 && searchStart < fullSource.length()) {
            int index = fullSource.indexOf(eventCode, searchStart);
            if (index < 0) {
                break;
            }
            occurrences.add(new int[]{index, index + eventCode.length()});
            searchStart = index + 1;
        }
        return occurrences;
    }

    private static int lineOfIndex(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static ProjectFileBean findProjectFile(String scId, String javaFileName) {
        ArrayList<ProjectFileBean> projectFiles = new ArrayList<>();
        projectFiles.addAll(jC.b(scId).b());
        projectFiles.addAll(jC.b(scId).c());

        for (ProjectFileBean projectFileBean : projectFiles) {
            if (javaFileName.equals(projectFileBean.getJavaName())) {
                return projectFileBean;
            }
        }
        return null;
    }

    private static ArrayList<CandidateEvent> buildCandidateEvents(String scId, ProjectFileBean projectFileBean) {
        ArrayList<CandidateEvent> candidateEvents = new ArrayList<>();

        CandidateEvent initializeLogic = new CandidateEvent("onCreate", "initializeLogic", "initializeLogic", "onCreate_initializeLogic");
        initializeLogic.alternateLogicKey = "initializeLogic_initializeLogic";
        candidateEvents.add(initializeLogic);

        for (EventBean eventBean : jC.a(scId).g(projectFileBean.getJavaName())) {
            candidateEvents.add(new CandidateEvent(
                    eventBean.targetId,
                    eventBean.eventName,
                    eventBean.eventName,
                    eventBean.targetId + "_" + eventBean.eventName
            ));
        }

        for (Pair<String, String> moreBlock : jC.a(scId).i(projectFileBean.getJavaName())) {
            candidateEvents.add(new CandidateEvent(
                    moreBlock.first,
                    "moreBlock",
                    "moreBlock",
                    moreBlock.first + "_moreBlock"
            ));
        }
        return candidateEvents;
    }

    private static ParsedCompileError parseCompileError(String rawLog) {
        ParsedCompileError parsedCompileError = new ParsedCompileError();
        if (rawLog == null || rawLog.trim().isEmpty()) {
            return parsedCompileError;
        }

        Matcher javaMatcher = JAVA_ERROR_PATTERN.matcher(rawLog);
        if (javaMatcher.find()) {
            parsedCompileError.fileName = javaMatcher.group(1);
            parsedCompileError.lineNumber = safeParseInt(javaMatcher.group(2));
            parsedCompileError.message = javaMatcher.group(3).trim();
            return parsedCompileError;
        }

        Matcher eclipseMatcher = ECLIPSE_ERROR_PATTERN.matcher(rawLog);
        if (eclipseMatcher.find()) {
            parsedCompileError.fileName = eclipseMatcher.group(1);
            parsedCompileError.lineNumber = safeParseInt(eclipseMatcher.group(2));
            parsedCompileError.message = extractFirstErrorLine(rawLog);
            return parsedCompileError;
        }

        Matcher genericFileMatcher = GENERIC_JAVA_FILE_PATTERN.matcher(rawLog);
        if (genericFileMatcher.find()) {
            parsedCompileError.fileName = genericFileMatcher.group(1);
        }
        parsedCompileError.message = extractFirstErrorLine(rawLog);
        return parsedCompileError;
    }

    private static String extractFirstErrorLine(String rawLog) {
        if (rawLog == null) {
            return "";
        }

        String[] lines = rawLog.split("\\r?\\n");
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains(" error") || lower.contains("error:")) {
                return line.trim();
            }
        }
        return lines.length > 0 ? lines[0].trim() : "";
    }

    private static int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static class ParsedCompileError {
        private String fileName = "";
        private int lineNumber = -1;
        private String message = "";
    }

    private static class CandidateEvent {
        private final String targetId;
        private final String eventName;
        private final String eventText;
        private final String logicKey;
        private String alternateLogicKey;

        private CandidateEvent(String targetId, String eventName, String eventText, String logicKey) {
            this.targetId = targetId;
            this.eventName = eventName;
            this.eventText = eventText;
            this.logicKey = logicKey;
        }
    }

    private static class CandidateMatch {
        private final CandidateEvent candidateEvent;
        private final int score;

        private CandidateMatch(CandidateEvent candidateEvent, int score) {
            this.candidateEvent = candidateEvent;
            this.score = score;
        }
    }
}
