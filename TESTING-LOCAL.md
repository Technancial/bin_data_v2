dock# 🧪 Guía de Testing Local - Kafka + S3 + Lambda

Esta guía te muestra cómo probar localmente toda la arquitectura usando Docker Compose.

---

## 📋 Stack Completo

El `docker-compose.yml` incluye:

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| **Zookeeper** | 2181 | Coordinación de Kafka |
| **Kafka** | 9092 | Message broker |
| **Kafka UI** | 8090 | Interface web para Kafka |
| **LocalStack** | 4566 | Simula AWS S3 localmente |
| **document-generator** | 8080 | Tu aplicación Quarkus |

---

## 🚀 Inicio Rápido

### 1. Levantar el Stack Completo

```bash
# Levantar todos los servicios
docker-compose up -d

# Ver logs de todos los servicios
docker-compose logs -f

# Ver logs solo de la aplicación
docker logs -f document-generator
```

**Tiempo de inicio:** ~30-60 segundos (esperar a que Kafka esté listo)

### 2. Verificar que Todo Esté Corriendo

```bash
# Ver estado de contenedores
docker-compose ps

# Debería mostrar 5 servicios running:
# - zookeeper
# - kafka
# - kafka-ui
# - localstack
# - document-generator
```

### 3. Setup Inicial de LocalStack (S3)

```bash
# Ejecutar script de setup (solo primera vez)
./scripts/setup-localstack.sh
```

Este script crea el bucket `local-documents-bucket` en LocalStack.

---

## 🧪 Pruebas

### Opción A: Endpoint HTTP (Generación Individual)

#### Método 1: Usando el script

```bash
./scripts/test-http-endpoint.sh
```

Este script:
- Envía una petición POST al endpoint `/generate`
- Muestra la respuesta JSON
- Guarda el PDF decodificado en `test-output.pdf`

#### Método 2: Usando curl directamente

```bash
curl -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d '{
    "templatePath": "plantilla.docx",
    "data": {
      "nombre": "Test Manual",
      "empresa": "Mi Empresa",
      "monto": 7500,
      "fecha": "03/11/2025"
    },
    "images": null
  }' | jq .
```

**Respuesta esperada:**
```json
{
  "base64Document": "JVBERi0xLjQKJeL..."
}
```

#### Decodificar el PDF

```bash
# Guardar respuesta en variable
RESPONSE=$(curl -s -X POST http://localhost:8080/generate -H "Content-Type: application/json" -d @payload.json)

# Extraer y decodificar
echo $RESPONSE | jq -r '.base64Document' | base64 -d > documento.pdf

# Abrir PDF
open documento.pdf  # macOS
xdg-open documento.pdf  # Linux
```

---

### Opción B: Kafka Batch (Procesamiento Asíncrono)

#### Paso 1: Publicar un Mensaje a Kafka

```bash
# Publicar mensaje individual
./scripts/kafka-produce-message.sh

# O publicar batch de 5 mensajes
./scripts/kafka-produce-batch.sh 5

# O publicar batch de 10 mensajes
./scripts/kafka-produce-batch.sh 10
```

#### Paso 2: Ver los Logs de Procesamiento

```bash
# Ver logs en tiempo real
docker logs -f document-generator

# Deberías ver:
# - "Received Kafka batch with X messages"
# - "Processing Kafka message 0 - template: plantilla.docx"
# - "Document generated successfully (XXXX bytes)"
# - "Document saved to S3: generated-documents/2025/11/03/plantilla-uuid.pdf"
```

#### Paso 3: Verificar Documentos en S3 (LocalStack)

```bash
# Listar documentos generados
aws --endpoint-url=http://localhost:4566 s3 ls s3://local-documents-bucket/generated-documents/ --recursive

# Descargar un documento
aws --endpoint-url=http://localhost:4566 s3 cp \
  s3://local-documents-bucket/generated-documents/2025/11/03/plantilla-XXXXX.pdf \
  downloaded.pdf

# Abrir documento
open downloaded.pdf
```

---

## 🎛️ Kafka UI

Abre tu navegador en: **http://localhost:8090**

Desde Kafka UI puedes:
- ✅ Ver topics existentes
- ✅ Ver mensajes en cada topic
- ✅ Publicar mensajes manualmente
- ✅ Ver consumer groups
- ✅ Monitorear throughput

### Publicar Mensaje desde Kafka UI

1. Ir a **Topics** → `document-requests`
2. Click en **Produce Message**
3. Pegar JSON:
   ```json
   {
     "templatePath": "plantilla.docx",
     "data": {
       "nombre": "Desde Kafka UI",
       "monto": 3000
     }
   }
   ```
4. Click **Produce**

---

## 🔍 Debugging

### Ver Mensajes en el Topic

```bash
# Consumir mensajes (modo lectura)
./scripts/kafka-consume-messages.sh

# Presiona Ctrl+C para salir
```

### Logs por Servicio

```bash
# Kafka
docker logs -f kafka

# Aplicación
docker logs -f document-generator

# LocalStack
docker logs -f localstack

# Todos
docker-compose logs -f
```

### Entrar a un Contenedor

```bash
# Entrar al contenedor de Kafka
docker exec -it kafka bash

# Listar topics
kafka-topics --bootstrap-server localhost:9092 --list

# Ver detalles del topic
kafka-topics --bootstrap-server localhost:9092 --describe --topic document-requests

# Salir
exit
```

---

## 🧹 Limpieza

### Detener Todo

```bash
# Detener servicios pero mantener volúmenes
docker-compose down

# Detener y eliminar volúmenes (datos de Kafka y LocalStack)
docker-compose down -v

# Eliminar imágenes también
docker-compose down -v --rmi all
```

