# 📄 Document Generation Engine v2

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.24.4-blue.svg)](https://quarkus.io/)
[![AWS Lambda](https://img.shields.io/badge/AWS-Lambda-orange.svg)](https://aws.amazon.com/lambda/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)]()

Sistema de generación de documentos multi-formato (PDF, HTML, TXT) basado en plantillas ODT/DOCX, optimizado para AWS Lambda con soporte HTTP REST y AWS MSK (Kafka).

---

## 📋 Tabla de Contenidos

- [Características](#-características)
- [Arquitectura](#-arquitectura)
- [Módulos del Proyecto](#-módulos-del-proyecto)
- [Requisitos](#-requisitos)
- [Instalación y Build](#-instalación-y-build)
- [Empaquetado Docker](#-empaquetado-docker)
- [Despliegue en AWS Lambda](#-despliegue-en-aws-lambda)
- [Configuración](#-configuración)
- [Testing Local](#-testing-local)
- [Documentación Adicional](#-documentación-adicional)

---

## 🚀 Características

### Generación de Documentos
- ✅ **Múltiples formatos de salida**: PDF, HTML, TXT
- ✅ **Templates soportados**: ODT (LibreOffice), DOCX (Microsoft Word)
- ✅ **Motor de templates**: Freemarker para interpolación de variables
- ✅ **Imágenes embebidas**: Soporte Base64 para imágenes en templates
- ✅ **Variables dinámicas**: Inyección de datos en tiempo de ejecución

### Integración con AWS
- ✅ **AWS Lambda**: Ejecución serverless optimizada
- ✅ **AWS S3**: Almacenamiento de templates y documentos generados
- ✅ **AWS MSK**: Procesamiento batch con Kafka
- ✅ **Multi-protocolo**: S3, HTTP(S), Filesystem para templates

### Arquitectura y Calidad
- ✅ **Clean Architecture**: Domain → Application → Infrastructure
- ✅ **Cache inteligente**: TTL de 2 horas para templates descargados
- ✅ **Log sanitization**: Protección de PII y datos sensibles
- ✅ **Procesamiento funcional**: Streams paralelos para batch

### Modos de Operación
- 🌐 **HTTP REST API**: Invocaciones síncronas vía API Gateway
- 📨 **Kafka/MSK Events**: Procesamiento batch asíncrono
- 🔄 **Dual handler**: Mismo código, múltiples triggers

---

## 🏗 Arquitectura

### Diagrama de Capas

```
┌─────────────────────────────────────────────────────────────┐
│                      AWS Lambda Container                    │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐  │
│  │           Infrastructure Layer (JAX-RS)               │  │
│  │  • DocumentLambdaResource (HTTP Handler)              │  │
│  │  • LambdaMskEventHandler (Kafka Handler)              │  │
│  │  • S3DocumentRepository                               │  │
│  │  • InfraTemplateRepository (Cache)                    │  │
│  │  • Document Generators (PDF, HTML, TXT)               │  │
│  └───────────────────────────────────────────────────────┘  │
│                            ↓                                 │
│  ┌───────────────────────────────────────────────────────┐  │
│  │           Application Layer (Use Cases)               │  │
│  │  • GenerateDocumentUseCase                            │  │
│  └───────────────────────────────────────────────────────┘  │
│                            ↓                                 │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Domain Layer (Business Logic)            │  │
│  │  • TemplateRequest (Value Object)                     │  │
│  │  • DocumentResult (Aggregate)                         │  │
│  │  • TemplateRepository (Port)                          │  │
│  │  • DocumentRepository (Port)                          │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↕
        ┌──────────────────────────────────────────┐
        │         External Services (AWS)          │
        │  • S3 (Templates & Documents)            │
        │  • MSK/Kafka (Event Streaming)           │
        │  • CloudWatch (Logging)                  │
        └──────────────────────────────────────────┘
```

### Flujos de Invocación

#### 1. HTTP REST Flow
```
API Gateway → Lambda (HTTP) → DocumentLambdaResource
                             ↓
                   GenerateDocumentUseCase
                             ↓
                   PDF/HTML/TXT Generator
                             ↓
                   S3DocumentRepository → AWS S3
                             ↓
                   Return JSON response
```

#### 2. Kafka/MSK Flow
```
AWS MSK Topic (Input) → Lambda ESM → LambdaMskEventHandler
                                    ↓
                        Parallel Stream Processing
                                    ↓
                        GenerateDocumentUseCase (x N)
                                    ↓
                        DocumentResultProducer
                                    ↓
                        AWS MSK Topic (Output)
```

---

## 📦 Módulos del Proyecto

### 1. `domain/` - Domain Layer

**Responsabilidad**: Lógica de negocio pura, independiente de frameworks

**Componentes principales**:
```
domain/
├── TemplateRequest.java         # Value Object con datos del template
├── DocumentResult.java           # Aggregate con resultado de generación
├── TemplateRepository.java       # Port para obtener templates
├── DocumentRepository.java       # Port para persistir documentos
└── exception/
    ├── DocumentGenerationException.java
    └── TemplateNotFoundException.java
```

**Reglas de negocio**:
- No depende de ningún módulo externo
- Define interfaces (ports) para infraestructura
- Contiene validaciones de dominio
- Sin anotaciones de frameworks

**Maven**:
```xml
<groupId>pe.soapros</groupId>
<artifactId>domain</artifactId>
<packaging>jar</packaging>
```

---

### 2. `application/` - Application Layer

**Responsabilidad**: Casos de uso y orquestación

**Componentes principales**:
```
application/
└── GenerateDocumentUseCase.java  # Caso de uso principal
```

**Flujo del caso de uso**:
1. Recibe `TemplateRequest` con datos y configuración
2. Obtiene template vía `TemplateRepository`
3. Genera documento en formato solicitado
4. Persiste en S3 si `persist=true`
5. Retorna `DocumentResult` con bytes y paths

**Maven**:
```xml
<groupId>pe.soapros</groupId>
<artifactId>application</artifactId>
<packaging>jar</packaging>
<dependencies>
    <dependency>
        <groupId>pe.soapros</groupId>
        <artifactId>domain</artifactId>
    </dependency>
</dependencies>
```

---

### 3. `infrastructure/` - Infrastructure Layer

**Responsabilidad**: Implementaciones técnicas, adaptadores, frameworks

**Estructura**:
```
infrastructure/
├── config/
│   └── AwsClientProducer.java           # CDI Producer para S3Client
├── repository/
│   ├── InfraTemplateRepository.java     # Implementa TemplateRepository
│   ├── S3DocumentRepository.java        # Implementa DocumentRepository
│   └── downloader/
│       ├── S3TemplateDownloader.java    # Descarga desde S3
│       ├── HttpTemplateDownloader.java  # Descarga desde HTTP(S)
│       └── FileSystemTemplateDownloader.java
├── generation/
│   ├── XDocPdfGenerator.java            # Genera PDF desde ODT/DOCX
│   ├── HtmlTemplateGenerator.java       # Genera HTML con Mustache
│   └── PlainTextGenerator.java          # Genera TXT
├── lambda/
│   ├── rest/
│   │   └── DocumentLambdaResource.java  # Handler HTTP (JAX-RS)
│   └── kafka/
│       ├── LambdaMskEventHandler.java   # Handler Kafka batch
│       └── DocumentResultProducer.java  # Producer de resultados
├── mapper/
│   └── SentryMessageMapper.java         # Mapea DTO complejo a dominio
└── util/
    └── LogSanitizer.java                # Sanitización de logs (PII)
```

**Tecnologías**:
- **Quarkus 3.24.4**: Framework base
- **JAX-RS**: REST endpoints
- **SmallRye Kafka**: Messaging reactivo
- **AWS SDK S3**: Integración con S3
- **XDocReport**: Generación de PDF desde templates
- **Mustache**: Templates HTML
- **Jackson**: Serialización JSON

**Maven**:
```xml
<groupId>pe.soapros</groupId>
<artifactId>infrastructure</artifactId>
<packaging>jar</packaging>
<dependencies>
    <dependency>
        <groupId>pe.soapros</groupId>
        <artifactId>application</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-amazon-lambda-http</artifactId>
    </dependency>
    <!-- ... más dependencias -->
</dependencies>
```

---

## 🔧 Requisitos

### Software Necesario

| Componente | Versión | Propósito |
|------------|---------|-----------|
| **Java JDK** | 21+ | Runtime y compilación |
| **Maven** | 3.9+ | Build y dependencias |
| **Docker** | 24+ | Empaquetado y testing local |
| **AWS CLI** | 2.x | Despliegue a AWS |

### Cuentas y Permisos AWS

- **AWS Account** con permisos para:
  - Lambda (create, update, invoke)
  - S3 (read, write)
  - MSK/Kafka (connect, produce, consume)
  - ECR (push images)
  - CloudWatch Logs (write)
  - IAM (crear execution role)

---

## 🛠 Instalación y Build

### 1. Clonar el Repositorio
```bash
git clone <repository-url>
cd engine-commons-v2
```

### 2. Compilar el Proyecto
```bash
# Compilar todos los módulos
mvn clean install -DskipTests

# Solo compilar sin tests
mvn clean compile

# Ejecutar tests
mvn test
```

### 3. Package para Lambda
```bash
cd infrastructure

# Generar JAR para Lambda (legacy-jar mode)
mvn clean package -DskipTests

# Verificar artefactos generados
ls -lh target/infrastructure-1.0.0-runner.jar
ls -lh target/lib/
```

**Artefactos generados**:
- `infrastructure-1.0.0-runner.jar`: JAR principal
- `target/lib/`: Dependencias externas

---

## 🐳 Empaquetado Docker

### Dockerfile para AWS Lambda

El proyecto incluye un `Dockerfile` optimizado para Lambda:

```dockerfile
# Stage 1: Build
FROM maven:3.9.6-amazoncorretto-21 AS build
WORKDIR /app

# Copy POMs and download dependencies
COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
RUN mvn dependency:go-offline -B

# Copy source and build
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
RUN mvn clean package -DskipTests

# Stage 2: Lambda Runtime
FROM public.ecr.aws/lambda/java:21

# Copy artifacts
COPY --from=build /app/infrastructure/target/*-runner.jar ${LAMBDA_TASK_ROOT}/lib/function.jar
COPY --from=build /app/infrastructure/target/lib ${LAMBDA_TASK_ROOT}/lib

# Handler configuration
CMD ["io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"]
```

### Build de la Imagen

```bash
# Build para arquitectura x86_64
docker build -t document-generator:latest .

# Build para arquitectura ARM64 (Graviton)
docker build --platform linux/arm64 -t document-generator:latest-arm64 .

# Verificar imagen
docker images | grep document-generator
```

### Probar Imagen Localmente

```bash
# Ejecutar container localmente
docker run -p 9000:8080 \
  -e AWS_REGION=us-east-1 \
  -e S3_DOCUMENTS_BUCKET=my-bucket \
  document-generator:latest

# Test con curl
curl -X POST "http://localhost:9000/2015-03-31/functions/function/invocations" \
  -d @events/http-event-simple.json
```

### Push a Amazon ECR

```bash
# Autenticar con ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Crear repositorio (si no existe)
aws ecr create-repository \
  --repository-name document-generator \
  --region us-east-1

# Tag de la imagen
docker tag document-generator:latest \
  <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest

# Push a ECR
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest
```

---

## ☁️ Despliegue en AWS Lambda

### Arquitectura de Despliegue

```
┌─────────────────────────────────────────────────────────────┐
│                         AWS Account                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐        ┌──────────────────┐          │
│  │   API Gateway    │───────▶│  Lambda Function │          │
│  │   (HTTP Trigger) │        │  (Container)     │          │
│  └──────────────────┘        └──────────────────┘          │
│                                       │                       │
│  ┌──────────────────┐                │                       │
│  │    AWS MSK       │────Event───────┘                       │
│  │  (Kafka Trigger) │   Source                               │
│  └──────────────────┘   Mapping                              │
│                                       │                       │
│                     ┌─────────────────┴─────────────────┐   │
│                     │                                     │   │
│                     ▼                                     ▼   │
│              ┌─────────────┐                    ┌──────────┐ │
│              │   S3 Bucket │                    │   MSK    │ │
│              │  (Templates │                    │ (Output  │ │
│              │  & Docs)    │                    │  Topic)  │ │
│              └─────────────┘                    └──────────┘ │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 1. Crear Execution Role

```bash
# Crear policy para Lambda
cat > lambda-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::my-templates-bucket/*",
        "arn:aws:s3:::my-documents-bucket/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "kafka:DescribeCluster",
        "kafka:GetBootstrapBrokers",
        "kafka-cluster:Connect"
      ],
      "Resource": "arn:aws:kafka:us-east-1:*:cluster/*"
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

# Crear IAM role
aws iam create-role \
  --role-name document-generator-lambda-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "lambda.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach policy
aws iam put-role-policy \
  --role-name document-generator-lambda-role \
  --policy-name document-generator-policy \
  --policy-document file://lambda-policy.json
```

### 2. Crear Lambda Function

```bash
# Crear función desde imagen ECR
aws lambda create-function \
  --function-name document-generator \
  --package-type Image \
  --code ImageUri=<account-id>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest \
  --role arn:aws:iam::<account-id>:role/document-generator-lambda-role \
  --timeout 300 \
  --memory-size 2048 \
  --environment Variables='{
    "AWS_REGION":"us-east-1",
    "S3_DOCUMENTS_BUCKET":"my-documents-bucket",
    "S3_DOCUMENTS_PREFIX":"generated-documents",
    "S3_TEMPLATES_BUCKET":"my-templates-bucket",
    "GENERATION_TEMP":"/tmp",
    "KAFKA_BOOTSTRAP_SERVERS":"b-1.mycluster.kafka.us-east-1.amazonaws.com:9092",
    "KAFKA_TOPIC_RESPONSES":"document-responses"
  }' \
  --region us-east-1
```

### 3. Configurar HTTP Trigger (API Gateway)

```bash
# Crear API Gateway HTTP API
aws apigatewayv2 create-api \
  --name document-generator-api \
  --protocol-type HTTP \
  --target arn:aws:lambda:us-east-1:<account-id>:function:document-generator

# Dar permisos a API Gateway para invocar Lambda
aws lambda add-permission \
  --function-name document-generator \
  --statement-id apigateway-invoke \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com
```

### 4. Configurar Kafka Trigger (MSK Event Source Mapping)

```bash
# Crear Event Source Mapping para MSK
aws lambda create-event-source-mapping \
  --function-name document-generator \
  --event-source-arn arn:aws:kafka:us-east-1:<account-id>:cluster/my-msk-cluster/* \
  --topics document-requests \
  --starting-position LATEST \
  --batch-size 10 \
  --maximum-batching-window-in-seconds 5
```

### 5. Actualizar Función (Deployment)

```bash
# Actualizar código desde nueva imagen
aws lambda update-function-code \
  --function-name document-generator \
  --image-uri <account-id>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest

# Actualizar configuración
aws lambda update-function-configuration \
  --function-name document-generator \
  --timeout 300 \
  --memory-size 3008 \
  --environment Variables='{...}'
```

---

## ⚙️ Configuración

### Variables de Entorno Requeridas

#### AWS Configuration

| Variable | Descripción | Ejemplo | Requerido |
|----------|-------------|---------|-----------|
| `AWS_REGION` | Región de AWS | `us-east-1` | ✅ Sí |
| `AWS_ENDPOINT_URL` | Endpoint de S3 (LocalStack dev) | `http://localstack:4566` | ❌ No (solo dev) |

#### S3 Configuration

| Variable | Descripción | Ejemplo | Requerido |
|----------|-------------|---------|-----------|
| `S3_DOCUMENTS_BUCKET` | Bucket para documentos generados | `my-documents-bucket` | ✅ Sí |
| `S3_DOCUMENTS_PREFIX` | Prefijo para organización | `generated-documents` | ❌ No (default: `generated-documents`) |
| `S3_TEMPLATES_BUCKET` | Bucket con templates | `my-templates-bucket` | ✅ Sí |

#### Kafka/MSK Configuration

| Variable | Descripción | Ejemplo | Requerido |
|----------|-------------|---------|-----------|
| `KAFKA_BOOTSTRAP_SERVERS` | Bootstrap servers de MSK | `b-1.mycluster.kafka.us-east-1.amazonaws.com:9092` | ✅ Sí (para Kafka) |
| `KAFKA_TOPIC_RESPONSES` | Tópico de salida | `document-responses` | ✅ Sí (para Kafka) |

#### Application Configuration

| Variable | Descripción | Ejemplo | Requerido |
|----------|-------------|---------|-----------|
| `GENERATION_TEMP` | Directorio temporal | `/tmp` | ❌ No (default: `/tmp`) |

### Archivo application.properties

Las propiedades se configuran en `infrastructure/src/main/resources/application.properties`:

```properties
# AWS S3 Configuration
aws.s3.bucket.documents=${S3_DOCUMENTS_BUCKET:my-documents-bucket}
aws.s3.prefix.documents=${S3_DOCUMENTS_PREFIX:generated-documents}
aws.s3.bucket.templates=${S3_TEMPLATES_BUCKET:nexux-templates}
aws.s3.endpoint=${AWS_ENDPOINT_URL:}
aws.region=${AWS_REGION:us-east-1}

# Kafka Configuration
kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
mp.messaging.outgoing.document-responses-manual.topic=${KAFKA_TOPIC_RESPONSES:document-responses}

# Generation Configuration
app.generation.temp=${GENERATION_TEMP:/tmp}

# Logging Configuration
quarkus.log.level=INFO
%prod.quarkus.log.console.json=true
```

### Perfiles de Ejecución

#### Development (`%dev`)
```bash
# Ejecutar en modo dev
mvn quarkus:dev

# O con variables específicas
AWS_ENDPOINT_URL=http://localhost:4566 \
S3_DOCUMENTS_BUCKET=local-bucket \
mvn quarkus:dev
```

#### Production (`%prod`)
```bash
# Build para producción
mvn clean package -DskipTests -Dquarkus.profile=prod

# Variables deben estar configuradas en Lambda
```

---

## 🧪 Testing Local

### Con Docker Compose

El proyecto incluye `docker-compose.yml` para testing local completo:

```yaml
# docker-compose.yml
services:
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3,lambda
      - DEFAULT_REGION=us-east-1
    volumes:
      - ./localstack-data:/tmp/localstack

  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      AWS_ENDPOINT_URL: http://localstack:4566
      S3_DOCUMENTS_BUCKET: local-documents-bucket
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - localstack
      - kafka
```

#### Iniciar Stack Local

```bash
# Levantar todos los servicios
docker-compose up -d

# Ver logs
docker-compose logs -f app

# Setup inicial de S3 en LocalStack
aws --endpoint-url=http://localhost:4566 s3 mb s3://local-documents-bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://local-templates-bucket
```

### Testing HTTP Endpoint

```bash
# Test simple - Generar PDF
curl -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templatePath": "s3@host:path/to/template.odt",
    "fileType": "pdf",
    "persist": true,
    "data": {
      "nombre": "Juan Pérez",
      "fecha": "2025-11-05"
    }
  }' | jq

# Test completo con imágenes
curl -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d @events/http-event-complete.json
```

### Testing Kafka Handler

```bash
# Producir mensaje a Kafka
docker exec -it kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic document-requests < events/kafka-batch-event.json

# Consumir respuestas
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic document-responses \
  --from-beginning
```

### Tests Unitarios

```bash
# Ejecutar todos los tests
mvn test

# Ejecutar tests de un módulo específico
mvn test -pl domain
mvn test -pl application
mvn test -pl infrastructure

# Test con coverage
mvn clean test jacoco:report
```

---

## 📚 Documentación Adicional

### Documentos del Proyecto

- **[TESTING-LOCAL.md](TESTING-LOCAL.md)** - Guía completa de testing local con Docker y scripts
- **[LOGGING-SECURITY-REVIEW.md](LOGGING-SECURITY-REVIEW.md)** - Auditoría de seguridad de logs y sanitización de PII
- **[SENTRY-MESSAGE-FLOW.md](SENTRY-MESSAGE-FLOW.md)** - Flujo de procesamiento de mensajes Sentry
- **[S3-IMPLEMENTATION-SUMMARY.md](S3-IMPLEMENTATION-SUMMARY.md)** - Detalles de integración con S3
- **[IMPLEMENTATION-SUMMARY.md](IMPLEMENTATION-SUMMARY.md)** - Resumen de implementación técnica

### Endpoints Disponibles

#### HTTP REST API

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/generate` | POST | Genera documento (HTTP síncrono) |
| `/msk/batch` | POST | Procesa batch de Kafka (interno) |
| `/q/health` | GET | Health check |
| `/q/metrics` | GET | Métricas Prometheus |

#### Event Formats

**HTTP Request** (`POST /generate`):
```json
{
  "templatePath": "s3@host:bucket/path/template.odt",
  "fileType": "pdf",
  "persist": true,
  "data": {
    "variable1": "valor1",
    "variable2": "valor2"
  },
  "images": [
    {
      "name": "logo",
      "data": "base64encodedimage...",
      "extension": "png"
    }
  ]
}
```

**Kafka Event** (MSK Input Topic):
```json
{
  "data": {
    "cliente": {
      "nombre": "Cliente",
      "id": "12345"
    },
    "item_canonico": {
      "outputs": [
        {
          "template": "s3@host:path/template.odt",
          "fileType": "pdf",
          "persist": true
        }
      ]
    }
  }
}
```

### Template Protocols

El sistema soporta múltiples protocolos para obtener templates:

| Protocolo | Formato | Ejemplo |
|-----------|---------|---------|
| **S3** | `s3@host:key` | `s3@pe.nexux.talos.dev:2.0/templates/invoice.odt` |
| **HTTP(S)** | `http@url` o `https@url` | `http@https://cdn.example.com/template.docx` |
| **Filesystem** | `fs@/path` | `fs@/shared/templates/report.odt` |
| **Classpath** | `filename` (sin prefijo) | `plantilla.docx` |

### Cache de Templates

- **Ubicación**: `/tmp/templates-cache/`
- **TTL**: 2 horas
- **Naming**: MD5 hash de URI + extensión original
- **Limpieza**: Automática antes de cada descarga

---

## 🔐 Seguridad

### Log Sanitization

El sistema implementa `LogSanitizer` para proteger información sensible:

- ✅ Oculta nombres de buckets S3
- ✅ Oculta paths completos de archivos
- ✅ Oculta hosts de URLs
- ✅ Remueve IPs de mensajes de error
- ✅ No serializa objetos completos con datos de usuario

### Mejores Prácticas Implementadas

- ✅ No se loguean datos personales (PII)
- ✅ No se exponen credenciales
- ✅ Validación de inputs
- ✅ Manejo seguro de excepciones
- ✅ Logs estructurados en JSON (producción)

---

## 🐛 Troubleshooting

### Problema: Lambda timeout

**Solución**: Aumentar timeout y memoria

```bash
aws lambda update-function-configuration \
  --function-name document-generator \
  --timeout 300 \
  --memory-size 3008
```

### Problema: Out of Memory en Lambda

**Causa**: Procesamiento de documentos muy grandes

**Solución**:
1. Aumentar memoria a 3GB o más
2. Reducir tamaño de batch en Kafka ESM
3. Implementar streaming si es necesario

### Problema: Template no encontrado

**Verificar**:
1. Bucket S3 correcto en variables de entorno
2. Permisos IAM para leer S3
3. Path del template correcto con protocolo

```bash
# Verificar permisos S3
aws s3 ls s3://my-templates-bucket/

# Test manual de descarga
aws s3 cp s3://my-templates-bucket/template.odt /tmp/
```

### Problema: Kafka no conecta

**Verificar**:
1. Bootstrap servers correcto
2. Security groups permiten tráfico
3. Lambda está en misma VPC que MSK (si aplica)

---

## 📊 Métricas y Monitoreo

### CloudWatch Metrics

Lambda publica automáticamente:
- Invocaciones
- Duración
- Errores
- Throttles

### Custom Metrics (Prometheus)

Disponible en `/q/metrics`:
```
# HELP document_generation_total Total de documentos generados
# TYPE document_generation_total counter
document_generation_total{format="pdf"} 1234

# HELP document_generation_duration_seconds Duración de generación
# TYPE document_generation_duration_seconds histogram
document_generation_duration_seconds_bucket{format="pdf",le="1.0"} 100
```

### Logs

**Formato de producción** (JSON):
```json
{
  "@timestamp": "2025-11-05T10:30:00.123Z",
  "level": "INFO",
  "logger": "pe.soapros.document.infrastructure.lambda.rest.DocumentLambdaResource",
  "message": "Document generated: file.pdf (1.2 MB)",
  "thread": "executor-thread-1"
}
```

---

## 🤝 Contribución

### Workflow de Desarrollo

1. Fork del repositorio
2. Crear branch: `git checkout -b feature/nueva-funcionalidad`
3. Commit: `git commit -am 'Add: nueva funcionalidad'`
4. Push: `git push origin feature/nueva-funcionalidad`
5. Crear Pull Request

### Estándares de Código

- **Java**: Google Java Style Guide
- **Commits**: Conventional Commits
- **Tests**: Cobertura mínima 80%

---

## 📝 Changelog

### v1.0.0 (2025-11-05)
- ✅ Implementación inicial de Clean Architecture
- ✅ Soporte HTTP REST y Kafka/MSK
- ✅ Generación PDF, HTML, TXT
- ✅ Cache inteligente de templates
- ✅ Log sanitization (seguridad)
- ✅ Empaquetado Docker optimizado
- ✅ Documentación completa

---

## 📄 Licencia

Propietario - SOAPROS

---

## 👥 Autores

- **Development Team** - SOAPROS
- **Architecture** - Clean Architecture DDD

---

## 📞 Soporte

Para soporte técnico:
- 📧 Email: soporte@soapros.pe
- 📖 Docs: [Confluence/Wiki URL]
- 🐛 Issues: [GitHub Issues URL]

---

**Última actualización**: 5 de Noviembre, 2025
**Versión**: 1.0.0
