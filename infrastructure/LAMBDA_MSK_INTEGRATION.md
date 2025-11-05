# AWS Lambda + MSK Integration

## Descripción

Integración entre **AWS Lambda** y **AWS MSK (Managed Streaming for Apache Kafka)** para procesamiento batch de documentos usando **programación funcional**.

---

## Arquitectura

```
AWS MSK Topic (Input)
  document-requests
       ↓
Lambda Event Source Mapping
  - Consume mensajes
  - Agrupa en batch (10 msgs o 5 segundos)
       ↓
Lambda Function (Quarkus)
  POST /msk/batch
       ↓
LambdaMskEventHandler
  - parallelStream() procesamiento
  - Genera documentos (PDF/HTML/TXT)
  - Actualiza rutas S3
       ↓
DocumentResultProducer
  - Kafka Producer
  - Envía resultados a MSK
       ↓
AWS MSK Topic (Output)
  document-responses
```

---

## Componentes

### **1. LambdaMskEventHandler** (Handler Principal)

**Archivo**: `LambdaMskEventHandler.java`

```java
@Path("/msk")
public class LambdaMskEventHandler {
    @POST @Path("/batch")
    public Response processMskBatch(List<SentryMessageInput> inputBatch)
}
```

**Responsabilidades:**
- Recibe batch de mensajes desde Lambda ESM
- Procesa en paralelo con `parallelStream()`
- Genera documentos (misma lógica que `DocumentLambdaResource`)
- Envía resultados a MSK vía `DocumentResultProducer`
- Retorna estadísticas HTTP a Lambda

**Características funcionales:**
```java
// Pipeline funcional
inputBatch.parallelStream()
    .map(this::processMessageFunctional)        // Input → ProcessResult
    .collect(Collectors.toList())               // Acumular resultados

// Transformación pura
SentryMessageInput → TemplateRequests → DocumentResults → Updated Input
```

---

### **2. DocumentResultProducer** (Kafka Producer)

**Archivo**: `DocumentResultProducer.java`

```java
@ApplicationScoped
public class DocumentResultProducer {
    @Channel("document-responses-manual")
    Emitter<String> resultEmitter;

    public CompletionStage<Void> sendBatch(List<SentryMessageInput> results)
}
```

**Responsabilidades:**
- Envía resultados procesados a tópico MSK
- Operaciones asíncronas con `CompletableFuture`
- Serialización JSON automática

---

## Configuración

### **AWS Lambda Event Source Mapping**

```bash
aws lambda create-event-source-mapping \
  --function-name document-generator-lambda \
  --event-source-arn arn:aws:kafka:us-east-1:123456789:cluster/msk-cluster/uuid \
  --topics document-requests \
  --starting-position LATEST \
  --batch-size 10 \
  --maximum-batching-window-in-seconds 5
```

**Parámetros clave:**
- `batch-size`: Máximo de mensajes por batch (10 recomendado)
- `maximum-batching-window-in-seconds`: Timeout para batch (5s)
- `starting-position`: LATEST (solo nuevos) o EARLIEST (desde inicio)

---

### **Variables de Entorno Lambda**

```bash
# Kafka/MSK
KAFKA_BOOTSTRAP_SERVERS=b-1.msk-cluster.xxx.kafka.us-east-1.amazonaws.com:9092,b-2...
KAFKA_TOPIC_RESPONSES=document-responses

# S3
S3_DOCUMENTS_BUCKET=my-documents-bucket
S3_DOCUMENTS_PREFIX=generated-documents
S3_TEMPLATES_BUCKET=my-templates-bucket

# App
GENERATION_TEMP=/tmp
AWS_REGION=us-east-1
```

---

### **application.properties**

```properties
# Kafka broker (AWS MSK)
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}

# Producer configuration (envío a tópico de salida)
mp.messaging.outgoing.document-responses-manual.connector=smallrye-kafka
mp.messaging.outgoing.document-responses-manual.topic=${KAFKA_TOPIC_RESPONSES}
mp.messaging.outgoing.document-responses-manual.compression.type=gzip
mp.messaging.outgoing.document-responses-manual.acks=all
mp.messaging.outgoing.document-responses-manual.max.request.size=10485760
```

