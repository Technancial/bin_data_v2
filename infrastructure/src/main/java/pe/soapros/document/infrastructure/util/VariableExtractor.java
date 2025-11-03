package pe.soapros.document.infrastructure.util;

import com.fasterxml.jackson.databind.JsonNode;
import pe.soapros.document.domain.TemplateRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableExtractor {

    public Map<String, Object> extract(String path, JsonNode node) {
        Map<String, Object> context = new HashMap<>();
        populate(path, node, context);
        return context;
    }

    public Map<String, Object> extract (String path, TemplateRequest input ) {
        Map<String, Object> contextMap = new HashMap<>();
        populate("", input.getData(), contextMap);
        return contextMap;
    }

    private void populate(String path, Object value, Map<String, Object> context) {
        if (value instanceof Map) {
            Map<?, ?> objectMap = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : objectMap.entrySet()) {
                populate(String.valueOf(entry.getKey()), entry.getValue(), context);
            }
        } else if (value instanceof List) {
            List<?> array = (List<?>) value;
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object item : array) {
                Map<String, Object> itemMap = new HashMap<>();
                populate(path, item, itemMap);
                list.add(itemMap);
            }
            context.put(path, list);
        } else {
            context.put(path, value != null ? value.toString() : "");
        }
    }

    private void populate(String path, JsonNode node, Map<String, Object> context) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> populate(entry.getKey(), entry.getValue(), context));
        } else if (node.isArray()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode item : node) {
                Map<String, Object> itemMap = new HashMap<>();
                populate(path, item, itemMap);
                list.add(itemMap);
            }
            context.put(path, list);
        } else if (node.isValueNode()) {
            context.put(path, node.asText());
        }
    }
}
