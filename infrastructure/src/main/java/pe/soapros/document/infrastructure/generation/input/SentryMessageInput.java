package pe.soapros.document.infrastructure.generation.input;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@NoArgsConstructor
@ToString
public class SentryMessageInput {
    private JsonNode metadata;
    private DataNode data;

    @Data
    public static class DataNode {
        private JsonNode clienteData;
        private ItemCanonico item_canonico;
        // Los otros campos (cliente, clienteData, action) pueden ser JsonNode si no se usan
    }

    @Data
    public static class ItemCanonico {
        // Contiene la lista de documentos (outputs) a generar
        private List<OutputNode> outputs;
    }

    @Data
    public static class OutputNode {
        private String type; // ej: "ec_con_password"
        private List<ComposicionNode> composicion; // La(s) plantilla(s) a usar
    }

    @Data
    public static class ComposicionNode {
        private String resource;
        private String type;
        private MetadataNode metadata;
    }

    @Data
    public static class MetadataNode {
        private ResourceNode resource;
        private ResultNode result;
    }

    @Data
    public static class ResourceNode {
        private String input_format;
        private String output_format;
        private String location;
        private JsonNode data;
    }

    @Data
    public static class ResultNode {
        private String location;
    }
}
