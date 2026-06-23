# syntax=docker/dockerfile:1
# Production image cho service "order".
# Build context = ROOT repo (cần root pom aggregator + libs/ mà service phụ thuộc).
#   docker build -f order/Dockerfile --build-arg SERVICE=order .

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
ARG SERVICE=order
# -am: build kem libs/common-core, libs/common-security (reactor tu xep thu tu)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl ${SERVICE} -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
ARG SERVICE=order
RUN useradd -u 1000 -m app
USER 1000
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/*.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
