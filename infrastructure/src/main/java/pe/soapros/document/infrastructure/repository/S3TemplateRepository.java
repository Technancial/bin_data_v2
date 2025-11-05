package pe.soapros.document.infrastructure.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.domain.TemplateRepository;
import pe.soapros.document.domain.exception.TemplateNotFoundException;
import pe.soapros.document.infrastructure.repository.downloader.TemplateDownloader;
import pe.soapros.document.infrastructure.repository.downloader.TemplateDownloaderFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Multi-protocol template repository with intelligent caching.
 *
 * Supported Protocols:
 * - s3@host:key          - AWS S3
 * - fs@/path/to/file     - Local filesystem
 * - http@https://url     - HTTP(S) download
 * - https@https://url    - HTTP(S) download
 * - (no prefix)          - Classpath/relative path
 *
 * Cache Strategy:
 * - Downloaded templates are cached locally for 2 hours
 * - After 2 hours, they are automatically re-downloaded
 * - Cache is stored in: /tmp/templates-cache/
 *
 * Examples:
 * - s3@pe.nexux.talos.dev:2.0/capacniam/bn_ripley/template_producto/10_112.odt
 * - fs@/shared/templates/plantilla.docx
 * - http@https://example.com/templates/report.odt
 * - plantilla.docx (classpath)
 */
@ApplicationScoped
@JBossLog
public class S3TemplateRepository implements TemplateRepository {

    @Inject
    TemplateDownloaderFactory downloaderFactory;

    private static final String TEMPLATES_CACHE_DIR = System.getProperty("java.io.tmpdir") + "/templates-cache";
    private static final Duration CACHE_TTL = Duration.ofHours(2); // 2 hours TTL

    /**
     * Gets a template file.
     * Strategy:
     * 1. If URI has no protocol (no @), treat as classpath/relative path
     * 2. Check cache - if exists and not expired, return cached file
     * 3. If not in cache or expired, download using appropriate downloader
     * 4. Clean up expired cache files
     *
     * @param uriFile the template URI
     * @return the template File
     * @throws TemplateNotFoundException if the template cannot be found or downloaded
     */
    @Override
    public File getTemplate(String uriFile) {
        if (uriFile == null || uriFile.trim().isEmpty()) {
            throw new TemplateNotFoundException("URI is null or empty");
        }

        // If it's already a local/classpath path (no protocol), don't cache it
        if (!hasProtocol(uriFile)) {
            log.debugf("Template has no protocol, treating as local/classpath: %s", uriFile);
            File localFile = new File(uriFile);
            if (!localFile.exists()) {
                // Try classpath (will be handled by XDocPdfGenerator)
                return localFile;
            }
            return localFile;
        }

        // Check if template is in cache and still valid
        File cachedFile = getCachedFile(uriFile);
        if (isValidCache(cachedFile)) {
            log.infof("Using cached template: %s (age: %s)",
                    cachedFile.getName(), getCacheAge(cachedFile));
            return cachedFile;
        }

        // Not in cache or expired, download it
        log.infof("Template not in cache or expired, downloading: %s", uriFile);
        return downloadAndCache(uriFile, cachedFile);
    }

    /**
     * Checks if a template is a direct local/classpath path (no protocol).
     *
     * This method is used to determine if we need to process the template through
     * the downloader/cache system.
     * Returns true ONLY if:
     * - The template has NO protocol (classpath/relative path)
     *
     * If the template has a protocol (s3@, fs@, http@, etc.), this returns false
     * so that getTemplate() will be called to get the actual cached file path.
     *
     * @param uriFile the URI to check
     * @return true if template is a direct local path without protocol
     */
    @Override
    public boolean isLocal(String uriFile) {
        if (uriFile == null || uriFile.trim().isEmpty()) {
            return false;
        }

        // Only return true if there's NO protocol
        // This ensures that any URI with a protocol (including fs@) goes through getTemplate()
        boolean isLocal = !hasProtocol(uriFile);

        if (isLocal) {
            log.debugf("Template is a direct local path (no protocol): %s", uriFile);
        } else {
            log.debugf("Template has protocol, will use cache system: %s", uriFile);
        }

        return isLocal;
    }

    /**
     * Checks if a URI has a protocol prefix (@).
     *
     * @param uri the URI to check
     * @return true if it has a protocol (contains @)
     */
    private boolean hasProtocol(String uri) {
        return uri != null && uri.contains("@");
    }

