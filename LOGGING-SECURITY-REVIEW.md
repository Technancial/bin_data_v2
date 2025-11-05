# Logging Security Review

**Fecha:** 2025-11-04
**Proyecto:** bind-data-v2 (Document Generation Engine)
**Revisado por:** Claude Code

---

## 📋 Resumen Ejecutivo

Este documento identifica información potencialmente sensible que se está logueando en el sistema y proporciona recomendaciones para mitigar riesgos de seguridad.

**Estado General:** ⚠️ **MEDIO RIESGO**

Se identificaron 67 statements de logging en 15 clases. La mayoría son seguros, pero algunos exponen información que podría ser sensible en ciertos contextos.

---

## 🔍 Información Sensible Detectada

### 1. **Rutas y URIs de Templates** (Riesgo: BAJO-MEDIO)

#### Archivos Afectados:
- `HtmlTemplateGenerator.java` (líneas 60, 85, 88, 109, 116, 123, 130)
- `XDocPdfGenerator.java` (líneas 50, 60, 63, 73, 106)
- `PlainTextGenerator.java` (línea 25)
- `S3TemplateRepository.java` (líneas 71, 83, 89, 168)
- `S3TemplateDownloader.java` (líneas 38, 42)
- `HttpTemplateDownloader.java` (líneas 43, 47)
- `FileSystemTemplateDownloader.java` (líneas 32, 49)

#### Ejemplos de Logs:
```java
log.infof("Generating HTML document from template: %s", input.getTemplatePath());
// Ejemplo: "Generating HTML document from template: s3@pe.nexux.talos.dev:2.0/capacniam/bn_ripley/template_producto/10_112.odt"

log.infof("Template not in cache or expired, downloading: %s", uriFile);
// Ejemplo: "Template not in cache or expired, downloading: s3@pe.nexux.talos.dev:2.0/capacniam/bn_ripley/template_producto/10_112.odt"
```

#### Riesgos:
- ✅ **Bajo riesgo general:** Las rutas de templates son típicamente públicas dentro de la organización
- ⚠️ **Riesgo potencial:**
  - Expone estructura de directorios S3
  - Revela nombres de buckets (pe.nexux.talos.dev)
  - Podría exponer información de clientes en nombres de rutas (bn_ripley, capacniam)

#### Recomendaciones:
- **Opción 1 (Preferida):** Loguear solo el nombre del archivo sin la ruta completa
  ```java
  String fileName = Paths.get(input.getTemplatePath()).getFileName().toString();
  log.infof("Generating HTML document from template: %s", fileName);
  ```

- **Opción 2:** Usar nivel DEBUG para rutas completas
  ```java
  log.debugf("Generating HTML document from template: %s", input.getTemplatePath());
  log.infof("Generating HTML document from template: [REDACTED]");
  ```

- **Opción 3:** Enmascarar información sensible
  ```java
  String sanitizedPath = sanitizePath(input.getTemplatePath());
  log.infof("Generating HTML document from template: %s", sanitizedPath);
  ```

---

### 2. **Endpoints y Configuración de AWS** (Riesgo: BAJO)

#### Archivos Afectados:
- `AwsClientProducer.java` (líneas 47, 59)

#### Ejemplos de Logs:
```java
log.infof("Creating S3 client for LocalStack at: %s", endpoint);
// Ejemplo: "Creating S3 client for LocalStack at: http://localstack:4566"

log.infof("Creating S3 client for AWS region: %s", awsRegion);
// Ejemplo: "Creating S3 client for AWS region: us-east-1"
```

#### Riesgos:
- ✅ **Riesgo muy bajo:** Esta información es de configuración estándar
- ⚠️ **Consideración:** En modo desarrollo (LocalStack) está bien, en producción podría revelar infraestructura

#### Recomendaciones:
- **Mantener en modo development** (ya está configurado así)
- **Opcional:** Cambiar a DEBUG en producción
  ```properties
  %prod.quarkus.log.category."pe.soapros.document.infrastructure.config".level=DEBUG
  ```

