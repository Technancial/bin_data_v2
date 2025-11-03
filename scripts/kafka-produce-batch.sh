#!/bin/bash

# Script para publicar múltiples mensajes a Kafka
# Simula un batch de procesamiento

TOPIC="document-requests"
COUNT=${1:-5}  # Por defecto 5 mensajes

echo "📤 Publicando $COUNT mensajes al topic: $TOPIC"
echo ""

for i in $(seq 1 $COUNT); do
  MESSAGE=$(cat <<EOF
{
  "templatePath": "plantilla.docx",
  "data": {
    "nombre": "Cliente $i",
    "empresa": "Empresa $i SA",
    "monto": $((RANDOM % 10000 + 1000)),
    "fecha": "03/11/2025"
  },
  "images": null
}
EOF
)

  echo "📝 Mensaje $i/$COUNT"
  docker exec -i kafka kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic $TOPIC <<< "$MESSAGE"

  sleep 0.5  # Pequeña pausa entre mensajes
done

echo ""
echo "✅ $COUNT mensajes publicados!"
echo ""
echo "Para ver los logs de procesamiento:"
echo "  docker logs -f document-generator"
