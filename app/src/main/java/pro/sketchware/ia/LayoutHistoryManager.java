package pro.sketchware.ia;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LayoutHistoryManager {
    private static final String PREFS_NAME = "layout_generation_history";
    private static final int MAX_HISTORY_SIZE = 10; // Manter apenas as últimas 10 conversas
    
    private final Context context;
    private final Gson gson;
    
    public LayoutHistoryManager(Context context) {
        this.context = context;
        this.gson = new Gson();
    }
    
    /**
     * Salva uma entrada no histórico
     * @param scId ID do projeto
     * @param xmlName Nome do arquivo XML
     * @param userPrompt Prompt do usuário
     * @param generatedLayout Layout gerado
     */
    public void saveHistoryEntry(String scId, String xmlName, String userPrompt, String generatedLayout) {
        if (scId == null || xmlName == null) return;
        
        String key = getHistoryKey(scId, xmlName);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        List<HistoryEntry> history = getHistory(scId, xmlName);
        
        // Adicionar nova entrada
        HistoryEntry entry = new HistoryEntry(userPrompt, generatedLayout);
        history.add(entry);
        
        // Manter apenas as últimas N entradas
        if (history.size() > MAX_HISTORY_SIZE) {
            history = new ArrayList<>(history.subList(history.size() - MAX_HISTORY_SIZE, history.size()));
        }
        
        // Salvar
        String json = gson.toJson(history);
        prefs.edit().putString(key, json).apply();
    }
    
    /**
     * Obtém o histórico de conversas
     * @param scId ID do projeto
     * @param xmlName Nome do arquivo XML
     * @return Lista de entradas do histórico
     */
    public List<HistoryEntry> getHistory(String scId, String xmlName) {
        if (scId == null || xmlName == null) return new ArrayList<>();
        
        String key = getHistoryKey(scId, xmlName);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(key, null);
        
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            Type listType = new TypeToken<ArrayList<HistoryEntry>>(){}.getType();
            List<HistoryEntry> history = gson.fromJson(json, listType);
            return history != null ? history : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * Limpa o histórico de um arquivo específico
     * @param scId ID do projeto
     * @param xmlName Nome do arquivo XML
     */
    public void clearHistory(String scId, String xmlName) {
        if (scId == null || xmlName == null) return;
        
        String key = getHistoryKey(scId, xmlName);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(key).apply();
    }
    
    /**
     * Gera a chave única para o histórico
     */
    private String getHistoryKey(String scId, String xmlName) {
        return scId + "_" + xmlName;
    }
    
    /**
     * Classe para representar uma entrada do histórico
     */
    public static class HistoryEntry {
        public String userPrompt;
        public String generatedLayout;
        public long timestamp;
        
        public HistoryEntry(String userPrompt, String generatedLayout) {
            this.userPrompt = userPrompt;
            this.generatedLayout = generatedLayout;
            this.timestamp = System.currentTimeMillis();
        }
    }
}

