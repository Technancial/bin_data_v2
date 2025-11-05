package pe.soapros.document.infrastructure.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import lombok.extern.jbosslog.JBossLog;

/**
 * CDI Producer para ObjectMapper.
 *
 * Crea y configura una instancia única de ObjectMapper que será inyectada
 * en toda la aplicación, evitando la creación repetida de instancias costosas.
 *
 * Configuraciones aplicadas:
 * - StreamReadConstraints: Soporte para JSON muy grandes (100MB strings, 2000 nested depth)
 * - DeserializationFeature: Tolerancia a propiedades desconocidas
 * - SerializationFeature: Formato de fechas ISO-8601
 *
 * Uso en clases:
 * <pre>
 * @Inject
 * ObjectMapper objectMapper;
 * </pre>
 */
@ApplicationScoped
@JBossLog
public class ObjectMapperProducer {

    /**
     * Produce una instancia singleton de ObjectMapper configurada para la aplicación.
     *
     * IMPORTANTE: Esta instancia es thread-safe después de la configuración inicial
     * y puede ser compartida por todos los threads de la aplicación.
     *
     * @return ObjectMapper configurado y optimizado
     */
    @Produces
    @Singleton
    public ObjectMapper createObjectMapper() {
        log.info("Creating and configuring singleton ObjectMapper instance");

        ObjectMapper mapper = new ObjectMapper();

        // Configurar constraints para manejar JSON muy grandes
        // Útil para payloads con clienteData extensos
        mapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(100_000_000)  // 100 MB para strings grandes
                .maxNestingDepth(2000)         // Profundidad de anidamiento
                .build()
        );

        // Configurar comportamiento de deserialización
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        // Configurar comportamiento de serialización
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false); // Compact JSON

        // Registrar módulos adicionales si es necesario
        // mapper.registerModule(new JavaTimeModule());

        log.info("ObjectMapper configured successfully with large payload support");

        return mapper;
    }
}
