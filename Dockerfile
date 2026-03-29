# Stage 1 — build du fat JAR
FROM gradle:8-jdk21 AS builder

WORKDIR /build

# Copie les fichiers de build séparément pour bénéficier du cache Docker sur les dépendances
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# Copie les sources et construit le fat JAR
COPY src/ src/
RUN ./gradlew shadowJar --no-daemon -q

# Stage 2 — image runtime
# Debian (jammy) requis : les natives zstd-jni et webp-imageio nécessitent glibc
FROM eclipse-temurin:21-jre-jammy

RUN groupadd -r gtd && useradd -r -g gtd -s /sbin/nologin gtd

WORKDIR /app

COPY --from=builder /build/build/libs/gtd-all.jar ./gtd.jar

RUN mkdir -p /app/data && chown -R gtd:gtd /app

VOLUME /app/data

USER gtd

# GTD_CONFIG : chemin vers le config.toml (généré automatiquement si absent + DISCORD_TOKEN défini)
# DISCORD_TOKEN : token du bot Discord (requis si pas de config.toml monté)
# GIT_TOKEN     : token GitHub (optionnel)
# GTD_DATA_PATH : chemin du dossier data dans le conteneur (défaut : /app/data)
ENV GTD_CONFIG=/app/config.toml

ENTRYPOINT ["java", "-jar", "gtd.jar"]
