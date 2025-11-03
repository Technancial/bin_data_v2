package pe.soapros.document.infrastructure.generation.input;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

@Data
public class TemplateData {
    private String templatePath;
    private JsonNode data;
    private Map<String, String> images;
}