**Nota**: NO hay configuración de consumer porque AWS Lambda ESM lo gestiona.

---

## Flujo de Datos

### **1. Input (AWS MSK → Lambda ESM)**

**Tópico**: `document-requests`

```json
{
  "metadata": {},
  "data": {
    "cliente": {"name": "Cliente ABC"},
    "item_canonico": {
      "outputs": [{
        "type": "ec_con_password",
        "composicion": [{
          "type": "template",
          "metadata": {
            "resource": {
              "output_format": "pdf",
              "location": "s3://templates-bucket/template.odt",
              "data": {
                "nombre": "Juan Pérez",
                "fecha": "2025-01-15",
                "monto": "1000.00"
              }
            }
          }
        }]
      }]
    }
  }
}
```

### **2. Procesamiento (LambdaMskEventHandler)**

```java
// 1. Lambda ESM invoca con batch
POST /msk/batch
Content-Type: application/json
[{mensaje1}, {mensaje2}, ..., {mensaje10}]

// 2. Procesamiento paralelo funcional
inputBatch.parallelStream()
    .map(input -> {
        // a) Convertir a TemplateRequest
        List<TemplateRequest> templates = SentryMessageMapper.toTemplateRequest(input);

        // b) Generar documentos en paralelo
        List<DocumentResult> results = templates.parallelStream()
            .map(this::generateDocument)
            .collect(Collectors.toList());

        // c) Actualizar input con rutas generadas
        return SentryMessageMapper.updateWithGeneratedDocuments(input, results);
    })
```

### **3. Output (Lambda → AWS MSK)**

**Tópico**: `document-responses`

```json
{
  "metadata": {},
  "data": {
    "cliente": {"name": "Cliente ABC"},
    "item_canonico": {
      "outputs": [{
        "type": "ec_con_password",
        "composicion": [{
          "type": "template",
          "metadata": {
            "resource": {
              "output_format": "pdf",
              "location": "s3://templates-bucket/template.odt",
              "data": {...}
            },
            "result": {
              "location": "s3://documents-bucket/generated/01JCXM9K3HQRST.pdf"
            }
          }
        }]
      }]
    }
  }
}
```

**Campo nuevo**: `result.location` con la ruta del documento generado.

---

## Programación Funcional

### **Principios Aplicados**

#### **1. Funciones Puras**
```java
// Mismo input → mismo output
private DocumentResult generateDocument(TemplateRequest template) {
    String pathFile = generateFilename(template.getFileType());
    return generateDocumentUseCase.execute(template, pathFile);
}
```

#### **2. Inmutabilidad**
```java
// No modifica input, crea nuevo objeto
SentryMessageInput updatedInput = SentryMessageMapper
    .updateWithGeneratedDocuments(input, documentResults);
```

#### **3. Streams Paralelos**
```java
// Procesamiento concurrente
inputBatch.parallelStream()
    .map(this::processMessageFunctional)
    .collect(Collectors.toList())
```

#### **4. CompletableFuture para I/O**
```java
kafkaProducer.sendBatch(successResults)
    .whenComplete((v, error) -> {
        if (error != null) log.errorf(error, "Error sending to MSK");
    });
```

#### **5. Pattern Matching con Result Type**
```java
private static class ProcessResult {
    final boolean success;
    final SentryMessageInput result;
    final String errorMessage;

    static ProcessResult success(SentryMessageInput result) { ... }
    static ProcessResult failure(String errorMessage) { ... }
}
```

---

## Ventajas del Diseño

