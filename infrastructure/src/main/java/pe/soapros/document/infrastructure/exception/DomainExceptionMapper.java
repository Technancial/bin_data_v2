package pe.soapros.document.infrastructure.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.jbosslog.JBossLog;
import pe.soapros.document.domain.exception.*;

import java.util.Map;

/**
 * JAX-RS ExceptionMapper that translates domain exceptions into appropriate HTTP responses.
 * This adapter prevents domain exceptions from leaking to the client and provides
 * meaningful error messages.
 */
@Provider
@JBossLog
public class DomainExceptionMapper implements ExceptionMapper<DocumentGenerationException> {

    @Override
    public Response toResponse(DocumentGenerationException exception) {
        log.errorf(exception, "Domain exception occurred: %s", exception.getClass().getSimpleName());

        // Map specific domain exceptions to HTTP status codes
        if (exception instanceof TemplateNotFoundException notFound) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse(
                            "TEMPLATE_NOT_FOUND",
                            "Template not found: " + notFound.getTemplatePath(),
                            Response.Status.NOT_FOUND.getStatusCode()
                    ))
                    .build();
        }

        if (exception instanceof InvalidTemplatePathException invalidPath) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse(
                            "INVALID_TEMPLATE_PATH",
                            "Invalid template path. Paths must be relative and cannot contain '..' or absolute references.",
                            Response.Status.BAD_REQUEST.getStatusCode()
                    ))
                    .build();
        }

        if (exception instanceof InvalidTemplateDataException) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse(
                            "INVALID_TEMPLATE_DATA",
                            exception.getMessage(),
                            Response.Status.BAD_REQUEST.getStatusCode()
                    ))
                    .build();
        }

        if (exception instanceof TemplateProcessingException) {
            return Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse(
                            "TEMPLATE_PROCESSING_ERROR",
                            "Failed to process template. Please check template format and data.",
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
                    ))
                    .build();
        }

        // Generic domain exception (fallback)
        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(createErrorResponse(
                        "DOCUMENT_GENERATION_ERROR",
                        "An error occurred while generating the document.",
                        Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
                ))
                .build();
    }

    /**
     * Creates a standardized error response structure.
     *
     * @param errorCode application-specific error code
     * @param message human-readable error message
     * @param statusCode HTTP status code
     * @return Map representing the error response
     */
    private Map<String, Object> createErrorResponse(String errorCode, String message, int statusCode) {
        return Map.of(
                "error", errorCode,
                "message", message,
                "status", statusCode,
                "timestamp", System.currentTimeMillis()
        );
    }
}
