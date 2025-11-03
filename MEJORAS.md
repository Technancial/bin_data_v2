# Mejoras Implementadas - engine-commons-v2

## Resumen de Cambios

Este documento resume las mejoras implementadas en el proyecto para optimizar la arquitectura limpia, seguridad y despliegue en AWS Lambda.

---

## 1. Corrección del Dockerfile para Proyecto Multi-Módulo ✅

### Problema
El Dockerfile original asumía una estructura de proyecto simple con un único módulo, lo que causaba fallos en el build.

### Solución
- Actualizado para copiar los POMs de todos los módulos (domain, application, infrastructure)
- Implementado cacheo de dependencias Maven para optimizar builds
- Configurado para compilar solo el módulo infrastructure con sus dependencias
- Cambiado a usar **uber-jar** para mejor rendimiento en AWS Lambda

### Archivos Modificados
- `Dockerfile`
- `docker-compose.yml`

### Beneficios
- Build exitoso en Docker
- Mejor aprovechamiento del cache de Docker (builds más rápidos)
- Imagen optimizada para AWS Lambda

---

## 2. Sistema de Excepciones de Dominio ✅

### Problema
El código usaba `Exception` genérica en todo el proyecto, dificultando el manejo de errores específicos.

### Solución
Creadas excepciones específicas del dominio:

```
domain/src/main/java/pe/soapros/document/domain/exception/
├── DocumentGenerationException.java       (Base exception)
├── TemplateNotFoundException.java         (Template not found)
├── InvalidTemplatePathException.java      (Path traversal prevention)
├── InvalidTemplateDataException.java      (Invalid input data)
└── TemplateProcessingException.java       (Processing errors)
```

### Archivos Modificados
- `domain/src/main/java/pe/soapros/document/domain/DocumentGenerator.java`
- `application/src/main/java/pe/soapros/document/application/GenerateDocumentUseCase.java`
- `infrastructure/src/main/java/pe/soapros/document/infrastructure/generation/XDocPdfGenerator.java`

### Beneficios
- Errores más descriptivos y específicos
- Mejor debugging y logging
- Permite manejo de errores granular en diferentes capas

---

## 3. Validación de Seguridad en Paths ✅

### Problema
No había validación de paths de plantillas, permitiendo **ataques de Path Traversal**.

