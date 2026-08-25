package pro.sketchware.ai.fix;

import org.json.JSONException;
import org.json.JSONObject;

public class AiFixSession {

    public String sessionId;
    public String scId;
    public String rawLog;
    public String errorFileName;
    public int errorLine = -1;
    public String errorMessage;
    public String targetJavaName;
    public String targetJavaSource;
    public String targetId;
    public String targetEventName;
    public String targetEventText;
    public long createdAt;

    public boolean hasResolvedTarget() {
        return notBlank(targetJavaName) && notBlank(targetId) && notBlank(targetEventName);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("sessionId", sessionId);
        jsonObject.put("scId", scId);
        jsonObject.put("rawLog", rawLog);
        jsonObject.put("errorFileName", errorFileName);
        jsonObject.put("errorLine", errorLine);
        jsonObject.put("errorMessage", errorMessage);
        jsonObject.put("targetJavaName", targetJavaName);
        jsonObject.put("targetJavaSource", targetJavaSource);
        jsonObject.put("targetId", targetId);
        jsonObject.put("targetEventName", targetEventName);
        jsonObject.put("targetEventText", targetEventText);
        jsonObject.put("createdAt", createdAt);
        return jsonObject;
    }

    public static AiFixSession fromJson(JSONObject jsonObject) {
        AiFixSession session = new AiFixSession();
        session.sessionId = jsonObject.optString("sessionId", "");
        session.scId = jsonObject.optString("scId", "");
        session.rawLog = jsonObject.optString("rawLog", "");
        session.errorFileName = jsonObject.optString("errorFileName", "");
        session.errorLine = jsonObject.optInt("errorLine", -1);
        session.errorMessage = jsonObject.optString("errorMessage", "");
        session.targetJavaName = jsonObject.optString("targetJavaName", "");
        session.targetJavaSource = jsonObject.optString("targetJavaSource", "");
        session.targetId = jsonObject.optString("targetId", "");
        session.targetEventName = jsonObject.optString("targetEventName", "");
        session.targetEventText = jsonObject.optString("targetEventText", "");
        session.createdAt = jsonObject.optLong("createdAt", 0L);
        return session;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
