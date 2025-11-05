package pe.soapros.document.infrastructure.repository.downloader;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Downloads templates from HTTP(S) URLs.
 * Handles URIs with format: http@url or https@url
 * Examples:
 *   - http@https://example.com/templates/plantilla.docx
 *   - https@https://secure.example.com/templates/report.odt
 *
 * Note: The protocol after @ should be the actual HTTP/HTTPS URL
 */
@ApplicationScoped
@JBossLog
public class HttpTemplateDownloader implements TemplateDownloader {

    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000;    // 30 seconds

    @Override
    public String getProtocol() {
        // This downloader handles both http@ and https@
        return "http"; // Will check in supports() method
    }

    @Override
    public boolean supports(String uri) {
        return uri != null && (uri.startsWith("http@") || uri.startsWith("https@"));
    }

    @Override
    public void download(String uri, File targetFile) throws Exception {
        log.infof("Downloading from HTTP(S): %s", uri);

        // Extract actual URL
        String actualUrl = extractUrl(uri);
        log.debugf("Actual URL: %s", actualUrl);

        // Download
        URL url = URI.create(actualUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod("GET");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error: " + responseCode + " for URL: " + actualUrl);
            }

            try (InputStream in = connection.getInputStream();
                 FileOutputStream fos = new FileOutputStream(targetFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }

                log.infof("Downloaded %d bytes from %s to %s",
                        totalBytes, actualUrl, targetFile.getName());
            }
        } catch (IOException e) {
            log.errorf(e, "Failed to download from HTTP(S): %s", uri);
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Extracts the actual URL from the URI.
     * Format: http@https://example.com/file or https@https://example.com/file
     */
    private String extractUrl(String uri) {
        if (uri.startsWith("http@")) {
            return uri.substring(5); // Remove "http@"
        } else if (uri.startsWith("https@")) {
            return uri.substring(6); // Remove "https@"
        }
        throw new IllegalArgumentException("Invalid HTTP URI format: " + uri);
    }
}
