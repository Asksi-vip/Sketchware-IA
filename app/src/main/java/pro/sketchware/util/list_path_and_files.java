package pro.sketchware.util;

import java.util.List;

/**
 * Helper para a ferramenta MCP que lista pastas, subpastas e arquivos (incluindo binários)
 */
public class list_path_and_files {

    /**
     * Lista tudo de forma recursiva, com ou sem filtro.
     *
     * @param scId          ID do projeto Sketchware
     * @param path          Caminho base (pode ser null para listar toda a estrutura)
     * @param searchPattern Filtro opcional por nome (pode ser null)
     * @return Texto com todas as pastas e arquivos encontrados
     */
    public static String execute(String scId, String path, String searchPattern) {
        try {
            // Sempre faz busca recursiva, mesmo sem searchPattern
            List<ProjectFileDiscovery.FileInfo> files;

            if (searchPattern != null && !searchPattern.trim().isEmpty()) {
                // Busca recursiva filtrada
                files = ProjectFileDiscovery.searchFiles(scId, searchPattern);
            } else {
                // Lista TUDO recursivamente (usa discoverFiles que já é recursivo)
                files = ProjectFileDiscovery.discoverFiles(scId, path);
            }

            if (files == null || files.isEmpty()) {
                return "Nenhum arquivo ou pasta encontrado" +
                        (path != null && !path.isEmpty() ? " em " + path : "") + ".";
            }

            StringBuilder sb = new StringBuilder();

            sb.append("**Lista completa de pastas e arquivos**\n\n");

            if (path != null && !path.isEmpty()) {
                sb.append("Caminho base: `").append(path).append("`\n\n");
            }

            sb.append("Legenda: 📁 pasta | 📄 arquivo\n");
            sb.append("Arquivos marcados como `encrypted=true` usam formato binário/criptografado do Sketchware.\n");
            sb.append("IMPORTANTE: Arquivos sem extensão (sem ponto no nome) são criptografados.\n");
            sb.append("NÃO adicione extensões (.json, .xml, etc.) ao caminho ao usar decrypt_file.\n");
            sb.append("Use o caminho EXATO como mostrado na lista acima.\n\n");

            for (ProjectFileDiscovery.FileInfo f : files) {
                sb.append(f.isDirectory ? "📁 " : "📄 ");
                sb.append(f.path);

                if (!f.isDirectory) {
                    sb.append(" (size=").append(f.size).append(" bytes");
                    sb.append(", encrypted=").append(f.isEncrypted ? "true" : "false");
                    // Adicionar aviso se não tem extensão
                    if (f.isEncrypted && !f.name.contains(".")) {
                        sb.append(", SEM EXTENSÃO - use caminho exato");
                    }
                    sb.append(")");
                }

                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return "Erro ao listar arquivos: " + e.getMessage();
        }
    }
}


