package pe.soapros.document.infrastructure.util;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilidad para sanitizar datos sensibles en logs.
 *
 * Evita escribir en logs:
 * - PII (Personally Identifiable Information)
 * - Rutas completas de S3/filesystem
 * - URLs completas con posibles tokens
 * - Nombres de buckets completos
 * - Datos de usuario
 *
 * Uso:
 * <pre>
 * log.info("Processing file: " + LogSanitizer.sanitizePath(fullPath));
 * log.info("S3 location: " + LogSanitizer.sanitizeS3Path(s3Uri));
 * </pre>
 */
public class LogSanitizer {

    private static final Pattern S3_URI_PATTERN = Pattern.compile("s3://([^/]+)/(.+)");
    private static final Pattern HTTP_URI_PATTERN = Pattern.compile("https?://[^/]+/(.+)");

    /**
     * Sanitiza una ruta de archivo, mostrando solo el nombre del archivo.
     * Extrae unicamente el nombre sin revelar la estructura de directorios.
     *
     * @param fullPath ruta completa
     * @return solo el nombre del archivo
     */
    public static String sanitizePath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "[empty]";
        }

        try {
            Path path = Paths.get(fullPath);
            String fileName = path.getFileName().toString();
            return fileName.isEmpty() ? "[root]" : fileName;
        } catch (Exception e) {
            // Si falla el parsing, extraer manualmente
            int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
            return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
        }
    }

    /**
     * Sanitiza una URI de S3, ocultando el bucket y mostrando solo el nombre del archivo.
     * Oculta informacion sensible del bucket y path completo.
     *
     * @param s3Uri URI completa de S3
     * @return URI sanitizada con bucket oculto
     */
    public static String sanitizeS3Uri(String s3Uri) {
        if (s3Uri == null || s3Uri.isEmpty()) {
            return "[empty]";
        }

        try {
            // Formato: s3://bucket/key
            if (s3Uri.startsWith("s3://")) {
                URI uri = new URI(s3Uri);
                String path = uri.getPath();
                String fileName = extractFileName(path);
                return "s3://****/" + fileName;
            }

            // Formato: s3@host:path
            if (s3Uri.contains("@") && s3Uri.contains(":")) {
                int colonIndex = s3Uri.indexOf(':');
                if (colonIndex != -1) {
                    String path = s3Uri.substring(colonIndex + 1);
                    return extractFileName(path);
                }
            }

            // Fallback: extraer nombre de archivo
            return extractFileName(s3Uri);

        } catch (Exception e) {
            return extractFileName(s3Uri);
        }
    }

    /**
     * Sanitiza una URL HTTP, ocultando el host y mostrando solo el recurso final.
     * Oculta el hostname completo para proteger informacion sensible.
     *
     * @param httpUrl URL completa
     * @return URL sanitizada con host oculto
     */
    public static String sanitizeHttpUrl(String httpUrl) {
        if (httpUrl == null || httpUrl.isEmpty()) {
            return "[empty]";
        }

        try {
            URI uri = new URI(httpUrl);
            String path = uri.getPath();
            String fileName = extractFileName(path);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            return scheme + "://****/" + fileName;
        } catch (Exception e) {
            return extractFileName(httpUrl);
        }
    }

    /**
     * Sanitiza un objeto Map para logs, limitando el numero de campos y ocultando valores.
     * Retorna solo el conteo de campos sin exponer los valores.
     *
     * @param data map de datos
     * @return string sanitizado con formato de conteo
     */
    public static String sanitizeDataMap(Map<String, ?> data) {
        if (data == null) {
            return "null";
        }
        if (data.isEmpty()) {
            return "{empty}";
        }
        return String.format("{%d fields}", data.size());
    }

    /**
     * Sanitiza conteo de bytes para mostrar en formato legible.
     * Convierte bytes a formato KB o MB segun corresponda.
     *
     * @param bytes numero de bytes
     * @return tamanio legible en B, KB o MB
     */
    public static String sanitizeByteCount(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Sanitiza un template path mostrando solo el nombre del template.
     * Maneja rutas S3, HTTP y filesystem extrayendo solo el nombre.
     *
     * @param templatePath ruta completa del template
     * @return solo nombre del template sin path completo
     */
    public static String sanitizeTemplatePath(String templatePath) {
        if (templatePath == null || templatePath.isEmpty()) {
            return "[empty]";
        }

        if (templatePath.startsWith("s3://") || templatePath.startsWith("s3@")) {
            return extractFileName(sanitizeS3Uri(templatePath));
        }

        if (templatePath.startsWith("http://") || templatePath.startsWith("https://")) {
            return extractFileName(sanitizeHttpUrl(templatePath));
        }

        return sanitizePath(templatePath);
    }

    /**
     * Extrae el nombre de archivo de una ruta.
     *
     * @param path ruta
     * @return nombre de archivo
     */
    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "[empty]";
        }

        // Remover protocolo si existe
        String cleanPath = path;
        if (cleanPath.startsWith("s3://****")) {
            cleanPath = cleanPath.substring("s3://****/".length());
        } else if (cleanPath.startsWith("http://****")) {
            cleanPath = cleanPath.substring("http://****/".length());
        } else if (cleanPath.startsWith("https://****")) {
            cleanPath = cleanPath.substring("https://****/".length());
        }

        // Extraer nombre de archivo
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleanPath.length() - 1) {
            return cleanPath.substring(lastSlash + 1);
        }

        return cleanPath;
    }

    /**
     * Sanitiza un nombre de bucket S3, mostrando solo indicador generico.
     * Oculta completamente el nombre del bucket por seguridad.
     *
     * @param bucketName nombre del bucket
     * @return indicador generico sin revelar nombre real
     */
    public static String sanitizeBucketName(String bucketName) {
        if (bucketName == null || bucketName.isEmpty()) {
            return "[empty]";
        }
        // No revelar nombre de bucket en logs
        return "[S3_BUCKET]";
    }

    /**
     * Sanitiza un error message removiendo potencial informacion sensible.
     *
     * @param errorMessage mensaje de error
     * @return mensaje sanitizado
     */
    public static String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return "null";
        }

        // Remover rutas absolutas del sistema
        String sanitized = errorMessage.replaceAll("/[\\w/.-]+/", "/.../");

        // Remover URLs completas
        sanitized = sanitized.replaceAll("https?://[^\\s]+", "http://[REDACTED]");

        // Remover IPs
        sanitized = sanitized.replaceAll("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", "[IP]");

        return sanitized;
    }
}
