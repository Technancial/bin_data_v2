# Flujo de Generación de Documentos con SentryMessage

## 📋 Descripción

El endpoint `/generate` ahora retorna el **mismo objeto `SentryMessageInput`** recibido, pero con el campo `result.location` actualizado con la ruta del documento generado.

## 🔄 Flujo Completo

```
1. Cliente envía SentryMessageInput
   ↓
2. SentryMessageMapper.toTemplateRequest() → List<TemplateRequest>
   ↓
3. GenerateDocumentUseCase.execute() → List<DocumentResult>
   ↓
4. SentryMessageMapper.updateWithGeneratedDocuments() → SentryMessageInput actualizado
   ↓
5. Retorna SentryMessageInput con result.location poblado
```

## 📥 Request (Input)

```json
{
  "metadata": { ... },
  "data": {
    "item_canonico": {
      "outputs": [
        {
          "type": "ec_con_password",
          "composicion": [
            {
              "resource": "template_001",
              "type": "template",
              "metadata": {
                "resource": {
                  "input_format": "odt",
                  "output_format": "pdf",
                  "location": "fs@/Users/furth/Downloads/10_112.odt",
                  "data": {
                    "nombre": "Juan Pérez",
                    "fecha": "2025-11-04",
                    "monto": 1500.50
                  }
                },
                "result": {
                  "location": null
                }
              }
            }
          ]
        }
      ]
    }
  }
}
```

## 📤 Response (Output)

### Escenario 1: Sin Persistencia (persist=false)

```json
{
  "metadata": { ... },
  "data": {
    "item_canonico": {
      "outputs": [
        {
          "type": "ec_con_password",
          "composicion": [
            {
              "resource": "template_001",
              "type": "template",
              "metadata": {
                "resource": {
                  "input_format": "odt",
                  "output_format": "pdf",
                  "location": "fs@/Users/furth/Downloads/10_112.odt",
                  "data": { ... }
                },
                "result": {
                  "location": "/temp/01K98M7JYD11TTQ391W43YDZK5.pdf"
                }
              }
            }
          ]
        }
      ]
    }
  }
}
```

### Escenario 2: Con Persistencia (persist=true)

```json
{
  "metadata": { ... },
  "data": {
    "item_canonico": {
      "outputs": [
        {
          "type": "ec_con_password",
          "composicion": [
            {
              "resource": "template_001",
              "type": "template",
              "metadata": {
                "resource": {
                  "input_format": "odt",
                  "output_format": "pdf",
                  "location": "fs@/Users/furth/Downloads/10_112.odt",
                  "data": { ... }
                },
                "result": {
                  "location": "s3@:my-documents-bucket/generated-documents/2025/11/04/01K98M7JYD11TTQ391W43YDZK5-550e8400-e29b-41d4-a716-446655440000.pdf"
                }
              }
            }
          ]
        }
      ]
    }
  }
}
```

## 🔍 Lógica de `result.location`

El campo `result.location` se actualiza con la siguiente lógica:

```java
String documentPath = result.getRepositoryPath() != null
        ? result.getRepositoryPath()  // Prioridad: Ruta S3 si fue persistido
        : result.getLocalPath();       // Fallback: Ruta local
```

### Valores posibles:

| Persist | result.location | Descripción |
|---------|----------------|-------------|
| `false` | `/temp/01K98M7JYD11TTQ391W43YDZK5.pdf` | Ruta local del archivo temporal |
| `true` | `s3@:my-bucket/generated-documents/2025/11/04/file-uuid.pdf` | Ruta S3 en formato protocolo |

## 🔧 Múltiples Documentos

Si el input contiene múltiples composiciones de tipo "template", cada una se actualiza con su respectiva ruta:

### Request:

```json
{
  "data": {
    "item_canonico": {
      "outputs": [
        {
          "type": "package",
          "composicion": [
            {
              "resource": "template_factura",
              "type": "template",
              "metadata": {
                "resource": {
                  "output_format": "pdf",
                  "location": "s3@templates:facturas/factura.odt",
                  "data": { "cliente": "ABC Corp" }
                },
                "result": { "location": null }
              }
            },
            {
              "resource": "template_recibo",
              "type": "template",
              "metadata": {
                "resource": {
                  "output_format": "html",
                  "location": "s3@templates:recibos/recibo.html",
                  "data": { "monto": 1500 }
                },
                "result": { "location": null }
              }
            }
          ]
        }
      ]
    }
  }
}
```

### Response:

```json
{
  "data": {
    "item_canonico": {
      "outputs": [
        {
          "type": "package",
          "composicion": [
            {
              "resource": "template_factura",
              "type": "template",
              "metadata": {
                "resource": { ... },
                "result": {
                  "location": "s3@:my-bucket/generated-documents/2025/11/04/factura-uuid1.pdf"
                }
              }
            },
            {
              "resource": "template_recibo",
              "type": "template",
              "metadata": {
                "resource": { ... },
                "result": {
                  "location": "s3@:my-bucket/generated-documents/2025/11/04/recibo-uuid2.html"
                }
              }
            }
          ]
        }
      ]
    }
  }
}
```

## ⚠️ Validaciones

El mapper `updateWithGeneratedDocuments` realiza las siguientes validaciones:

1. **Orden de resultados**: Los resultados deben estar en el mismo orden que fueron generados
2. **Cantidad de resultados**: Debe haber exactamente un resultado por cada composición de tipo "template"
3. **Estructura del mensaje**: Valida que existan los nodos requeridos

### Errores posibles:

```json
{
  "error": "InvalidTemplateDataException",
  "message": "Número de resultados (2) no coincide con número de templates (3)"
}
```

## 🎯 Ventajas del Nuevo Diseño

1. ✅ **Consistencia**: Mismo formato de entrada y salida
2. ✅ **Trazabilidad**: El cliente puede asociar fácilmente cada resultado con su request original
3. ✅ **Simplificidad**: No necesita mapear entre estructuras diferentes
4. ✅ **Flexibilidad**: Soporta tanto rutas locales como S3
5. ✅ **Mantenibilidad**: Lógica centralizada en el mapper

## 📝 Uso desde el Cliente

```javascript
// Enviar request
const response = await fetch('/generate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(sentryMessage)
});

const result = await response.json();

// Acceder a las rutas generadas
const locations = result.data.item_canonico.outputs
  .flatMap(output => output.composicion)
  .filter(comp => comp.type === 'template')
  .map(comp => comp.metadata.result.location);

console.log('Documentos generados:', locations);
// ["s3@:my-bucket/generated-documents/2025/11/04/doc-uuid.pdf"]
```

## 🔄 Compatibilidad con Sistema de Templates

El formato `s3@:bucket/path` es compatible con el sistema de lectura de templates:

```json
{
  "metadata": {
    "resource": {
      "location": "s3@templates:facturas/factura.odt"  // INPUT: Template a usar
    },
    "result": {
      "location": "s3@:my-bucket/generated-documents/2025/11/04/factura-uuid.pdf"  // OUTPUT: Documento generado
    }
  }
}
```

- **INPUT** (`resource.location`): Usa `s3@templates:path` (bucket configurado)
- **OUTPUT** (`result.location`): Retorna `s3@:bucket/path` (indica bucket completo)

## 🧪 Testing

Para probar el endpoint:

```bash
curl -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d @test-sentry-message.json
```

El response será el mismo JSON con `result.location` actualizado.