### Solución
Implementada validación estricta en `TemplateRequest`:
- Rechaza paths con `..` (path traversal)
- Rechaza paths absolutos (`/`, `\`)
- Rechaza paths con drive letters de Windows (`C:`, `D:`)
- Solo acepta paths relativos seguros

### Ejemplo de Protección
```java
// ❌ Rechazado
request.setTemplatePath("../../../etc/passwd");
request.setTemplatePath("/etc/passwd");
request.setTemplatePath("C:\\Windows\\System32\\config");

// ✅ Aceptado
request.setTemplatePath("plantilla.docx");
request.setTemplatePath("templates/invoice.docx");
```

### Archivos Modificados
- `domain/src/main/java/pe/soapros/document/domain/TemplateRequest.java`

### Beneficios
- Prevención de ataques de path traversal
- Cumplimiento con OWASP Top 10
- Seguridad en producción

---

## 4. ExceptionMapper para Infraestructura ✅

### Problema
Las excepciones de dominio llegaban sin procesar al cliente HTTP.

### Solución
Creado `DomainExceptionMapper` que traduce excepciones de dominio a respuestas HTTP apropiadas:

| Excepción de Dominio | HTTP Status | Error Code |
|---------------------|-------------|------------|
| `TemplateNotFoundException` | 404 NOT FOUND | TEMPLATE_NOT_FOUND |
| `InvalidTemplatePathException` | 400 BAD REQUEST | INVALID_TEMPLATE_PATH |
| `InvalidTemplateDataException` | 400 BAD REQUEST | INVALID_TEMPLATE_DATA |
| `TemplateProcessingException` | 500 SERVER ERROR | TEMPLATE_PROCESSING_ERROR |

### Formato de Respuesta de Error
```json
{
  "error": "TEMPLATE_NOT_FOUND",
  "message": "Template not found: plantilla.docx",
  "status": 404,
  "timestamp": 1699876543210
}
```

### Archivos Creados
- `infrastructure/src/main/java/pe/soapros/document/infrastructure/exception/DomainExceptionMapper.java`

### Archivos Modificados
- `infrastructure/src/main/java/pe/soapros/document/infrastructure/lambda/rest/DocumentLambdaResource.java`

### Beneficios
- Respuestas HTTP estandarizadas
- Códigos de error claros para clientes
- Separación de responsabilidades (domain vs HTTP)

---

## 5. Gestión de Plantillas en Producción ✅

### Problema
Las plantillas estaban en `src/test/resources/` y se copiaban manualmente.

### Solución
- Creado directorio `infrastructure/src/main/resources/templates/`
- Plantillas ahora se empaquetan dentro del JAR
- `XDocPdfGenerator` intenta cargar desde classpath primero, luego filesystem como fallback

### Archivos Movidos
- `infrastructure/src/test/resources/plantilla.docx` → `infrastructure/src/main/resources/templates/plantilla.docx`

### Archivos Modificados
- `infrastructure/src/main/java/pe/soapros/document/infrastructure/generation/XDocPdfGenerator.java`
- `Dockerfile`

### Beneficios
- Plantillas disponibles en producción sin copias manuales
- Mejor organización de recursos
- Soporte para cargar plantillas desde S3 en el futuro

---

## 6. Optimización de Empaquetado Quarkus ✅

### Problema
Usaba `legacy-jar` que no es óptimo para AWS Lambda.

### Solución
Cambiado a **uber-jar** para mejor rendimiento:

```xml
<quarkus.package.jar.type>uber-jar</quarkus.package.jar.type>
```

### Archivos Modificados
- `infrastructure/pom.xml`
- `Dockerfile`

### Beneficios
- Menor cold start en AWS Lambda
- Un solo JAR con todas las dependencias
- Despliegue simplificado

### Opción Nativa (Comentada)
```xml
<!-- Para producción de alto rendimiento -->
<quarkus.native.enabled>true</quarkus.native.enabled>
<quarkus.native.container-build>true</quarkus.native.container-build>
```

---

## 7. Integración Completa con Kafka ✅

### Problema
El código de Kafka estaba deshabilitado y sin implementación completa.

### Solución
- Habilitado `DocumentKafkaConsumer` con anotaciones `@Incoming` y `@Blocking`
- Agregado canal de salida para publicar resultados
- Implementado manejo de errores específico
- Configurado consumer y producer en `application.properties`

### Flujo de Datos
```
Kafka Topic: document-requests
    ↓
DocumentKafkaConsumer
    ↓
GenerateDocumentUseCase
    ↓
XDocPdfGenerator
    ↓
Kafka Topic: document-results
```

### Configuración
```properties
# Input channel
mp.messaging.incoming.document-requests.connector=smallrye-kafka
mp.messaging.incoming.document-requests.topic=document-requests

# Output channel
mp.messaging.outgoing.document-results.connector=smallrye-kafka
mp.messaging.outgoing.document-results.topic=document-results
```

### Archivos Modificados
- `infrastructure/src/main/java/pe/soapros/document/infrastructure/lambda/kafka/DocumentKafkaConsumer.java`
- `infrastructure/src/main/resources/application.properties`

### Beneficios
- Procesamiento asíncrono de documentos
- Arquitectura orientada a eventos
- Escalabilidad horizontal
- Separación de concerns (REST síncrono vs Kafka asíncrono)

---

## 8. Tests Unitarios ✅

### Problema
No había tests para validar la lógica de negocio.

### Solución
Creados tests para las capas domain y application:

#### Tests de Domain Layer
```
domain/src/test/java/pe/soapros/document/domain/
└── TemplateRequestTest.java
```

**Cobertura:**
- Validación de paths seguros
- Rechazo de path traversal
- Rechazo de paths absolutos
- Manejo de datos válidos e inválidos

#### Tests de Application Layer
```
application/src/test/java/pe/soapros/document/application/
└── GenerateDocumentUseCaseTest.java
```

**Cobertura:**
- Generación exitosa de documentos
- Propagación de excepciones
- Interacción con DocumentGenerator mock

### Archivos Modificados
- `domain/pom.xml` (agregadas dependencias JUnit)
- `application/pom.xml` (agregadas dependencias JUnit + Mockito)

### Ejecutar Tests
```bash
# Todos los tests
mvn test

# Solo domain
mvn test -pl domain

# Solo application
mvn test -pl application
```

### Beneficios
- Validación automática de lógica de negocio
- Detección temprana de regresiones
- Documentación viva del comportamiento esperado
- Base para CI/CD

---

## Arquitectura Final

### Diagrama de Capas
```
┌─────────────────────────────────────────────────────────┐
│ INFRASTRUCTURE LAYER                                     │
│ - DocumentLambdaResource (REST)                          │
│ - DocumentKafkaConsumer (Kafka)                          │
│ - XDocPdfGenerator (Implementación)                      │
│ - DomainExceptionMapper (Error handling)                 │
│ - UseCaseProducer (DI)                                   │
└─────────────────────┬───────────────────────────────────┘
                      │ depends on ↓
┌─────────────────────────────────────────────────────────┐
│ APPLICATION LAYER                                        │
│ - GenerateDocumentUseCase                                │
└─────────────────────┬───────────────────────────────────┘
                      │ depends on ↓
┌─────────────────────────────────────────────────────────┐
│ DOMAIN LAYER                                             │
│ - DocumentGenerator (interface)                          │
│ - TemplateRequest (entity con validación)                │
│ - Excepciones de dominio                                 │
└─────────────────────────────────────────────────────────┘
```

### Tecnologías
- **Framework:** Quarkus 3.24.4
- **Java:** 21
- **Build:** Maven multi-módulo
- **Container:** Docker + AWS Lambda
- **Messaging:** Kafka (Smallrye Reactive Messaging)
- **Document Processing:** XDocReport + Freemarker
- **Testing:** JUnit 5 + Mockito

---

## Comandos Útiles

### Build Local
```bash
mvn clean package -DskipTests
```

### Build con Tests
```bash
mvn clean package
```

### Build Docker
```bash
docker-compose build
```

### Run Local con Docker
```bash
docker-compose up
```

### Test Endpoint REST
```bash
curl -X POST http://localhost:9090/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templatePath": "plantilla.docx",
    "data": {
      "name": "John Doe",
      "amount": 1000
    }
  }'
