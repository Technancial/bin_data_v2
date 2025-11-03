#!/bin/bash

# Script para consumir mensajes del topic de Kafka
# Útil para debugging

TOPIC="document-requests"
KAFKA_BROKER="localhost:9092"

echo "👂 Consumiendo mensajes de Kafka topic: $TOPIC"
echo "Presiona Ctrl+C para detener"
echo ""

docker exec -i kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic $TOPIC \
  --from-beginning \
  --property print.timestamp=true \
  --property print.key=true \
  --property print.value=true
