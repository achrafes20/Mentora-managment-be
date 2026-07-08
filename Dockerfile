# =====================================================================
#  Dockerfile — Mentora RH Backend
#  Build multi-stage : 1) Maven build  2) JRE runtime léger
#  Image de base : eclipse-temurin:21-jdk-alpine (JDK pour build)
#                  eclipse-temurin:21-jre-alpine  (JRE pour runtime)
# =====================================================================

# --- Stage 1 : Build ---
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Copier les fichiers Maven en premier pour profiter du cache des couches
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Télécharger les dépendances (couche mise en cache si pom.xml ne change pas)
RUN ./mvnw dependency:go-offline -q

# Copier les sources et compiler
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# --- Stage 2 : Runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime

# Utilisateur non-root (NFR-SEC)
RUN addgroup -S rh && adduser -S rh -G rh
USER rh

WORKDIR /app

# Copier uniquement le JAR final
COPY --from=build /build/target/*.jar app.jar

# Port exposé (configurable via SERVER_PORT)
EXPOSE 8080

# Healthcheck interne Docker
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
