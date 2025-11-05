package pe.soapros.document.infrastructure.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.soapros.document.domain.TemplateRequest;

import java.util.Map;

/**
 * Mapper para convertir JsonNode a TemplateRequest.
 *
 * Optimizado para reutilizar ObjectMapper inyectado en lugar de crear
 * instancias nuevas en cada invocación.
 */
@ApplicationScoped
public class TemplateDataMapper {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Convierte un JsonNode a TemplateRequest.
     *
     * @param data el JsonNode con los datos del template
     * @param images mapa de imágenes base64
     * @param path path del template
     * @return TemplateRequest configurado
     */
    public TemplateRequest from(JsonNode data, Map<String, String> images, String path) {
        Map<String, Object> dataMap = objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
        TemplateRequest request = new TemplateRequest();
        request.setData(dataMap);
        request.setImages(images);
        request.setTemplatePath(path);
        return request;
    }
}