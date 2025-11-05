package pe.soapros.document.infrastructure.repository.downloader;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * "Downloads" (copies) templates from the local filesystem.
 * Handles URIs with format: fs@/path/to/file
 * Example: fs@/shared/templates/plantilla.docx
 *
 * This is useful for:
 * - Network file systems (NFS, SMB)
 * - Shared volumes in Docker
 * - Local development with absolute paths
 */
@ApplicationScoped
@JBossLog
public class FileSystemTemplateDownloader implements TemplateDownloader {

    @Override
    public String getProtocol() {
        return "fs@";
    }

    @Override
    public void download(String uri, File targetFile) throws Exception {
        log.infof("Copying from filesystem: %s", uri);

        // Remove "fs@" prefix to get the actual path
        String sourcePath = uri.substring(3);
        File sourceFile = new File(sourcePath);

        if (!sourceFile.exists()) {
            throw new IOException("Source file not found: " + sourcePath);
        }

        if (!sourceFile.canRead()) {
            throw new IOException("Cannot read source file: " + sourcePath);
        }

        // Copy to target
        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.infof("Copied %d bytes from %s to %s",
                    sourceFile.length(), sourceFile.getName(), targetFile.getName());
        } catch (IOException e) {
            log.errorf(e, "Failed to copy from filesystem: %s", uri);
            throw e;
        }
    }
}
