package pe.soapros.document.infrastructure.generation.html;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.domain.exception.TemplateNotFoundException;
import pe.soapros.document.domain.exception.TemplateProcessingException;
import pe.soapros.document.infrastructure.qualifier.Html;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * HTML document generator using Mustache template engine.
 * Generates HTML documents from .html or .mustache templates.
 *
 * Template Syntax (Mustache):
 * - Variables: {{nombre}}, {{empresa}}, {{monto}}
 * - Conditionals: {{#isActive}}...{{/isActive}}
 * - Lists: {{#items}}...{{/items}}
 * - Images: <img src="{{imagen_logo}}" /> (base64 data URIs)
 *
 * Example Template:
 * <pre>
 * &lt;html&gt;
 *   &lt;body&gt;
 *     &lt;h1&gt;Hola {{nombre}}&lt;/h1&gt;
 *     &lt;p&gt;Empresa: {{empresa}}&lt;/p&gt;
 *     &lt;p&gt;Monto: {{monto}}&lt;/p&gt;
 *     {{#logo}}
 *       &lt;img src="{{logo}}" alt="Logo" /&gt;
 *     {{/logo}}
 *   &lt;/body&gt;
 * &lt;/html&gt;
 * </pre>
 */
@ApplicationScoped
@Html
@JBossLog
public class HtmlTemplateGenerator implements DocumentGenerator {

    private final MustacheFactory mustacheFactory;

    public HtmlTemplateGenerator() {
        // Create factory that looks for templates in classpath
        this.mustacheFactory = new DefaultMustacheFactory();
    }

    @Override
    public byte[] generate(TemplateRequest input) throws DocumentGenerationException {
        try {
            log.infof("Generating HTML document from template: %s", input.getTemplatePath());

            // Load template
            InputStream templateStream = loadTemplateAsStream(input.getTemplatePath());
            Reader reader = new InputStreamReader(templateStream, StandardCharsets.UTF_8);

            // Compile Mustache template
            Mustache mustache = mustacheFactory.compile(reader, input.getTemplatePath());

            // Prepare data context
            Map<String, Object> context = prepareContext(input);
            log.debugf("Prepared context with %d variables", context.size());

            // Render template
            StringWriter writer = new StringWriter();
            mustache.execute(writer, context).flush();
            String html = writer.toString();

            log.infof("HTML document generated successfully (%d bytes)", html.length());
            return html.getBytes(StandardCharsets.UTF_8);

        } catch (TemplateNotFoundException e) {
            // Re-throw domain exceptions as-is
            throw e;
        } catch (FileNotFoundException e) {
            log.errorf(e, "Template not found: %s", input.getTemplatePath());
            throw new TemplateNotFoundException(input.getTemplatePath());
        } catch (Exception e) {
            log.errorf(e, "Error processing HTML template: %s", input.getTemplatePath());
            throw new TemplateProcessingException("Failed to generate HTML document: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a template file from the classpath or filesystem.
     * First attempts to load from classpath (templates/), then falls back to filesystem.
     *
     * @param templatePath the path to the template
     * @return InputStream of the template file
     * @throws TemplateNotFoundException if the template cannot be found
     */
    private InputStream loadTemplateAsStream(String templatePath) throws TemplateNotFoundException {
        try {
            // First try to load from classpath (preferred for production)
            // Mustache expects templates in the root of classpath or in "templates/" folder
            String classPathTemplatePath = "templates/" + templatePath;
            InputStream classPathStream = getClass().getClassLoader().getResourceAsStream(classPathTemplatePath);

            if (classPathStream != null) {
                log.debugf("Template loaded from classpath: %s", classPathTemplatePath);
                return classPathStream;
            }

            // Try without templates/ prefix
            classPathStream = getClass().getClassLoader().getResourceAsStream(templatePath);
            if (classPathStream != null) {
                log.debugf("Template loaded from classpath: %s", templatePath);
                return classPathStream;
            }

            // Fallback to filesystem (for development/testing or when using absolute paths)
            File file = new File(templatePath);
            if (file.exists() && file.canRead()) {
                log.debugf("Template loaded from filesystem: %s", templatePath);
                return new FileInputStream(file);
            }

            throw new TemplateNotFoundException(templatePath);

        } catch (IOException e) {
            log.errorf(e, "Failed to load template: %s", templatePath);
            throw new TemplateNotFoundException(templatePath);
        }
    }

    /**
     * Prepares the context data for Mustache rendering.
     * Combines template data and images into a single context map.
     *
     * @param input the template request
     * @return context map for Mustache
     */
    private Map<String, Object> prepareContext(TemplateRequest input) {
        Map<String, Object> context = new HashMap<>();

        // Add all data variables
        if (input.getData() != null) {
            context.putAll(input.getData());
        }

        // Process images and add as base64 data URIs
        if (input.getImages() != null && !input.getImages().isEmpty()) {
            processImages(input.getImages(), context);
        }

        return context;
    }

    /**
     * Processes images and adds them to the context as base64 data URIs.
     * Images can be embedded directly in HTML using:
     * <img src="{{imagen_logo}}" />
     *
     * @param images map of image names to Base64-encoded data (without data URI prefix)
     * @param context the context map to add images to
     */
    private void processImages(Map<String, String> images, Map<String, Object> context) {
        for (Map.Entry<String, String> entry : images.entrySet()) {
            String imageName = entry.getKey();
            String base64Data = entry.getValue();

            try {
                // Detect image format from base64 data (first few bytes)
                String mimeType = detectImageMimeType(base64Data);

                // Create data URI
                String dataUri = String.format("data:%s;base64,%s", mimeType, base64Data);

                // Add to context with "imagen_" prefix to avoid conflicts
                String contextKey = "imagen_" + imageName;
                context.put(contextKey, dataUri);

                log.debugf("Processed image: %s -> %s (size: %d bytes)",
                        imageName, contextKey, base64Data.length());

            } catch (Exception e) {
                log.warnf("Failed to process image '%s': %s", imageName, e.getMessage());
                // Add placeholder
                context.put("imagen_" + imageName, "data:image/png;base64,");
            }
        }

        log.infof("Processed %d images", images.size());
    }

    /**
     * Detects the MIME type of an image from its base64 data.
     * Uses the magic bytes (file signature) to determine the format.
     *
     * @param base64Data the base64-encoded image data
     * @return the MIME type (e.g., "image/png", "image/jpeg")
     */
    private String detectImageMimeType(String base64Data) {
        try {
            // Decode first few bytes to check magic bytes
            byte[] bytes = Base64.getDecoder().decode(base64Data.substring(0, Math.min(20, base64Data.length())));

            // Check magic bytes
            if (bytes.length >= 2) {
                // PNG: 89 50 4E 47
                if (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50) {
                    return "image/png";
                }
                // JPEG: FF D8
                if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
                    return "image/jpeg";
                }
                // GIF: 47 49 46
                if (bytes[0] == (byte) 0x47 && bytes[1] == (byte) 0x49) {
                    return "image/gif";
                }
                // WebP: 52 49 46 46 (RIFF)
                if (bytes.length >= 4 && bytes[0] == (byte) 0x52 && bytes[1] == (byte) 0x49) {
                    return "image/webp";
                }
            }
        } catch (Exception e) {
            log.debugf("Could not detect image type, using default: %s", e.getMessage());
        }

        // Default to PNG
        return "image/png";
    }
}

