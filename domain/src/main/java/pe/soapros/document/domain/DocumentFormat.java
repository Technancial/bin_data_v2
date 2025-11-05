package pe.soapros.document.domain;

/**
 * Enumeration representing supported document output formats.
 * Each format includes its file extension and MIME type for proper handling.
 */
public enum DocumentFormat {
    PDF("pdf", "application/pdf"),
    HTML("html", "text/html"),
    TXT("txt", "text/plain");

    private final String extension;
    private final String mimeType;

    DocumentFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    /**
     * Converts a string representation to a DocumentFormat.
     * Defaults to PDF if the string is null or doesn't match any format.
     *
     * @param format the format string (e.g., "pdf", "html", "txt")
     * @return the corresponding DocumentFormat, or PDF as default
     */
    public static DocumentFormat fromString(String format) {
        if (format == null || format.trim().isEmpty()) {
            return PDF; // default
        }

        for (DocumentFormat df : values()) {
            if (df.extension.equalsIgnoreCase(format.trim())) {
                return df;
            }
        }
        return PDF; // default
    }

    @Override
    public String toString() {
        return extension;
    }
}
