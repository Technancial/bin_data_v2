# Implementación de S3DocumentRepository

## 📋 Resumen

Se ha implementado completamente el sistema de guardado de documentos en S3 con metadata para búsquedas futuras.

## ✅ Cambios Realizados

### 1. **DocumentRepository Interface** (`domain/DocumentRepository.java`)
- ✅ Actualizado el método `save()` para retornar `String` (path del documento en S3)
- ✅ Documentación mejorada

### 2. **DocumentResult** (`domain/DocumentResult.java`) - NUEVO
- ✅ Clase de resultado que contiene:
  - `byte[] documentBytes` - Bytes del documento generado
  - `String localPath` - Ruta local donde se guardó
  - `String repositoryPath` - Ruta en S3 (si fue persistido)

### 3. **S3DocumentRepository** (`infrastructure/repository/S3DocumentRepository.java`)
- ✅ Implementación completa del método `save()`
- ✅ Generación de claves S3 con estructura organizada por fecha
- ✅ Metadata agregada para búsquedas:
  - `upload-timestamp` - Timestamp de subida
  - `original-filename` - Nombre original del archivo
  - `file-size` - Tamaño del archivo
  - `generated-date` - Fecha de generación
  - `file-type` - Tipo/extensión del archivo
- ✅ Detección automática de Content-Type
- ✅ Retorna path en formato `s3@:BUCKET/PATH`

### 4. **GenerateDocumentUseCase** (`application/GenerateDocumentUseCase.java`)
- ✅ Actualizado para retornar `DocumentResult` en lugar de `byte[]`
- ✅ Logging de ruta S3 cuando se persiste
- ✅ Maneja correctamente el caché de templates

### 5. **DocumentLambdaResource** (`infrastructure/lambda/rest/DocumentLambdaResource.java`)
- ✅ Actualizado para usar `DocumentResult`
- ✅ Incluye `repositoryPath` en la respuesta JSON cuando se persiste
- ✅ Logging mejorado

## 🏗️ Estructura de Claves S3

Los documentos se guardan con la siguiente estructura:

```
{prefix}/{year}/{month}/{day}/{filename}-{uuid}.{ext}

Ejemplo:
generated-documents/2025/11/04/01K98M7JYD11TTQ391W43YDZK5-550e8400-e29b-41d4-a716-446655440000.pdf
```

## 📦 Metadata del Objeto S3

Cada objeto S3 incluye los siguientes metadatos:

| Key | Ejemplo | Descripción |
|-----|---------|-------------|
| `upload-timestamp` | `2025-11-04T18:30:45` | Timestamp ISO de subida |
| `original-filename` | `01K98M7JYD11TTQ391W43YDZK5.pdf` | Nombre original del archivo |
| `file-size` | `245678` | Tamaño en bytes |
| `generated-date` | `2025-11-04` | Fecha de generación |
| `file-type` | `pdf` | Extensión/tipo de archivo |

## 🔧 Formato de Path Retornado

El sistema retorna paths en el formato protocolo establecido:

```
s3@:BUCKET/PATH/TO/FILE

Ejemplo:
s3@:my-documents-bucket/generated-documents/2025/11/04/report-550e8400-e29b-41d4-a716-446655440000.pdf
```

- El `:` después de `s3@` indica que se usa el bucket configurado en `application.properties`
- Compatible con el sistema de lectura de templates que usa el mismo formato

## 📝 Configuración Requerida

En `application.properties`:

```properties
# Bucket para documentos generados
aws.s3.bucket.documents=${S3_DOCUMENTS_BUCKET:my-documents-bucket}

# Prefijo/carpeta base en el bucket
aws.s3.prefix.documents=${S3_DOCUMENTS_PREFIX:generated-documents}

# Región AWS
aws.region=${AWS_REGION:us-east-1}

# Endpoint (opcional, para LocalStack en desarrollo)
aws.s3.endpoint=${AWS_ENDPOINT_URL:}
```

## 🧪 Ejemplo de Uso

### Request con persist=true:

```json
{
  "data": {
    "item_canonico": {
      "template_path": "fs@/Users/furth/Downloads/10_112.odt",
      "persist": true,
      "outputs": [
        {
          "file_type": "pdf",
          "var1": "valor1"
        }
      ]
    }
  }
}
```

### Response:

```json
{
  "success": true,
  "documentsGenerated": 1,
  "documents": [
    {
      "filename": "01K98M7JYD11TTQ391W43YDZK5.pdf",
      "fileType": "pdf",
      "base64Data": "JVBERi0xLjQKJeLjz9MK...",
      "sizeBytes": 245678,
      "templateName": "10_112.odt",
      "repositoryPath": "s3@:my-documents-bucket/generated-documents/2025/11/04/01K98M7JYD11TTQ391W43YDZK5-550e8400-e29b-41d4-a716-446655440000.pdf"
    }
  ]
}
```

## 🔍 Content-Type Soportados

El sistema detecta automáticamente el Content-Type basándose en la extensión:

- `.pdf` → `application/pdf`
- `.html`, `.htm` → `text/html`
- `.txt` → `text/plain`
- `.docx` → `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `.odt` → `application/vnd.oasis.opendocument.text`
- `.doc` → `application/msword`
- Otros → `application/octet-stream`

## 🚀 Próximos Pasos Sugeridos

1. **Búsqueda de Documentos**: Implementar búsqueda por metadata
2. **Cleanup Automático**: Job para limpiar documentos antiguos
3. **Versionado**: Sistema de versionado de documentos
4. **Pre-signed URLs**: Generación de URLs temporales para descarga
5. **Lifecycle Policies**: Configurar políticas de S3 para transición a Glacier/eliminación automática

## 🔐 Seguridad

- ✅ Validación de rutas de templates (prevención de path traversal)
- ✅ Soporte para protocolos seguros (`s3@`, `fs@`, `http@`)
- ✅ Caché de templates con TTL de 2 horas
- ✅ Metadata sanitizada (sin datos sensibles del usuario)
