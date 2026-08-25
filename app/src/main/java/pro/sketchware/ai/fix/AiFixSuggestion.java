package pro.sketchware.ai.fix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class AiFixSuggestion {

    public static class Operation {
        public String type = "";
        public String blockId = "";
        public int parameterIndex = -1;
        public String newValue = "";
        public String name = "";
        public String valueType = "";
        public String note = "";
    }

    public String summary = "";
    public String explanation = "";
    public double confidence = 0.0;
    public boolean canAutoApply = false;
    public final ArrayList<Operation> operations = new ArrayList<>();
    public final ArrayList<String> manualSteps = new ArrayList<>();
    public String rawResponse = "";

    public boolean hasApplicableOperations() {
        for (Operation operation : operations) {
            if ("update_parameter".equals(operation.type)
                    || "add_variable".equals(operation.type)
                    || "add_list".equals(operation.type)) {
                return true;
            }
        }
        return false;
    }

    public static AiFixSuggestion fromResponse(String response) {
        AiFixSuggestion suggestion = new AiFixSuggestion();
        suggestion.rawResponse = response == null ? "" : response;

        try {
            String jsonCandidate = extractJsonObject(response);
            JSONObject jsonObject = new JSONObject(jsonCandidate);
            suggestion.summary = jsonObject.optString("summary", "");
            suggestion.explanation = jsonObject.optString("explanation", "");
            suggestion.confidence = jsonObject.optDouble("confidence", 0.0);
            suggestion.canAutoApply = jsonObject.optBoolean("can_auto_apply", false);

            JSONArray operationsArray = jsonObject.optJSONArray("operations");
            if (operationsArray != null) {
                for (int i = 0; i < operationsArray.length(); i++) {
                    JSONObject operationObject = operationsArray.optJSONObject(i);
                    if (operationObject == null) {
                        continue;
                    }

                    Operation operation = new Operation();
                    operation.type = operationObject.optString("type", "");
                    operation.blockId = operationObject.optString("block_id", "");
                    operation.parameterIndex = operationObject.optInt("parameter_index", -1);
                    operation.newValue = operationObject.optString("new_value", "");
                    operation.name = operationObject.optString("name", "");
                    operation.valueType = operationObject.optString("value_type", "");
                    operation.note = operationObject.optString("note", "");
                    suggestion.operations.add(operation);
                }
            }

            JSONArray manualStepsArray = jsonObject.optJSONArray("manual_steps");
            if (manualStepsArray != null) {
                for (int i = 0; i < manualStepsArray.length(); i++) {
                    suggestion.manualSteps.add(manualStepsArray.optString(i, ""));
                }
            }
        } catch (Exception ignored) {
            suggestion.summary = "";
            suggestion.explanation = response == null ? "" : response.trim();
            suggestion.canAutoApply = false;
        }
        return suggestion;
    }

    private static String extractJsonObject(String response) {
        if (response == null) {
            throw new IllegalArgumentException("AI response is empty.");
        }

        int fencedStart = response.indexOf("```json");
        if (fencedStart >= 0) {
            int blockStart = response.indexOf('{', fencedStart);
            int fencedEnd = response.indexOf("```", fencedStart + 7);
            if (blockStart >= 0 && fencedEnd > blockStart) {
                return response.substring(blockStart, fencedEnd).trim();
            }
        }

        int firstBrace = response.indexOf('{');
        int lastBrace = response.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return response.substring(firstBrace, lastBrace + 1);
        }

        throw new IllegalArgumentException("No JSON object found in AI response.");
    }
}
