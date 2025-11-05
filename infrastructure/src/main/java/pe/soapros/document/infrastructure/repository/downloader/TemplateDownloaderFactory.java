package pe.soapros.document.infrastructure.repository.downloader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory that selects the appropriate TemplateDownloader based on the URI protocol.
 * Uses CDI to automatically discover all TemplateDownloader implementations.
 *
 * Supported protocols:
 * - s3@ : AWS S3
 * - fs@ : Local filesystem
 * - http@ / https@ : HTTP(S) download
 *
 * To add a new protocol:
 * 1. Create a class implementing TemplateDownloader
 * 2. Annotate with @ApplicationScoped
 * 3. It will be automatically discovered and registered
 */
@ApplicationScoped
@JBossLog
public class TemplateDownloaderFactory {

    private final List<TemplateDownloader> downloaders;

    @Inject
    public TemplateDownloaderFactory(Instance<TemplateDownloader> downloaderInstances) {
        this.downloaders = new ArrayList<>();

        // Register all discovered downloaders
        for (TemplateDownloader downloader : downloaderInstances) {
            downloaders.add(downloader);
            log.infof("Registered template downloader: %s for protocol: %s",
                    downloader.getClass().getSimpleName(),
                    downloader.getProtocol());
        }

        if (downloaders.isEmpty()) {
            log.warn("No template downloaders registered!");
        }
    }

    /**
     * Gets the appropriate downloader for the given URI.
     *
     * @param uri the template URI
     * @return the matching downloader
     * @throws IllegalArgumentException if no downloader supports the URI
     */
    public TemplateDownloader getDownloader(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("URI cannot be null or empty");
        }

        for (TemplateDownloader downloader : downloaders) {
            if (downloader.supports(uri)) {
                log.debugf("Selected downloader: %s for URI: %s",
                        downloader.getClass().getSimpleName(), uri);
                return downloader;
            }
        }

        throw new IllegalArgumentException("No downloader found for URI: " + uri +
                ". Supported protocols: " + getSupportedProtocols());
    }

    /**
     * Checks if there's a downloader that supports the given URI.
     *
     * @param uri the URI to check
     * @return true if a downloader exists for this URI
     */
    public boolean hasDownloaderFor(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return false;
        }

        return downloaders.stream()
                .anyMatch(downloader -> downloader.supports(uri));
    }

    /**
     * Gets a list of all supported protocols.
     *
     * @return list of protocol prefixes
     */
    public List<String> getSupportedProtocols() {
        return downloaders.stream()
                .map(TemplateDownloader::getProtocol)
                .distinct()
                .toList();
    }
}
