ARG APP_UID=1000
ARG APP_GID=1000

FROM maven:3.6-jdk-8 as builder
# package jar
COPY . /app/
WORKDIR /app
RUN mvn clean package

FROM openjdk:8-jre-alpine as base
ARG APP_UID
ARG APP_GID
# config user and install timezone data
RUN RUN addgroup -g ${APP_GID} -S appgroup && adduser -u ${APP_UID} -S appuser -G appgroup \
    && apk add --no-cache tzdata \
    && mkdir /app && chmod appuser:appgroup /app

USER appuser
WORKDIR app
# copy jar from builder
COPY --from=builder /app/target/rssthis-jar-with-dependencies.jar /app/rssthis.jar

EXPOSE 58080

ENTRYPOINT ["java", "-jar", "/app/rssthis.jar"]
CMD ['-p', '58080']
