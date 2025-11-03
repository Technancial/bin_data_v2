package pe.soapros.document.infrastructure.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.soapros.document.domain.TemplateRequest;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

public class TemplateDataMapper {

    public static TemplateRequest from(JsonNode data, Map<String, String> images, String path) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> dataMap = mapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
        TemplateRequest request = new TemplateRequest();
        request.setData(dataMap);
        request.setImages(images);
        request.setTemplatePath(path);
        return request;
    }
}