| Aspecto | Antes (Secuencial) | Ahora (Funcional) |
|---------|-------------------|-------------------|
| **Procesamiento** | for loop | parallelStream() ⚡ |
| **Velocidad** | N × tiempo | N/cores × tiempo |
| **Código** | Imperativo | Declarativo |
| **Errores** | Try-catch anidados | Result pattern |
| **Testabilidad** | Difícil | Funciones puras fáciles de testear |

**Ejemplo**: Batch de 10 documentos en Lambda con 4 vCPUs:
- Secuencial: 10 × 2s = **20 segundos**
- Paralelo: 10/4 × 2s = **5 segundos** ⚡

---

## Testing

### **Test Local (sin AWS)**

```bash
# 1. Iniciar Kafka local
docker run -p 9092:9092 apache/kafka:latest

# 2. Ejecutar app en dev mode
mvn quarkus:dev

# 3. Simular invocación de Lambda ESM
curl -X POST http://localhost:8080/msk/batch \
  -H "Content-Type: application/json" \
  -d @test-batch.json
```

**test-batch.json**:
```json
[
  {
    "metadata": {},
    "data": {
      "item_canonico": {
        "outputs": [...]
      }
    }
  }
]
```

### **Test en AWS Lambda**

```bash
# Invocar Lambda directamente
aws lambda invoke \
  --function-name document-generator-lambda \
  --payload file://test-event.json \
  response.json

# Ver respuesta
cat response.json
```

### **Monitoreo**

```bash
# CloudWatch Logs
aws logs tail /aws/lambda/document-generator-lambda --follow

# Verificar consumer group en MSK
aws kafka describe-cluster --cluster-arn arn:aws:kafka:...
```

---

## Troubleshooting

### **Lambda timeout**
```bash
# Aumentar timeout en Lambda (default 3s → 60s)
aws lambda update-function-configuration \
  --function-name document-generator-lambda \
  --timeout 60
```

### **Mensajes muy grandes**
```properties
# Aumentar límite en application.properties
mp.messaging.outgoing.document-responses-manual.max.request.size=20971520
```

### **Lambda no recibe mensajes**
```bash
# Verificar ESM activo
aws lambda list-event-source-mappings \
  --function-name document-generator-lambda

# Output debe mostrar State: "Enabled"
```

### **Errores en producer**
```bash
# Ver logs de Kafka
aws logs tail /aws/lambda/document-generator-lambda --follow | grep -i kafka
```

---

## Performance Tips

### **1. Ajustar batch size**
```bash
# Batch pequeño (baja latencia): 5-10 mensajes
# Batch grande (alto throughput): 50-100 mensajes
aws lambda update-event-source-mapping \
  --uuid <mapping-id> \
  --batch-size 50
```

### **2. Ajustar memoria Lambda**
```bash
# Más memoria = más vCPUs = más paralelismo
aws lambda update-function-configuration \
  --function-name document-generator-lambda \
  --memory-size 2048  # 2GB = ~1 vCPU, 3GB = ~2 vCPUs
```

### **3. Compresión GZIP**
Ya habilitada en configuración:
```properties
mp.messaging.outgoing.document-responses-manual.compression.type=gzip
```
Reduce tamaño 70-90%.

---

## Resumen

**Implementación final para Lambda + MSK:**

✅ **Clases usadas:**
- `LambdaMskEventHandler` - Handler principal REST
- `DocumentResultProducer` - Kafka producer

✅ **Configuración:**
- `application.properties` - Solo producer config
- Lambda ESM gestiona consumer

✅ **Programación funcional:**
- Streams paralelos
- Funciones puras
- Inmutabilidad
- CompletableFuture

✅ **Performance:**
- Procesamiento paralelo (5-10x más rápido)
- Compresión GZIP
- Batch processing optimizado

---

## Próximos Pasos

1. ✅ Implementado: Lambda + MSK integration
2. ✅ Implementado: Batch processing funcional
3. ✅ Implementado: Kafka producer
4. 🔜 TODO: Dead Letter Queue (DLQ) para errores
5. 🔜 TODO: Métricas CloudWatch custom
6. 🔜 TODO: Alertas SNS para fallos