---

### 3. **Claves S3 y Nombres de Archivos** (Riesgo: MEDIO)

#### Archivos Afectados:
- `S3DocumentStorage.java` (líneas 45, 67, 72)
- `KafkaEventHandler.java` (línea 124)

#### Ejemplos de Logs:
```java
log.infof("Saving document to S3: s3://%s/%s (%d bytes)", documentsBucket, s3Key, documentBytes.length);
// Ejemplo: "Saving document to S3: s3://my-documents-bucket/generated-documents/01HFPZX8Y9ABC123.pdf (15234 bytes)"

log.infof("Document saved to S3: %s", s3Key);
// Ejemplo: "Document saved to S3: generated-documents/2024/11/04/client-123/invoice-456.pdf"
```

#### Riesgos:
- ⚠️ **Riesgo medio:** Las claves S3 pueden contener:
  - IDs de clientes
  - Nombres de documentos que revelan información de negocio
  - Fechas y estructuras organizacionales

#### Recomendaciones:
- **Opción 1 (Recomendada):** Loguear solo información estadística
  ```java
  log.infof("Saving document to S3 bucket: %s (%d bytes)", documentsBucket, documentBytes.length);
  log.debugf("S3 Key: %s", s3Key); // Solo en DEBUG
  ```

- **Opción 2:** Usar un identificador genérico
  ```java
  String keyHash = Integer.toHexString(s3Key.hashCode());
  log.infof("Document saved to S3: %s (key: %s)", documentsBucket, keyHash);
  ```

---

### 4. **Datos de Input del Usuario** (Riesgo: ALTO - CRÍTICO)

#### Archivos Afectados:
- `DocumentLambdaResource.java` (línea 43)

#### Ejemplos de Logs:
```java
log.infof("Input: %s", input.toString());
// ⚠️ CRÍTICO: Esto puede loguear TODA la información del request incluyendo:
// - Datos personales (nombres, emails, direcciones)
// - Información financiera (montos, cuentas)
// - Cualquier dato en el campo "data"
```

#### Riesgos:
- 🚨 **CRÍTICO:** Violación potencial de:
  - GDPR (si hay PII de europeos)
  - Ley de Protección de Datos Personales (Perú)
  - PCI-DSS (si hay datos de tarjetas)
  - Regulaciones financieras (si hay datos bancarios)

#### Recomendaciones:
- **⚠️ ACCIÓN INMEDIATA REQUERIDA:**
  ```java
  // ❌ NO HACER:
  log.infof("Input: %s", input.toString());

  // ✅ HACER:
  log.infof("Processing request - template: %s, fileType: %s, persist: %s",
            input.getTemplatePath(),
            input.getFileType(),
            input.isPersist());
  log.debugf("Request has %d data fields", input.getData() != null ? input.getData().size() : 0);
  ```

- **Implementar redacción de datos sensibles:**
  ```java
  private String sanitizeInput(TemplateRequest input) {
      return String.format("TemplateRequest[template=%s, fileType=%s, dataFields=%d, hasImages=%s]",
          getFileName(input.getTemplatePath()),
          input.getFileType(),
          input.getData() != null ? input.getData().size() : 0,
          input.getImages() != null && !input.getImages().isEmpty());
  }
  ```

---

### 5. **Mensajes de Kafka** (Riesgo: MEDIO-ALTO)

#### Archivos Afectados:
- `KafkaEventHandler.java` (línea 98)

#### Ejemplos de Logs:
```java
log.infof("Processing Kafka message %d - template: %s", index, mappedRequest.getTemplatePath());
// Puede exponer información sensible si el template path contiene datos de clientes
```

#### Riesgos:
- ⚠️ **Riesgo medio-alto:** Similar al punto 1, pero en contexto de eventos
- Los mensajes de Kafka pueden contener información de múltiples clientes

#### Recomendaciones:
- Usar el mismo enfoque que para templates (solo nombre del archivo)
- Considerar agregar un campo de "correlationId" para tracking sin exponer datos

