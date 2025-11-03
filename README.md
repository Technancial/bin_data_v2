# Arquitectura Dual: HTTP + Kafka

Este documento explica cómo la Lambda `document-generator` puede responder tanto a peticiones HTTP como a eventos de Kafka.

---

## 📋 Tabla de Contenidos

1. [Arquitectura General](#arquitectura-general)
2. [Componentes](#componentes)
3. [Flujo HTTP](#flujo-http)
4. [Flujo Kafka](#flujo-kafka)
5. [Configuración AWS](#configuración-aws)
6. [Variables de Entorno](#variables-de-entorno)
7. [Testing Local](#testing-local)
8. [Troubleshooting](#troubleshooting)

---

## 🏗️ Arquitectura General

```
┌─────────────────────────────────────────────────────────────┐
│              AWS Lambda: document-generator                  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         Quarkus Event Detection Layer                  │ │
│  │  (Auto-detecta si el evento es HTTP o Kafka)           │ │
│  └──────────────┬─────────────────────┬───────────────────┘ │
│                 │                     │                      │
│       ┌─────────▼─────────┐  ┌───────▼─────────┐           │
│       │  HTTP Handler     │  │  Kafka Handler  │           │
│       │ (Quarkus REST)    │  │ (Custom Lambda) │           │
│       │                   │  │                 │           │
│       │ DocumentLambda    │  │ KafkaEvent      │           │
│       │ Resource          │  │ Handler         │           │
│       └─────────┬─────────┘  └───────┬─────────┘           │
│                 │                     │                      │
│                 │  ┌──────────────────┘                      │
│                 │  │                                         │
│                 ▼  ▼                                         │
│       ┌──────────────────────┐                              │
│       │ GenerateDocument     │                              │
│       │ UseCase              │                              │
│       │ (Shared Logic)       │                              │
│       └──────────┬───────────┘                              │
│                  │                                           │
│       ┌──────────▼───────────┐                              │
│       │ XDocPdfGenerator     │                              │
│       └──────────┬───────────┘                              │
│                  │                                           │
│                  ▼                                           │
│       ┌──────────────────────┐                              │
│       │   PDF Document       │                              │
│       └──────────┬───────────┘                              │
│                  │                                           │
│     ┌────────────┴────────────┐                             │
│     │                         │                             │
│     ▼                         ▼                             │
│ ┌────────┐            ┌────────────┐                        │
│ │ Return │            │   Save to  │                        │
│ │ Base64 │            │    S3      │                        │
│ └────────┘            └────────────┘                        │
└─────────────────────────────────────────────────────────────┘
     │                         │
     │                         │
     ▼                         ▼
┌──────────┐          ┌─────────────────┐
│  HTTP    │          │  S3 Bucket      │
│ Response │          │ /generated-docs │
└──────────┘          └─────────────────┘

TRIGGERS:
  ▲                         ▲
  │                         │
  │                         │
┌─┴──────────────┐   ┌──────┴─────────────┐
│ API Gateway    │   │ Event Source       │
│ or Function URL│   │ Mapping (Kafka)    │
└────────────────┘   └────────────────────┘
```

---

## 🧩 Componentes

### 1. Handlers (Puntos de Entrada)

#### a) **DocumentLambdaResource** (HTTP)
- **Ubicación:** `infrastructure/lambda/rest/DocumentLambdaResource.java`
- **Trigger:** API Gateway / Lambda Function URL
- **Input:** JSON con `TemplateData`
- **Output:** JSON con `base64Document`
- **Comportamiento:** Síncrono, retorna PDF como Base64

#### b) **KafkaEventHandler** (Eventos de Kafka)
- **Ubicación:** `infrastructure/lambda/kafka/KafkaEventHandler.java`
- **Trigger:** Event Source Mapping de MSK o Kafka self-managed
- **Input:** Batch de mensajes Kafka (KafkaEvent)
- **Output:** Resultado del procesamiento (success/failure counts)
- **Comportamiento:** Asíncrono, guarda PDFs en S3

### 2. Servicios de Dominio

#### **GenerateDocumentUseCase**
- **Capa:** Application
- **Responsabilidad:** Orquesta la generación del documento
- **Compartido por:** Ambos handlers (HTTP y Kafka)

#### **XDocPdfGenerator**
- **Capa:** Infrastructure
- **Responsabilidad:** Implementación concreta de generación PDF
- **Usa:** XDocReport + Freemarker

### 3. Servicios de Infraestructura

#### **S3DocumentStorage**
- **Ubicación:** `infrastructure/lambda/kafka/storage/S3DocumentStorage.java`
- **Responsabilidad:** Guardar documentos en S3 con metadata
- **Usado por:** KafkaEventHandler (no por HTTP handler)

#### **AwsClientProducer**
- **Ubicación:** `infrastructure/config/AwsClientProducer.java`
- **Responsabilidad:** CDI Producer para S3Client
- **Configuración:** Usa credenciales por defecto de AWS

---

## 🔄 Flujo HTTP

### Request
```http
POST /generate HTTP/1.1
Content-Type: application/json

{
  "templatePath": "plantilla.docx",
  "data": {
    "nombre": "Juan Pérez",
    "empresa": "ACME Corp",
    "monto": 5000
  },
  "images": null
}
```

### Procesamiento
1. API Gateway/Function URL invoca la Lambda
2. Quarkus detecta evento HTTP
3. `DocumentLambdaResource.generate()` procesa la petición
4. Mapea DTO → Domain entity (`TemplateRequest`)
5. Ejecuta `GenerateDocumentUseCase`
6. `XDocPdfGenerator` genera el PDF
7. Codifica PDF como Base64
8. Retorna JSON response

### Response
```json
{
  "base64Document": "JVBERi0xLjQKJeLjz9MKMiAwIG9iago..."
}
```

**Tiempo de respuesta:** Síncrono (segundos)

---

## 🎯 Flujo Kafka

### Mensaje en Kafka Topic
```json
{
  "templatePath": "plantilla.docx",
  "data": {
    "nombre": "María Gómez",
    "monto": 7500
  }
}
```

### Procesamiento
1. Event Source Mapping lee batch del topic
2. Invoca Lambda con `KafkaEvent`
3. Quarkus detecta evento Kafka
4. `KafkaEventHandler.handleRequest()` procesa el batch
5. Por cada mensaje:
   - Decodifica valor Base64
   - Deserializa JSON → `TemplateData`
   - Mapea DTO → Domain entity
   - Ejecuta `GenerateDocumentUseCase`
   - `XDocPdfGenerator` genera el PDF
   - `S3DocumentStorage` guarda en S3
6. Retorna resultado del batch

### Resultado
```json
{
  "processedRecords": 2,
  "successCount": 2,
  "failureCount": 0,
  "timestamp": 1699876543210
}
```

### Documento en S3
```
s3://my-documents-bucket/generated-documents/2025/11/03/plantilla-uuid.pdf

Metadata:
  - source: kafka
  - topic: document-requests
  - partition: 0
  - offset: 123
  - timestamp: 1699876543000
```

**Tiempo de respuesta:** Asíncrono (procesamiento por lotes)

---

## ⚙️ Configuración AWS

### 1. Crear Bucket S3

```bash
aws s3 mb s3://my-documents-bucket --region us-east-1

# Configurar lifecycle policy (opcional, para auto-eliminar docs antiguos)
aws s3api put-bucket-lifecycle-configuration \
  --bucket my-documents-bucket \
  --lifecycle-configuration file://s3-lifecycle.json
```

**s3-lifecycle.json:**
```json
{
  "Rules": [
    {
      "Id": "DeleteOldDocuments",
      "Status": "Enabled",
      "Prefix": "generated-documents/",
      "Expiration": {
        "Days": 30
      }
    }
  ]
}
```

### 2. Crear IAM Role para Lambda

```bash
# Crear política para S3 y Kafka
cat > lambda-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:PutObjectAcl"
      ],
      "Resource": "arn:aws:s3:::my-documents-bucket/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "kafka:DescribeCluster",
        "kafka:GetBootstrapBrokers",
        "ec2:CreateNetworkInterface",
        "ec2:DescribeNetworkInterfaces",
        "ec2:DeleteNetworkInterface"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    }
  ]
}
EOF

# Crear la política
aws iam create-policy \
  --policy-name DocumentGeneratorPolicy \
  --policy-document file://lambda-policy.json

# Crear role y asociar
aws iam create-role \
  --role-name DocumentGeneratorRole \
  --assume-role-policy-document file://trust-policy.json

aws iam attach-role-policy \
  --role-name DocumentGeneratorRole \
  --policy-arn arn:aws:iam::<account-id>:policy/DocumentGeneratorPolicy
```

### 3. Crear Lambda Function

```bash
# Build Docker image
docker build -t document-generator .

# Tag para ECR
docker tag document-generator:latest \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest

# Push a ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com

docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest

# Crear función Lambda
aws lambda create-function \
  --function-name document-generator \
  --package-type Image \
  --code ImageUri=<account-id>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest \
  --role arn:aws:iam::<account-id>:role/DocumentGeneratorRole \
  --timeout 60 \
  --memory-size 1024 \
  --environment Variables="{S3_DOCUMENTS_BUCKET=my-documents-bucket}"
```

### 4. Configurar Trigger HTTP

**Opción A: Lambda Function URL**
```bash
aws lambda create-function-url-config \
  --function-name document-generator \
  --auth-type NONE \
  --cors AllowOrigins="*",AllowMethods="POST",AllowHeaders="Content-Type"

# Obtener la URL
aws lambda get-function-url-config \
  --function-name document-generator
```

**Opción B: API Gateway**
```bash
# Crear API HTTP
aws apigatewayv2 create-api \
  --name document-generator-api \
  --protocol-type HTTP \
  --target arn:aws:lambda:us-east-1:<account-id>:function:document-generator
```

### 5. Configurar Event Source Mapping (Kafka)

**Para Amazon MSK:**
```bash
aws lambda create-event-source-mapping \
  --function-name document-generator \
  --event-source-arn arn:aws:kafka:us-east-1:<account-id>:cluster/my-cluster/<uuid> \
  --topics document-requests \
  --starting-position LATEST \
  --batch-size 10 \
  --maximum-batching-window-in-seconds 5
```

**Para Kafka Self-Managed:**
```bash
# Primero, crear secret con credenciales
aws secretsmanager create-secret \
  --name KafkaCredentials \
  --secret-string '{"username":"admin","password":"secret"}'

# Crear event source mapping
aws lambda create-event-source-mapping \
  --function-name document-generator \
  --topics document-requests \
  --source-access-configuration Type=BASIC_AUTH,URI=arn:aws:secretsmanager:us-east-1:<account-id>:secret:KafkaCredentials \
  --self-managed-event-source Endpoints={KAFKA_BOOTSTRAP_SERVERS=["kafka-broker:9092"]} \
  --starting-position LATEST \
  --batch-size 10
```

---

## 🔐 Variables de Entorno

Configurar en la Lambda:

| Variable | Descripción | Ejemplo | Requerida |
|----------|-------------|---------|-----------|
| `S3_DOCUMENTS_BUCKET` | Bucket donde guardar PDFs | `my-documents-bucket` | Sí (Kafka) |
| `S3_DOCUMENTS_PREFIX` | Prefijo para organizar docs | `generated-documents` | No |
| `AWS_REGION` | Región AWS | `us-east-1` | Auto-detectada |

**Configurar:**
```bash
aws lambda update-function-configuration \
  --function-name document-generator \
  --environment Variables="{S3_DOCUMENTS_BUCKET=my-documents-bucket,S3_DOCUMENTS_PREFIX=generated-documents}"
```

---

## 🧪 Testing Local

### 1. Testing HTTP

```bash
# Iniciar Quarkus en dev mode
mvn quarkus:dev -pl infrastructure

# En otra terminal, probar el endpoint
curl -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templatePath": "plantilla.docx",
    "data": {
      "nombre": "Test Local",
      "monto": 1000
    }
  }'
```

### 2. Testing Kafka (con SAM CLI)

```bash
# Instalar AWS SAM CLI
brew install aws-sam-cli

# Probar con evento Kafka
sam local invoke -e events/kafka-event.json

# Ver logs
sam local invoke -e events/kafka-event.json --log-file lambda.log
```

### 3. Testing con Kafka Real (Local)

```bash
# Iniciar Kafka local con Docker
docker run -d --name kafka \
  -p 9092:9092 \
  apache/kafka:latest

# Crear topic
docker exec -it kafka kafka-topics.sh \
  --create \
  --topic document-requests \
  --bootstrap-server localhost:9092

# Publicar mensaje
docker exec -it kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic document-requests

# Pegar JSON (sin codificar):
{"templatePath":"plantilla.docx","data":{"nombre":"Test Kafka"}}
```

---

## 🐛 Troubleshooting

### Lambda no recibe eventos de Kafka

**Verificar Event Source Mapping:**
```bash
aws lambda list-event-source-mappings \
  --function-name document-generator

# Ver estado
aws lambda get-event-source-mapping \
  --uuid <mapping-uuid>
```

**Estados posibles:**
- `Creating` → Se está creando
- `Enabled` → Funcionando correctamente
- `Disabled` → Deshabilitado manualmente
- `Deleting` → Eliminándose

**Habilitar si está disabled:**
```bash
aws lambda update-event-source-mapping \
  --uuid <mapping-uuid> \
  --enabled
```

### Error: Access Denied al guardar en S3

**Verificar permisos IAM:**
```bash
# Ver rol de la Lambda
aws lambda get-function-configuration \
  --function-name document-generator \
  --query 'Role'

# Ver políticas del rol
aws iam list-attached-role-policies \
  --role-name DocumentGeneratorRole
```

**Agregar permiso S3:**
```bash
aws iam attach-role-policy \
  --role-name DocumentGeneratorRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonS3FullAccess
```

### Timeout en generación de documentos

**Aumentar timeout:**
```bash
aws lambda update-function-configuration \
  --function-name document-generator \
  --timeout 120
```

**Aumentar memoria (mejora CPU):**
```bash
aws lambda update-function-configuration \
  --function-name document-generator \
  --memory-size 2048
```

### Ver logs en CloudWatch

```bash
# Ver log streams
aws logs describe-log-streams \
  --log-group-name /aws/lambda/document-generator \
  --order-by LastEventTime \
  --descending

# Ver últimos logs
aws logs tail /aws/lambda/document-generator --follow
```

---

## 📊 Monitoreo y Métricas

### CloudWatch Metrics

Métricas automáticas de Lambda:
- `Invocations` - Total de invocaciones
- `Duration` - Tiempo de ejecución
- `Errors` - Errores no controlados
- `Throttles` - Invocaciones limitadas

### Custom Metrics (Recomendado)

Agregar a `KafkaEventHandler`:
```java
cloudWatch.putMetricData(
    PutMetricDataRequest.builder()
        .namespace("DocumentGenerator")
        .metricData(
            MetricDatum.builder()
                .metricName("DocumentsGenerated")
                .value((double) successCount)
                .unit(StandardUnit.COUNT)
                .build()
        )
        .build()
);
```

---

## 🚀 Siguientes Pasos

1. **DLQ (Dead Letter Queue)** para mensajes fallidos
2. **Retry policy** personalizada para Kafka
3. **Pre-signed URLs** en lugar de guardar en S3
4. **SNS notifications** cuando documento esté listo
5. **Step Functions** para flujos complejos

---

**Última actualización:** 3 de Noviembre, 2025
