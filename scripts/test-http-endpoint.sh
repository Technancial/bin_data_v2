#!/bin/bash

# Script para probar el endpoint HTTP

URL="http://localhost:8080/generate"

echo "🧪 Probando endpoint HTTP: $URL"
echo ""

PAYLOAD='{
  "templatePath": "plantilla.docx",
  "data": {
    "codigo": "TEST-001",
    "nombre": "Test HTTP",
    "empresa": "Test Company",
    "monto": 9999,
    "fecha": "03/11/2025"
  },
  "images": null
}'

echo "📝 Payload:"
echo "$PAYLOAD" | jq .
echo ""

echo "📤 Enviando petición..."
RESPONSE=$(curl -s -X POST "$URL" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

if [ $? -eq 0 ]; then
  echo ""
  echo "✅ Respuesta recibida:"
  echo "$RESPONSE" | jq .

  # Guardar PDF decodificado (opcional)
  if command -v jq &> /dev/null; then
    BASE64_DOC=$(echo "$RESPONSE" | jq -r '.base64Document')
    if [ "$BASE64_DOC" != "null" ] && [ -n "$BASE64_DOC" ]; then
      echo ""
      echo "💾 Guardando PDF decodificado en test-output.pdf..."
      echo "$BASE64_DOC" | base64 -d > test-output.pdf
      echo "✅ PDF guardado!"
      echo "   Abrir con: open test-output.pdf"
    fi
  fi
else
  echo ""
  echo "❌ Error en la petición"
  exit 1
fi