---

## 📊 Resumen por Nivel de Riesgo

| Nivel | Cantidad | Acción Requerida |
|-------|----------|------------------|
| 🚨 **CRÍTICO** | 1 | **Inmediata** - Cambiar antes de producción |
| ⚠️ **MEDIO-ALTO** | 8 | **Alta prioridad** - Revisar en 1-2 días |
| ⚠️ **MEDIO** | 15 | **Media prioridad** - Revisar en 1 semana |
| ✅ **BAJO** | 43 | **Opcional** - Revisar cuando sea posible |

---

## 🛡️ Mejores Prácticas Implementadas

### ✅ Aspectos Positivos:

1. **Uso consistente de @JBossLog**: Todo el logging usa el mismo framework
2. **Niveles apropiados**: Uso correcto de DEBUG, INFO, ERROR, WARN
3. **Formato estructurado**: Los mensajes son claros y tienen contexto
4. **No se loguean contraseñas**: No se detectaron passwords en logs
5. **Excepciones bien logueadas**: Stack traces capturados con `log.errorf(exception, ...)`

---

## 🔧 Plan de Acción Recomendado

### Fase 1: Crítico (Antes de producción)
1. ✅ Configuración de logs mejorada (COMPLETADO)
2. 🔴 **PENDIENTE:** Eliminar `log.infof("Input: %s", input.toString())` en `DocumentLambdaResource.java`

### Fase 2: Alta Prioridad (1-2 días)
1. Implementar método `sanitizePath()` para enmascarar rutas sensibles
2. Revisar logs de S3DocumentStorage para reducir información de claves
3. Agregar correlationId para tracking sin exponer datos

### Fase 3: Media Prioridad (1 semana)
1. Cambiar logs de template paths a DEBUG
2. Implementar redacción automática de PII en logs
3. Agregar tests para verificar que no se loguean datos sensibles

### Fase 4: Mejora Continua (Opcional)
1. Implementar log scrubbing automático
2. Agregar alertas para logs sospechosos
3. Implementar audit logging separado para compliance

---

## 📝 Configuración de Logging por Ambiente

### Desarrollo
```properties
# Más verboso para debugging
%dev.quarkus.log.category."pe.soapros".level=DEBUG
%dev.quarkus.log.console.json=false
```

### Producción
```properties
# Solo información esencial
%prod.quarkus.log.category."pe.soapros".level=INFO
%prod.quarkus.log.console.json=true
%prod.quarkus.log.console.json.pretty-print=false

# Reducir ruido de frameworks
%prod.quarkus.log.category."io.quarkus".level=WARN
%prod.quarkus.log.category."software.amazon.awssdk".level=WARN
```

---

## 🔐 Recomendaciones de Compliance

### GDPR / Protección de Datos Personales
- ❌ **No loguear:** Nombres, emails, teléfonos, direcciones, IDs personales
- ✅ **Permitido:** Contadores, métricas, eventos sin PII

### PCI-DSS (Si aplica)
- ❌ **Nunca loguear:** Números de tarjetas, CVV, PINs
- ⚠️ **Enmascarar:** Primeros 6 y últimos 4 dígitos si es absolutamente necesario

### Información Financiera
- ❌ **No loguear:** Números de cuenta completos, claves, passwords
- ✅ **Permitido:** Montos agregados, tipos de transacción

---

## 📚 Referencias

- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [Quarkus Logging Guide](https://quarkus.io/guides/logging)
- [GDPR Article 32 - Security of Processing](https://gdpr-info.eu/art-32-gdpr/)

---

## ✅ Checklist de Revisión

- [x] Identificar todos los log statements
- [x] Clasificar por nivel de riesgo
- [x] Documentar información sensible
- [x] Proporcionar recomendaciones
- [ ] Implementar cambios críticos
- [ ] Revisar con equipo de seguridad
- [ ] Testing de logs en staging
- [ ] Deployment a producción
