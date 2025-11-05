package pe.soapros.document.infrastructure.generation.txt;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.domain.DocumentGenerator;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.DocumentGenerationException;
import pe.soapros.document.infrastructure.qualifier.Txt;

import java.nio.charset.StandardCharsets;

/**
 * Plain text document generator implementation.
 * Generates TXT documents from templates.
 *
 * TODO: Implement text generation logic using a template engine or simple string replacement
 */
@ApplicationScoped
@Txt
@JBossLog
public class PlainTextGenerator implements DocumentGenerator {

    @Override
    public byte[] generate(TemplateRequest input) throws DocumentGenerationException {
        log.infof("Generating TXT document from template: %s", input.getTemplatePath());

        // TODO: Implement TXT generation
        // Suggested approaches:
        // 1. Load .txt template from resources
        // 2. Use simple string replacement or a template engine like Freemarker
        // 3. Replace variables with data from input.getData()
        // 4. Format output appropriately (tables, lists, etc.)
        // 5. Return the generated text as bytes with proper charset encoding

        String text = buildPlaceholderText(input);
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Temporary placeholder implementation.
     * Replace this with actual template processing.
     */
    private String buildPlaceholderText(TemplateRequest input) {
        StringBuilder text = new StringBuilder();

        text.append("=".repeat(60)).append("\n");
        text.append("PLAIN TEXT DOCUMENT GENERATOR\n");
        text.append("=".repeat(60)).append("\n\n");

        text.append("Template: ").append(input.getTemplatePath()).append("\n\n");

        if (input.getData() != null) {
            text.append("Data:\n");
            text.append("-".repeat(60)).append("\n");
            input.getData().forEach((key, value) ->
                text.append(String.format("%-20s : %s\n", key, value))
            );
            text.append("-".repeat(60)).append("\n");
        }

        return text.toString();
    }
}
