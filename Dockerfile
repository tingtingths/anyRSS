ARG UID=1000
ARG GID=1000

FROM gradle:6.8-jdk11 as builder
# package jar
COPY . /app/
WORKDIR /app
RUN gradle clean build

FROM openjdk:11-jre-slim as base
ARG UID
ARG GID
# config user and install timezone data
RUN groupadd -g ${GID} appgroup && useradd -u ${UID} -m appuser \
    && apt-get update -yq && apt-get install -yq tzdata ca-certificates

USER appuser
WORKDIR app
# copy jar from builder
COPY --from=builder /app/build/libs/rssthis.jar /home/appuser/rssthis.jar

EXPOSE 80

ENTRYPOINT ["java", "-jar", "/home/appuser/rssthis.jar"]
CMD ["-p", "80"]
