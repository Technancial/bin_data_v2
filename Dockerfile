FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /build

# Copiar POMs primero para aprovechar el cache de Docker en dependencias
COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/

# Descargar dependencias (se cachea si no cambian los POMs)
RUN mvn dependency:go-offline -B

# Copiar código fuente de todos los módulos
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src

# Compilar solo el módulo infrastructure (incluye domain y application como dependencias)
RUN mvn clean package -pl infrastructure -am -DskipTests

FROM public.ecr.aws/lambda/java:21

# Copiar el uber-jar (single JAR with all dependencies)
COPY --from=build /build/infrastructure/target/infrastructure-1.0.0-runner.jar ${LAMBDA_TASK_ROOT}/app.jar

# Copiar plantillas desde recursos de producción
# NOTA: Las plantillas están empaquetadas dentro del JAR en /templates/
# pero también las copiamos al filesystem para fallback
COPY --from=build /build/infrastructure/src/main/resources/templates/ ${LAMBDA_TASK_ROOT}/templates/

# Definir el handler de Quarkus Lambda
CMD ["io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"]