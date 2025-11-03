FROM maven:3.9.6-amazoncorretto-21 AS build

WORKDIR /app

COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/

RUN mvn dependency:go-offline -B -f pom.xml

COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src

RUN mvn clean install -DskipTests -f pom.xml

WORKDIR /app/infrastructure

RUN mvn clean install -U

RUN mvn package -DskipTests


FROM public.ecr.aws/lambda/java:21

COPY --from=build /app/infrastructure/target/*-runner.jar /var/task/lib/function.jar

COPY --from=build /app/infrastructure/target/lib /var/task/lib

COPY infrastructure/src/test/resources/plantilla.docx ${LAMBDA_TASK_ROOT}/template/plantilla.docx

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

CMD ["io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"]

