package pe.soapros.document.infrastructure.generation;

import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.images.ByteArrayImageProvider;
import fr.opensagres.xdocreport.document.images.IImageProvider;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;
import jakarta.enterprise.context.ApplicationScoped;

import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.domain.exception.InvalidTemplateDataException;
import pe.soapros.document.domain.exception.TemplateNotFoundException;
import pe.soapros.document.domain.exception.TemplateProcessingException;
import pe.soapros.document.infrastructure.qualifier.Pdf;
import pe.soapros.document.infrastructure.util.VariableExtractor;

import java.io.*;
import java.util.Base64;
import java.util.Map;

/**
 * XDocReport-based implementation of DocumentGenerator.
 * Generates PDF documents from DOCX/ODT templates using the Freemarker template engine.
 */
@ApplicationScoped
@Pdf
@JBossLog
public class XDocPdfGenerator implements DocumentGenerator {

    /**
     * Loads a template file from the classpath or filesystem.
     * First attempts to load from classpath, then falls back to filesystem.
     *
     * @param templatePath the path to the template
     * @return InputStream of the template file
     * @throws TemplateNotFoundException if the template cannot be found
     */
    private InputStream loadTemplateAsStream(String templatePath) throws TemplateNotFoundException {
        try {
            // First try to load from classpath (preferred for production)
            InputStream classPathStream = getClass().getClassLoader().getResourceAsStream("templates/" + templatePath);
            if (classPathStream != null) {
                log.debugf("Template loaded from classpath: templates/%s", templatePath);
                return classPathStream;
            }

            // Fallback to filesystem (for development/testing)
            File file = new File(templatePath);
            if (!file.exists() || !file.canRead()) {
                throw new TemplateNotFoundException(templatePath);
            }

            log.debugf("Template loaded from filesystem: %s", templatePath);
            return new FileInputStream(file);
        } catch (IOException e) {
            log.errorf(e, "Failed to load template: %s", templatePath);
            throw new TemplateNotFoundException(templatePath);
        }
    }

    @Override
    public byte[] generate(TemplateRequest input) throws DocumentGenerationException {
        try {
            // Load template
            InputStream template = loadTemplateAsStream(input.getTemplatePath());
            log.debugf("Processing template: %s", input.getTemplatePath());

            // Parse template with XDocReport
            IXDocReport report = XDocReportRegistry.getRegistry()
                    .loadReport(template, TemplateEngineKind.Freemarker, false);
            log.debugf("Template engine: %s", report.getTemplateEngine());

            // Extract and prepare variables
            Map<String, Object> variables = new VariableExtractor().extract("", input);
            log.debugf("Extracted %d variables from template data", variables.size());

            IContext context = report.createContext(variables);

            // Process images if provided
            if (input.getImages() != null && !input.getImages().isEmpty()) {
                processImages(input.getImages(), report, context);
            }

            // Convert to PDF
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Options options = Options.getTo(ConverterTypeTo.PDF);
            report.convert(context, options, out);

            byte[] result = out.toByteArray();
            log.debugf("Successfully generated PDF document (%d bytes)", result.length);
            return result;

        } catch (TemplateNotFoundException | InvalidTemplateDataException e) {
            // Re-throw domain exceptions as-is
            throw e;
        } catch (IllegalArgumentException e) {
            log.errorf(e, "Invalid Base64 image data");
            throw new InvalidTemplateDataException("Invalid image data: " + e.getMessage(), e);
        } catch (Exception e) {
            log.errorf(e, "Error processing template: %s", input.getTemplatePath());
            throw new TemplateProcessingException("Failed to generate document: " + e.getMessage(), e);
        }
    }

    /**
     * Processes and embeds Base64-encoded images into the document.
     *
     * @param images map of image names to Base64-encoded data
     * @param report the XDocReport instance
     * @param context the template context
     * @throws InvalidTemplateDataException if image data is invalid
     */
    private void processImages(Map<String, String> images, IXDocReport report, IContext context)
            throws InvalidTemplateDataException {
        try {
            FieldsMetadata metadata = report.getFieldsMetadata();
            if (metadata == null) {
                metadata = new FieldsMetadata();
            }

            for (Map.Entry<String, String> entry : images.entrySet()) {
                String imageName = entry.getKey();
                String base64Data = entry.getValue();

                // Decode Base64 image
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                IImageProvider image = new ByteArrayImageProvider(imageBytes, true);

                // Add to context with prefix
                String key = "image_pr_" + imageName;
                metadata.addFieldAsImage(key);
                context.put(key, image);

                log.debugf("Processed image: %s (%d bytes)", imageName, imageBytes.length);
            }

            report.setFieldsMetadata(metadata);
            log.debugf("Successfully processed %d images", images.size());
        } catch (IllegalArgumentException e) {
            throw new InvalidTemplateDataException("Invalid Base64 image encoding", e);
        }
    }
}
