package pe.soapros.document.infrastructure.repository.downloader;

import java.io.File;

/**
 * Strategy interface for downloading templates from different sources.
 * Each implementation handles a specific protocol (s3@, fs@, http@, etc.)
 */
public interface TemplateDownloader {

    /**
     * Gets the protocol prefix that this downloader handles.
     * Examples: "s3@", "fs@", "http@", "https@"
     *
     * @return the protocol prefix (including @)
     */
    String getProtocol();

    /**
     * Downloads a template from the source and saves it to the target file.
     *
     * @param uri the full URI (e.g., "s3@host:key")
     * @param targetFile the local file where the template should be saved
     * @throws Exception if download fails
     */
    void download(String uri, File targetFile) throws Exception;

    /**
     * Checks if this downloader supports the given URI.
     *
     * @param uri the URI to check
     * @return true if this downloader can handle the URI
     */
    default boolean supports(String uri) {
        return uri != null && uri.startsWith(getProtocol());
    }
}
