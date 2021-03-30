ARG UID=1000
ARG GID=1000

FROM maven:3.6-jdk-8 as builder
FROM gradle:6.8-jdk11 as builder
# package jar
COPY . /app/
WORKDIR /app
RUN gradle clean build

FROM openjdk:8-jre-alpine as base
ARG UID
ARG GID
# config user and install timezone data
RUN RUN addgroup -g ${GID} -S appgroup && adduser -u ${UID} -S appuser -G appgroup \
    && apk add --no-cache tzdata \
    && mkdir /app && chmod appuser:appgroup /app

USER appuser
WORKDIR app
# copy jar from builder
COPY --from=builder /app/build/libs/rssthis.jar /app/rssthis.jar

EXPOSE 80

ENTRYPOINT ["java", "-jar", "/app/rssthis.jar"]
CMD ['-p', '80']
