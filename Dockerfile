FROM maven:3.6-jdk-8 as builder

COPY * /app/

WORKDIR /app

RUN mvn clean package

FROM openjdk:8-jre-alpine as base

RUN apk add tzdata

RUN mkdir /app

WORKDIR app

COPY --from=builder /app/target/*-jar-with-dependencies.jar /app/

ENTRYPOINT ["java", "-jar", "/app/*-jar-with-dependencies.jar"]
