# 🚀 Document Generator - Lambda con HTTP + Kafka

Servicio de generación de documentos PDF basado en plantillas DOCX/ODT, desplegable en AWS Lambda con soporte para invocaciones HTTP y eventos de Kafka.

Ver **[TESTING-LOCAL.md](TESTING-LOCAL.md)** para testing local completo.

## 📋 Quick Start

```bash
# Levantar stack local (Kafka + S3 + App)
docker-compose up -d

# Setup S3
./scripts/setup-localstack.sh

# Test HTTP
./scripts/test-http-endpoint.sh

# Test Kafka
./scripts/kafka-produce-batch.sh 10
```

## 📚 Documentación

- **[TESTING-LOCAL.md](TESTING-LOCAL.md)** - Guía completa de testing local
- **[ARQUITECTURA-DUAL.md](ARQUITECTURA-DUAL.md)** - Arquitectura HTTP + Kafka
- **[events/README.md](events/README.md)** - Ejemplos de eventos

## 🏗️ Arquitectura Limpia

```
domain/ → application/ → infrastructure/
```

**Última actualización:** 3 de Noviembre, 2025