### Limpiar Documentos en S3

```bash
# Eliminar todos los documentos generados
aws --endpoint-url=http://localhost:4566 s3 rm \
  s3://local-documents-bucket/generated-documents/ --recursive
```

---

## 🔧 Configuración Avanzada

### Cambiar Número de Particiones del Topic

```bash
docker exec -it kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --alter \
  --topic document-requests \
  --partitions 3
```

### Crear Topic Manualmente (opcional)

```bash
docker exec -it kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic document-results \
  --partitions 3 \
  --replication-factor 1
```

### Hot Reload (Desarrollo)

El código fuente está montado como volumen, así que:

1. Modifica código en `infrastructure/src`
2. Guarda el archivo
3. Quarkus detecta cambios automáticamente
4. Recompila y recarga (hot reload)

**No necesitas reiniciar el contenedor!**

---

## 📊 Flujo de Datos Completo

### Flujo HTTP

```
1. curl → POST /generate
2. DocumentLambdaResource
3. GenerateDocumentUseCase
4. XDocPdfGenerator → PDF
5. Base64 encode
6. Response JSON → Cliente
```

### Flujo Kafka

```
1. kafka-produce-message.sh
2. Kafka Topic: document-requests
3. POST /kafka/batch (interno)
4. KafkaEventHandler
5. For each message:
   - GenerateDocumentUseCase
   - XDocPdfGenerator → PDF
   - S3DocumentStorage → LocalStack
6. Response: {successCount, s3Keys}
```

---

## ⚠️ Troubleshooting

### Error: "Connection refused" al conectar a Kafka

**Causa:** Kafka aún no está listo

**Solución:**
```bash
# Esperar a que Kafka esté healthy
docker-compose ps

# Ver logs de Kafka
docker logs kafka

# Esperar mensaje: "Kafka Server started"
```

### Error: "Bucket does not exist"

**Causa:** LocalStack no tiene el bucket creado

**Solución:**
```bash
./scripts/setup-localstack.sh
```

### Error: Template no encontrado

**Causa:** La plantilla `plantilla.docx` no existe

**Solución:**
```bash
# Verificar que existe
ls infrastructure/src/main/resources/templates/plantilla.docx

# Si no existe, copiar una plantilla DOCX cualquiera
cp mi-plantilla.docx infrastructure/src/main/resources/templates/plantilla.docx
```

### La aplicación no se levanta

**Causa:** Puerto 8080 ocupado

**Solución:**
```bash
# Ver qué está usando el puerto
lsof -i :8080

# Cambiar puerto en docker-compose.yml
# ports:
#   - "8081:8080"  # Cambiar 8080 a 8081
```

### Hot Reload no funciona

**Causa:** Volumen no está montado correctamente

**Solución:**
```bash
# Recrear contenedores
docker-compose down
docker-compose up -d --build
```

---

## 📈 Monitoreo

### Ver Métricas en Tiempo Real

```bash
# CPU y Memoria
docker stats

# Solo la aplicación
docker stats document-generator
```

### Logs Estructurados

Los logs están en formato JSON (producción) o texto plano (dev).

```bash
# Filtrar solo errores
docker logs document-generator 2>&1 | grep ERROR

# Filtrar solo mensajes de Kafka
docker logs document-generator 2>&1 | grep "Kafka batch"
```

---

## 🎯 Casos de Uso de Testing

### Caso 1: Probar Generación Simple

```bash
./scripts/test-http-endpoint.sh
```

### Caso 2: Probar Procesamiento por Lotes

```bash
# Publicar 10 mensajes
./scripts/kafka-produce-batch.sh 10

# Ver logs de procesamiento
docker logs -f document-generator

# Verificar documentos en S3
aws --endpoint-url=http://localhost:4566 s3 ls \
  s3://local-documents-bucket/generated-documents/ --recursive
```

### Caso 3: Probar Manejo de Errores

```bash
# Publicar mensaje con template inválido
./scripts/kafka-produce-message.sh '{
  "templatePath": "../../../etc/passwd",
  "data": {"nombre": "Hacker"}
}'

# Ver logs de error
docker logs document-generator

# Deberías ver: "INVALID_TEMPLATE_PATH"
```

### Caso 4: Stress Test

```bash
# Publicar 100 mensajes
./scripts/kafka-produce-batch.sh 100

# Monitorear recursos
docker stats document-generator
```

---

## 🔗 URLs Útiles

| Servicio | URL | Uso |
|----------|-----|-----|
| **Aplicación HTTP** | http://localhost:8080/generate | Endpoint REST |
| **Kafka UI** | http://localhost:8090 | Visualizar Kafka |
| **LocalStack** | http://localhost:4566 | S3 local |
| **Debug Port** | localhost:5005 | Remote debugging |

---

## 🎓 Siguiente Paso: Desplegar a AWS

Cuando estés listo para desplegar a AWS:

1. Build imagen de producción:
   ```bash
   docker build -t document-generator .
   ```

2. Tag y push a ECR:
   ```bash
   docker tag document-generator:latest <account>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest
   docker push <account>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest
   ```

3. Actualizar Lambda:
   ```bash
   aws lambda update-function-code \
     --function-name document-generator \
     --image-uri <account>.dkr.ecr.us-east-1.amazonaws.com/document-generator:latest
   ```

4. Configurar Event Source Mapping para Kafka real

---

**¡Todo listo para testing local completo!** 🎉

Si tienes problemas, revisa la sección de Troubleshooting o abre un issue.
