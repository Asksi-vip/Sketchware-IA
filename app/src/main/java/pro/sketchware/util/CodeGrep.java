package pro.sketchware.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Busca por padrões de texto no código (grep)
 * Suporta regex e busca exata de strings, símbolos, funções
 */
public class CodeGrep {
    
    /**
     * Resultado de uma busca grep
     */
    public static class GrepResult {
        public final String filePath;
        public final int lineNumber;
        public final String lineContent;
        public final String match;
        
        public GrepResult(String filePath, int lineNumber, String lineContent, String match) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.lineContent = lineContent;
            this.match = match;
        }
    }
    
    /**
     * Busca um padrão em um arquivo específico
     * @param filePath Caminho relativo do arquivo
     * @param pattern Padrão de busca (regex ou texto simples)
     * @param useRegex Se true, trata pattern como regex; se false, busca exata
     * @return Lista de resultados encontrados
     */
    public static List<GrepResult> searchInFile(String scId, String filePath, String pattern, boolean useRegex) {
        List<GrepResult> results = new ArrayList<>();
        
        try {
            String content = SketchwareFileDecryptor.decryptFile(scId, filePath);
            if (content == null || content.isEmpty()) {
                return results;
            }
            
            String[] lines = content.split("\n");
            Pattern regexPattern = null;
            
            if (useRegex) {
                try {
                    regexPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    // Se regex inválida, tratar como busca simples
                    useRegex = false;
                }
            }
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                boolean matches = false;
                String match = null;
                
                if (useRegex && regexPattern != null) {
                    Matcher matcher = regexPattern.matcher(line);
                    if (matcher.find()) {
                        matches = true;
                        match = matcher.group();
                    }
                } else {
                    // Busca simples (case-insensitive)
                    if (line.toLowerCase().contains(pattern.toLowerCase())) {
                        matches = true;
                        match = pattern;
                    }
                }
                
                if (matches) {
                    results.add(new GrepResult(filePath, i + 1, line.trim(), match));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return results;
    }
    
    /**
     * Busca um padrão em múltiplos arquivos do projeto
     * @param scId ID do projeto
     * @param pattern Padrão de busca
     * @param useRegex Se true, trata pattern como regex
     * @param filePattern Padrão opcional para filtrar arquivos (ex: "*.java")
     * @return Lista de resultados encontrados
     */
    public static List<GrepResult> searchInProject(String scId, String pattern, boolean useRegex, String filePattern) {
        List<GrepResult> allResults = new ArrayList<>();
        
        // Descobrir arquivos do projeto
        List<ProjectFileDiscovery.FileInfo> files;
        if (filePattern != null && !filePattern.isEmpty()) {
            files = GlobFileSearch.search(scId, filePattern);
        } else {
            files = ProjectFileDiscovery.discoverFiles(scId, null);
        }
        
        // Buscar em cada arquivo
        for (ProjectFileDiscovery.FileInfo fileInfo : files) {
            if (!fileInfo.isDirectory) {
                List<GrepResult> fileResults = searchInFile(scId, fileInfo.path, pattern, useRegex);
                allResults.addAll(fileResults);
            }
        }
        
        return allResults;
    }
}

