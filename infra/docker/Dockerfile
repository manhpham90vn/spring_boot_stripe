FROM eclipse-temurin:21-jdk-jammy

ARG UID=1000
ARG GID=1000
RUN getent group ${GID} >/dev/null 2>&1 || groupadd -g ${GID} app; \
    id -u ${UID} >/dev/null 2>&1 || useradd -u ${UID} -g ${GID} -m -d /home/app -s /bin/bash app; \
    mkdir -p /home/app/.m2 /workspace; \
    chown -R ${UID}:${GID} /home/app /workspace

ENV HOME=/home/app
WORKDIR /workspace
USER ${UID}:${GID}

ENTRYPOINT ["sh", "-c", "exec ./mvnw -B -ntp spring-boot:run"]
