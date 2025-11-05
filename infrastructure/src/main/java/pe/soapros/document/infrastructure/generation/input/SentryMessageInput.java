package pe.soapros.document.infrastructure.generation.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    @ToString(exclude = {"clienteData"})
    public static class DataNode {
        private JsonNode cliente;
        private JsonNode clienteData;
        private ItemCanonico item_canonico;
        // Los otros campos (cliente, clienteData, action) pueden ser JsonNode si no se usan
    }


    @Data
    public static class ItemCanonico {
        private String output_producto;
        private String output_subproducto;
        private JsonNode output_metadata;
        private List<OutputNode> outputs;
        private JsonNode callback;
        private JsonNode metadata;
        private JsonNode _error;
    }

    @Data
    public static class OutputNode {
        private String type; // ej: "ec_con_password"
        private List<ComposicionNode> composicion; // La(s) plantilla(s) a usar
    }

    @Data
    public static class ComposicionNode {
        private String template;
        private JsonNode data;
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
        private JsonNode error;
        private JsonNode _error;
    }

    @Data
    public static class ResultNode {
        private String location;
    }
}
