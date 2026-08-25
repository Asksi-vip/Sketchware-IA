package pro.sketchware.util;

import android.os.Environment;

import java.io.File;

import mod.jbk.diagnostic.CompileErrorSaver;
import pro.sketchware.utility.FilePathUtil;

/**
 * Captura e gerencia erros de compilação do projeto
 */
public class CompileErrorCapture {
    
    /**
     * Obtém os últimos erros de compilação do projeto
     * @param scId ID do projeto
     * @return Texto dos erros de compilação ou null se não houver
     */
    public static String getLastCompileErrors(String scId) {
        try {
            CompileErrorSaver saver = new CompileErrorSaver(scId);
            return saver.getLogsFromFile();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Verifica se há erros de compilação salvos
     * @param scId ID do projeto
     * @return true se há erros salvos
     */
    public static boolean hasCompileErrors(String scId) {
        try {
            CompileErrorSaver saver = new CompileErrorSaver(scId);
            return saver.logFileExists();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Extrai informações relevantes dos erros de compilação
     * @param errorText Texto completo dos erros
     * @return Resumo formatado dos erros
     */
    public static String extractErrorSummary(String errorText) {
        if (errorText == null || errorText.trim().isEmpty()) {
            return "No compilation errors";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("**Compilation Errors:**\n\n");
        
        // Extrair erros principais
        String[] lines = errorText.split("\n");
        int errorCount = 0;
        
        for (String line : lines) {
            if (line.contains("ERROR") || line.contains("error:")) {
                summary.append("- ").append(line.trim()).append("\n");
                errorCount++;
                if (errorCount >= 10) { // Limitar a 10 erros
                    summary.append("... (more errors)\n");
                    break;
                }
            }
        }
        
        if (errorCount == 0) {
            // Se não encontrou padrão ERROR, pegar primeiras linhas
            int maxLines = Math.min(20, lines.length);
            for (int i = 0; i < maxLines; i++) {
                summary.append(lines[i]).append("\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Identifica arquivos mencionados nos erros de compilação
     * @param errorText Texto dos erros
     * @return Array de nomes de arquivos mencionados
     */
    public static String[] extractFilesFromErrors(String errorText) {
        if (errorText == null) {
            return new String[0];
        }
        
        java.util.List<String> files = new java.util.ArrayList<>();
        
        // Padrões comuns de referências a arquivos em erros Java
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "([A-Z][a-zA-Z0-9_]*\\.java|\\w+\\.xml|\\w+\\.kt)"
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(errorText);
        while (matcher.find()) {
            String fileName = matcher.group(1);
            if (!files.contains(fileName)) {
                files.add(fileName);
            }
        }
        
        return files.toArray(new String[0]);
    }
}

