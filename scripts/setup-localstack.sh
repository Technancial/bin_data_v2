#!/bin/bash

# Script para inicializar LocalStack con el bucket S3
# Ejecutar después de que LocalStack esté corriendo

set -e

echo "🚀 Configurando LocalStack..."

# Esperar a que LocalStack esté listo
echo "⏳ Esperando a que LocalStack esté listo..."
until curl -s http://localhost:4566/_localstack/health | grep -q "running"; do
  echo "Esperando..."
  sleep 2
done

echo "✅ LocalStack está listo!"

# Configurar AWS CLI para usar LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Crear buckets S3
echo "📦 Creando bucket S3 para documentos: local-documents-bucket..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://local-documents-bucket 2>/dev/null || echo "Bucket ya existe"

echo "📦 Creando bucket S3 para templates: local-templates-bucket..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://local-templates-bucket 2>/dev/null || echo "Bucket ya existe"

# Verificar buckets
echo "🔍 Verificando buckets..."
aws --endpoint-url=http://localhost:4566 s3 ls

echo "✅ Setup de LocalStack completado!"
echo ""
echo "Para ver el contenido de los buckets:"
echo "  aws --endpoint-url=http://localhost:4566 s3 ls s3://local-documents-bucket/ --recursive"
echo "  aws --endpoint-url=http://localhost:4566 s3 ls s3://local-templates-bucket/ --recursive"