```

### Deploy a AWS Lambda
```bash
# Build imagen
docker build -t document-generator .

# Tag para ECR
docker tag document-generator:latest <account-id>.dkr.ecr.<region>.amazonaws.com/document-generator:latest

# Push a ECR
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/document-generator:latest

# Actualizar función Lambda
aws lambda update-function-code \
  --function-name document-generator \
  --image-uri <account-id>.dkr.ecr.<region>.amazonaws.com/document-generator:latest
```

---

## Configuración de Kafka para Producción

### Variables de Entorno Recomendadas
```bash
# Kafka brokers
KAFKA_BOOTSTRAP_SERVERS=kafka-broker-1:9092,kafka-broker-2:9092

# Consumer group
KAFKA_GROUP_ID=document-generator-prod

# Topics
KAFKA_INPUT_TOPIC=document-requests
KAFKA_OUTPUT_TOPIC=document-results
```

### application.properties para Producción
```properties
%prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
%prod.mp.messaging.incoming.document-requests.group.id=${KAFKA_GROUP_ID}
%prod.mp.messaging.incoming.document-requests.topic=${KAFKA_INPUT_TOPIC}
%prod.mp.messaging.outgoing.document-results.topic=${KAFKA_OUTPUT_TOPIC}
```

---

## Próximos Pasos Recomendados

### Prioridad Alta
1. **Implementar almacenamiento en S3** para documentos generados
2. **Agregar métricas** (Micrometer + Prometheus)
3. **Health checks** para Kubernetes/ECS
4. **Circuit breaker** para resiliencia

### Prioridad Media
5. **Tests de integración** para infrastructure layer
6. **Compilación nativa** con GraalVM para producción
7. **OpenTelemetry** para observabilidad distribuida
8. **Versionado de APIs** (v1, v2)

### Prioridad Baja
9. **GraphQL API** como alternativa a REST
10. **Webhooks** para notificar completación de documentos
11. **Batch processing** para múltiples documentos

---

## Contribuciones

Al modificar el código, asegúrate de:
- ✅ Mantener la separación de capas (domain no debe depender de infrastructure)
- ✅ Agregar tests para nueva funcionalidad
- ✅ Documentar cambios en este archivo
- ✅ Validar seguridad (OWASP Top 10)
- ✅ Seguir los principios SOLID

---

## Contacto y Soporte

Para preguntas o problemas, contacta al equipo de desarrollo.

**Última actualización:** 2 de Noviembre, 2025
