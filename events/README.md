# Event Samples for Testing

Este directorio contiene eventos de prueba para testing local de la Lambda.

## Archivos

### `http-event.json`
Evento HTTP simulando una petición POST a `/generate`.

**Uso:**
```bash
# Con AWS SAM CLI
sam local invoke -e events/http-event.json

# Con Quarkus Dev Mode (enviar HTTP directo)
curl -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d @events/http-payload.json
```

### `kafka-event.json`
Evento de Kafka simulando 2 mensajes de un topic `document-requests`.

**Uso:**
```bash
# Con AWS SAM CLI
sam local invoke DocumentGenerator -e events/kafka-event.json

# Decodificar valores (están en Base64)
echo "eyJ0ZW1wbGF0ZVBhdGgiOiJwbGFudGlsbGEuZG9jeCIsImRhdGEiOnsibm9tYnJlIjoiSnVhbiBQw6lyZXoiLCJlbXByZXNhIjoiQUNNRSBDb3JwIiwibW9udG8iOjUwMDAsImZlY2hhIjoiMDMvMTEvMjAyNSJ9LCJpbWFnZXMiOm51bGx9" | base64 -d
```

**Resultado decodificado del primer mensaje:**
```json
{
  "templatePath": "plantilla.docx",
  "data": {
    "nombre": "Juan Pérez",
    "empresa": "ACME Corp",
    "monto": 5000,
    "fecha": "03/11/2025"
  },
  "images": null
}
```

**Resultado decodificado del segundo mensaje:**
```json
{
  "templatePath": "plantilla.docx",
  "data": {
    "nombre": "María Gómez",
    "empresa": "Tech Startup",
    "monto": 7500,
    "fecha": "03/11/2025"
  },
  "images": null
}
```

## Generar Eventos Custom

### HTTP Event
Para crear tu propio evento HTTP, usa este formato:
```json
{
  "httpMethod": "POST",
  "path": "/generate",
  "body": "{\"templatePath\":\"tu-plantilla.docx\",\"data\":{...}}",
  "isBase64Encoded": false
}
```

### Kafka Event
Para crear un mensaje Kafka:

1. Crea el payload JSON:
```json
{
  "templatePath": "plantilla.docx",
  "data": {
    "campo1": "valor1"
  }
}
```

2. Codifica en Base64:
```bash
echo -n '{"templatePath":"plantilla.docx","data":{"campo1":"valor1"}}' | base64
```

3. Usa el valor Base64 en el campo `value` del evento Kafka.

## Testing con Kafka Real

Para publicar a un topic real de Kafka:

```bash
# Usando kafka-console-producer
kafka-console-producer --bootstrap-server localhost:9092 --topic document-requests

# Luego pega el JSON (sin codificar):
{"templatePath":"plantilla.docx","data":{"nombre":"Test"}}
```

## AWS SAM Template

Si usas SAM, crea un `template.yaml`:

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31

Resources:
  DocumentGenerator:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: document-generator
      PackageType: Image
      ImageUri: document-generator:latest
      Timeout: 60
      MemorySize: 1024
      Events:
        HttpApi:
          Type: HttpApi
          Properties:
            Path: /generate
            Method: POST
        KafkaEvent:
          Type: MSK
          Properties:
            Stream: arn:aws:kafka:us-east-1:123456789012:cluster/...
            StartingPosition: LATEST
            Topics:
              - document-requests
```
