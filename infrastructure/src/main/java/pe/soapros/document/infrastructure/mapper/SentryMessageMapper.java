package pe.soapros.document.infrastructure.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.soapros.document.domain.DocumentResult;
import pe.soapros.document.domain.TemplateRequest;
import pe.soapros.document.domain.exception.InvalidTemplateDataException;
import pe.soapros.document.infrastructure.generation.input.SentryMessageInput;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SentryMessageMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Convierte un mensaje de Sentry a una lista plana de TemplateRequest.
     *
     * Estructura del mensaje Sentry:
     * - data.item_canonico.outputs[] (lista de outputs)
     *   - cada output tiene composicion[] (lista de templates)
     *
     * Esta función aplana la estructura anidada y retorna una lista simple
     * con todos los templates a generar.
     *
     * @param sentryMessage el mensaje de Sentry con la estructura de documentos a generar
     * @return lista plana de TemplateRequest (uno por cada template a generar)
     * @throws InvalidTemplateDataException si la estructura del mensaje es inválida
     */
    public static List<TemplateRequest> toTemplateRequest(SentryMessageInput sentryMessage) throws InvalidTemplateDataException {
        // 1. Validar existencia de la ruta crítica
        if (sentryMessage == null || sentryMessage.getData() == null || sentryMessage.getData().getItem_canonico() == null) {
            throw new InvalidTemplateDataException("El mensaje de Sentry está incompleto o le falta 'data.item_canonico'.");
        }

        List<SentryMessageInput.OutputNode> outputs = sentryMessage.getData().getItem_canonico().getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            throw new InvalidTemplateDataException("Falta la lista de outputs de 'item_canonico'.");
        }

        // Usar flatMap para aplanar la estructura anidada de outputs -> composiciones -> templates
        return outputs.stream()
                .flatMap((output) -> {
                    List<SentryMessageInput.ComposicionNode> composicions = output.getComposicion();
                    if (composicions == null || composicions.isEmpty()) {
                        throw new InvalidTemplateDataException(String.format("El output '%s' no tiene entradas de 'composicion'.", output.getType()));
                    }

                    // Para cada output, procesar sus composiciones y convertir a TemplateRequest
                    return composicions.stream()
                            .filter(composition -> "template".equals(composition.getType()))
                            .map((composition) -> {
                                String s3Location = composition.getMetadata().getResource().getLocation();

                                JsonNode templateDataNode = composition.getMetadata().getResource().getData();
                                if (templateDataNode == null || templateDataNode.isNull()) {
                                    throw new InvalidTemplateDataException("El nodo de datos de la plantilla está vacío.");
                                }
                                Map<String, Object> dataMap = MAPPER.convertValue(templateDataNode, new TypeReference<Map<String, Object>>() {});

                                // Extraer imágenes si existen
                                Map<String, String> imagesMap = extractImages(templateDataNode);

                                TemplateRequest request = new TemplateRequest();
                                request.setTemplatePath(s3Location);
                                request.setData(dataMap);
                                request.setImages(imagesMap);
                                request.setFileType(composition.getMetadata().getResource().getOutput_format());
                                return request;
                            });
                })
                .toList();
    }

    /**
     * Extrae el path completo del archivo de una URI S3 de Sentry.
     *
     * Formato esperado: s3@host:path/to/file.ext
     * Ejemplo: s3@pe.nexux.talos.dev:2.0/capacniam/bn_ripley/template_producto/10_112.odt
     * Retorna: 2.0/capacniam/bn_ripley/template_producto/10_112.odt
     *
     * @param uri la URI completa de S3
     * @return el path completo del archivo (todo después de los ':')
     */
    private static String extractFilenameFromS3Uri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            // Este caso debería generar un TemplateNotFoundException más adelante
            return "";
        }

        // Buscar el índice del primer ':' para extraer todo lo que viene después
        int colonIndex = uri.indexOf(':');
        if (colonIndex != -1 && colonIndex < uri.length() - 1) {
            // Retornar todo lo que viene después de los ':'
            return uri.substring(colonIndex + 1);
        }

        // Si no hay ':', asumir que es un path simple y retornarlo tal cual
        return uri;
    }

    /**
     * Extrae el mapa de imágenes (Base64) del nodo de datos si existe.
     */
    private static Map<String, String> extractImages(JsonNode dataNode) {
        // El JSON de ejemplo tiene un nodo 'images' dentro del nodo 'data' principal.
        if (dataNode.has("images") && dataNode.get("images").isObject()) {
            try {
                return MAPPER.convertValue(dataNode.get("images"), new TypeReference<Map<String, String>>() {});
            } catch (IllegalArgumentException e) {
                // El log ya capturaría el error en la generación, pero devolvemos vacío para no fallar el mapeo inicial
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Actualiza el SentryMessageInput original con las rutas de los documentos generados.
     * Modifica el campo 'result.location' de cada composición con la ruta del documento generado
     * (puede ser local o S3 si fue persistido).
     *
     * IMPORTANTE: El orden de los resultados debe coincidir con el orden en que se generaron
     * los documentos (mismo orden que toTemplateRequest()).
     *
     * @param sentryMessage el mensaje de Sentry original (se modifica in-place)
     * @param results lista de DocumentResult en el mismo orden que fueron generados
     * @return el mismo SentryMessageInput modificado (para conveniencia)
     * @throws InvalidTemplateDataException si hay inconsistencias en la estructura
     */
    public static SentryMessageInput updateWithGeneratedDocuments(
            SentryMessageInput sentryMessage,
            List<DocumentResult> results) throws InvalidTemplateDataException {

        if (sentryMessage == null || sentryMessage.getData() == null ||
            sentryMessage.getData().getItem_canonico() == null) {
            throw new InvalidTemplateDataException("El mensaje de Sentry está incompleto.");
        }

        List<SentryMessageInput.OutputNode> outputs = sentryMessage.getData().getItem_canonico().getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            throw new InvalidTemplateDataException("Falta la lista de outputs.");
        }

        // Contador para iterar sobre los resultados
        int resultIndex = 0;

        // Iterar sobre outputs y composiciones en el mismo orden que toTemplateRequest()
        for (SentryMessageInput.OutputNode output : outputs) {
            List<SentryMessageInput.ComposicionNode> composicions = output.getComposicion();
            if (composicions == null || composicions.isEmpty()) {
                continue;
            }

            for (SentryMessageInput.ComposicionNode composition : composicions) {
                // Solo procesar composiciones de tipo "template" (mismo filtro que en toTemplateRequest)
                if (!"template".equals(composition.getType())) {
                    continue;
                }

                // Verificar que no nos hayamos pasado del número de resultados
                if (resultIndex >= results.size()) {
                    throw new InvalidTemplateDataException(
                        String.format("Número de resultados (%d) no coincide con número de templates procesados",
                                     results.size()));
                }

                // Obtener el resultado correspondiente
                DocumentResult result = results.get(resultIndex);

                // Inicializar metadata.result si no existe
                if (composition.getMetadata() == null) {
                    composition.setMetadata(new SentryMessageInput.MetadataNode());
                }
                if (composition.getMetadata().getResult() == null) {
                    composition.getMetadata().setResult(new SentryMessageInput.ResultNode());
                }

                // Actualizar location con la ruta del documento generado
                // Priorizar repositoryPath (S3) si existe, sino usar localPath
                String documentPath = result.getRepositoryPath() != null
                        ? result.getRepositoryPath()
                        : result.getLocalPath();

                composition.getMetadata().getResult().setLocation(documentPath);

                resultIndex++;
            }
        }

        // Verificar que se hayan procesado todos los resultados
        if (resultIndex != results.size()) {
            throw new InvalidTemplateDataException(
                String.format("Número de resultados (%d) no coincide con número de templates (%d)",
                             results.size(), resultIndex));
        }

        return sentryMessage;
    }
}
