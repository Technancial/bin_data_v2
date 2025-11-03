#!/bin/bash

# Script para publicar un mensaje al topic de Kafka
# Uso: ./kafka-produce-message.sh [mensaje_json]

TOPIC="document-requests"
KAFKA_BROKER="localhost:9092"

if [ -z "$1" ]; then
  # Mensaje por defecto
  MESSAGE='{"templatePath":"plantilla.docx","data":{"codigo":"KAFKA-001","nombre":"Juan Pérez","empresa":"ACME Corp","monto":5000,"fecha":"03/11/2025"},"images":null}'
  echo "📝 Usando mensaje por defecto..."
else
  MESSAGE="$1"
fi

echo "📤 Publicando mensaje a Kafka topic: $TOPIC"
echo "Mensaje: $MESSAGE"
echo ""

# Publicar usando docker exec en el contenedor de Kafka
docker exec -i kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic $TOPIC <<< "$MESSAGE"

if [ $? -eq 0 ]; then
  echo ""
  echo "✅ Mensaje publicado exitosamente!"
  echo ""
  echo "Para consumir mensajes del topic:"
  echo "  ./scripts/kafka-consume-messages.sh"
else
  echo ""
  echo "❌ Error al publicar mensaje"
  exit 1
fi
