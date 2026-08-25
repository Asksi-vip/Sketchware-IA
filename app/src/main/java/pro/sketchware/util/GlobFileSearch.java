package pro.sketchware.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Busca arquivos por padrão glob.
 * Exemplos: padrões como .java, test/.ts, etc.
 * Retorna caminhos ordenados por data de modificação.
 */
public class GlobFileSearch {
    
    /**
     * Busca arquivos usando padrão glob.
     * @param scId ID do projeto
     * @param globPattern Padrão glob (exemplos: arquivos .java, arquivos em test/, etc)
     * @return Lista de arquivos encontrados, ordenados por data de modificação (mais recentes primeiro)
     */
    public static List<ProjectFileDiscovery.FileInfo> search(String scId, String globPattern) {
        List<ProjectFileDiscovery.FileInfo> matches = new ArrayList<>();
        
        try {
            // Converter padrão glob para regex
            String regexPattern = globToRegex(globPattern);
            Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
            
            // Descobrir todos os arquivos do projeto
            List<ProjectFileDiscovery.FileInfo> allFiles = ProjectFileDiscovery.discoverFiles(scId, null);
            
            // Filtrar arquivos que correspondem ao padrão
            for (ProjectFileDiscovery.FileInfo fileInfo : allFiles) {
                if (!fileInfo.isDirectory) {
                    // Verificar se o nome do arquivo ou caminho corresponde ao padrão
                    if (pattern.matcher(fileInfo.name).matches() || 
                        pattern.matcher(fileInfo.path).matches()) {
                        matches.add(fileInfo);
                    }
                }
            }
            
            // Ordenar por data de modificação (mais recentes primeiro)
            matches.sort((a, b) -> {
                try {
                    ProjectPathResolver.ResolvedPath resolvedA = ProjectPathResolver.resolveForRead(scId, a.path);
                    ProjectPathResolver.ResolvedPath resolvedB = ProjectPathResolver.resolveForRead(scId, b.path);
                    if (resolvedA == null || resolvedB == null) {
                        return 0;
                    }
                    File fileA = resolvedA.getFile();
                    File fileB = resolvedB.getFile();
                    
                    if (fileA.exists() && fileB.exists()) {
                        return Long.compare(fileB.lastModified(), fileA.lastModified());
                    }
                } catch (Exception e) {
                    // Ignorar erros de ordenação
                }
                return 0;
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return matches;
    }
    
    /**
     * Converte padrão glob para regex
     * Suporta: *, **, ?, [chars], {a,b}
     */
    private static String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        int len = glob.length();
        boolean inBrackets = false;
        boolean inBraces = false;
        
        for (int i = 0; i < len; i++) {
            char c = glob.charAt(i);
            
            switch (c) {
                case '*':
                    if (i + 1 < len && glob.charAt(i + 1) == '*') {
                        // ** corresponde a qualquer sequência de diretórios
                        regex.append(".*");
                        i++; // Pular o próximo *
                    } else {
                        // * corresponde a qualquer sequência de caracteres (exceto /)
                        regex.append("[^/]*");
                    }
                    break;
                    
                case '?':
                    // ? corresponde a um único caractere (exceto /)
                    regex.append("[^/]");
                    break;
                    
                case '[':
                    inBrackets = true;
                    regex.append('[');
                    break;
                    
                case ']':
                    inBrackets = false;
                    regex.append(']');
                    break;
                    
                case '{':
                    inBraces = true;
                    regex.append('(');
                    break;
                    
                case '}':
                    inBraces = false;
                    regex.append(')');
                    break;
                    
                case ',':
                    if (inBraces) {
                        regex.append('|');
                    } else {
                        regex.append(',');
                    }
                    break;
                    
                case '.':
                case '+':
                case '(':
                case ')':
                case '^':
                case '$':
                case '|':
                    // Escapar caracteres especiais do regex
                    regex.append('\\').append(c);
                    break;
                    
                default:
                    regex.append(c);
                    break;
            }
        }
        
        return regex.toString();
    }
}