    /**
     * Downloads a template using the appropriate downloader and caches it.
     *
     * @param uri the template URI
     * @param targetFile the target cache file
     * @return the cached File
     * @throws TemplateNotFoundException if download fails
     */
    private File downloadAndCache(String uri, File targetFile) {
        try {
            // Get the appropriate downloader
            TemplateDownloader downloader = downloaderFactory.getDownloader(uri);

            // Create cache directory if needed
            File cacheDir = targetFile.getParentFile();
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // Clean up expired cache files before downloading
            cleanExpiredCache();

            // Download
            downloader.download(uri, targetFile);

            log.infof("Template downloaded and cached: %s (%d bytes)",
                    targetFile.getAbsolutePath(), targetFile.length());

            return targetFile;

        } catch (Exception e) {
            log.errorf(e, "Failed to download template: %s", uri);
            throw new TemplateNotFoundException("Failed to download template: " + e.getMessage());
        }
    }

    /**
     * Checks if a cached file is valid (exists and not expired).
     *
     * @param cachedFile the cached file to check
     * @return true if file exists and is less than 2 hours old
     */
    private boolean isValidCache(File cachedFile) {
        if (cachedFile == null || !cachedFile.exists()) {
            return false;
        }

        try {
            BasicFileAttributes attrs = Files.readAttributes(cachedFile.toPath(), BasicFileAttributes.class);
            Instant creationTime = attrs.creationTime().toInstant();
            Instant now = Instant.now();
            Duration age = Duration.between(creationTime, now);

            boolean isValid = age.compareTo(CACHE_TTL) < 0;

            if (!isValid) {
                log.infof("Cache expired for file: %s (age: %s, TTL: %s)",
                        cachedFile.getName(), age, CACHE_TTL);
            }

            return isValid;

        } catch (IOException e) {
            log.warnf("Could not read file attributes for: %s", cachedFile.getName());
            return false;
        }
    }

    /**
     * Gets the age of a cached file as a human-readable string.
     *
     * @param cachedFile the cached file
     * @return formatted age string
     */
    private String getCacheAge(File cachedFile) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(cachedFile.toPath(), BasicFileAttributes.class);
            Instant creationTime = attrs.creationTime().toInstant();
            Instant now = Instant.now();
            Duration age = Duration.between(creationTime, now);

            long hours = age.toHours();
            long minutes = age.toMinutes() % 60;
            return String.format("%dh %dm", hours, minutes);

        } catch (IOException e) {
            return "unknown";
        }
    }

    /**
     * Gets the cached file path for a given URI.
     * Uses MD5 hash to create a safe filename.
     *
     * @param uri the URI
     * @return the cached File
     */
    private File getCachedFile(String uri) {
        File cacheDir = new File(TEMPLATES_CACHE_DIR);
        String safeFileName = generateSafeFileName(uri);
        return new File(cacheDir, safeFileName);
    }

    /**
     * Generates a safe filename from a URI using MD5 hash.
     * Preserves the original file extension.
     *
     * @param uri the URI
     * @return safe filename
     */
    private String generateSafeFileName(String uri) {
        try {
            // Extract extension from the URI
            String extension = "";
            int lastDot = uri.lastIndexOf('.');
            if (lastDot != -1) {
                int lastSlash = uri.lastIndexOf('/');
                // Only use extension if the dot comes after the last slash
                if (lastSlash < lastDot) {
                    extension = uri.substring(lastDot);
                }
            }

            // Hash the full URI for a safe filename
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(uri.getBytes());
            String hashHex = HexFormat.of().formatHex(hash);

            return hashHex + extension;
        } catch (Exception e) {
            // Fallback: just replace problematic characters
            return uri.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
    }

    /**
     * Cleans up expired cache files (older than 2 hours).
     * This is called before each download to keep the cache clean.
     */
    private void cleanExpiredCache() {
        File cacheDir = new File(TEMPLATES_CACHE_DIR);
        if (!cacheDir.exists() || !cacheDir.isDirectory()) {
            return;
        }

        File[] files = cacheDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        int deletedCount = 0;
        for (File file : files) {
            if (!isValidCache(file)) {
                try {
                    if (file.delete()) {
                        deletedCount++;
                        log.debugf("Deleted expired cache file: %s", file.getName());
                    }
                } catch (Exception e) {
                    log.warnf("Failed to delete expired cache file: %s", file.getName());
                }
            }
        }

        if (deletedCount > 0) {
            log.infof("Cleaned up %d expired cache files", deletedCount);
        }
    }
}
