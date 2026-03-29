# Docker Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Containeriser le bot GTD via un Dockerfile multi-stage qui accepte la config par env vars ou fichier monté.

**Architecture:** Multi-stage build avec `gradle:8-jdk21` pour produire un fat JAR via shadowJar, puis `eclipse-temurin:21-jre-jammy` (Debian, requis pour les natives zstd/webp) comme runtime. La logique de résolution de config dans `GTD.main()` est extraite en méthode testable qui gère trois cas : fichier existant, env vars, ou erreur explicite.

**Tech Stack:** Docker, Gradle Shadow Plugin `com.gradleup.shadow:8.3.6` (fork maintenu de johnrengelman, requis pour Gradle 9.x), JUnit 5 (existant)

---

## File Map

| Fichier | Action |
|---|---|
| `build.gradle` | Ajouter plugin `com.gradleup.shadow:8.3.6` + manifest Main-Class + `shadowJar { mergeServiceFiles() }` |
| `src/main/java/fr/enimaloc/gtd/GTD.java` | Extraire `resolveConfig()`, enrichir `main()` |
| `src/test/java/fr/enimaloc/gtd/GTDConfigTest.java` | Nouveau : tests pour `resolveConfig()` |
| `Dockerfile` | Nouveau : multi-stage build |
| `.dockerignore` | Nouveau : exclure fichiers inutiles |

---

## Task 1 : Ajouter le plugin Shadow à build.gradle

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1 : Modifier build.gradle**

Remplacer le bloc `plugins` et ajouter le manifest :

```groovy
plugins {
    id 'java'
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

group = 'fr.enimaloc'
version = '1.0-SNAPSHOT'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'net.dv8tion:JDA:6.3.2'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.13.0'
    implementation 'org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r'
    implementation 'org.msgpack:msgpack-core:0.9.8'
    implementation 'com.github.luben:zstd-jni:1.5.5-11'
    implementation 'org.sejda.imageio:webp-imageio:0.1.6'

    testImplementation platform('org.junit:junit-bom:5.10.0')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

jar {
    manifest {
        attributes 'Main-Class': 'fr.enimaloc.gtd.GTD'
    }
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 2 : Vérifier que shadowJar se résout**

```bash
./gradlew shadowJar --no-daemon
```

Résultat attendu : `BUILD SUCCESSFUL` et fichier `build/libs/gitToDiscord-1.0-SNAPSHOT-all.jar` créé.

- [ ] **Step 3 : Commit**

```bash
git add build.gradle
git commit -m "build: add shadow plugin for fat JAR packaging"
```

---

## Task 2 : Extraire et enrichir la résolution de config dans GTD.java

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/GTD.java`
- Create: `src/test/java/fr/enimaloc/gtd/GTDConfigTest.java`

- [ ] **Step 1 : Écrire les tests qui échouent**

Créer `src/test/java/fr/enimaloc/gtd/GTDConfigTest.java` :

```java
package fr.enimaloc.gtd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GTDConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveConfig_existingFile_readsIt() throws IOException {
        File configFile = tempDir.resolve("config.toml").toFile();
        GTD.Config written = new GTD.Config();
        written.botToken = "file-token";
        written.gitToken = "file-git-token";
        written.dataPath = "/custom/data";
        GTD.MAPPER.writer().writeValue(configFile, written);

        GTD.Config config = GTD.resolveConfig(configFile.getAbsolutePath(), Map.of());

        assertEquals("file-token", config.botToken);
        assertEquals("file-git-token", config.gitToken);
        assertEquals("/custom/data", config.dataPath);
    }

    @Test
    void resolveConfig_noFile_withEnvVars_generatesConfig() throws IOException {
        String tomlPath = tempDir.resolve("config.toml").toString();
        Map<String, String> env = Map.of(
            "DISCORD_TOKEN", "env-discord-token",
            "GIT_TOKEN", "env-git-token",
            "GTD_DATA_PATH", "/env/data"
        );

        GTD.Config config = GTD.resolveConfig(tomlPath, env);

        assertEquals("env-discord-token", config.botToken);
        assertEquals("env-git-token", config.gitToken);
        assertEquals("/env/data", config.dataPath);
        assertTrue(new File(tomlPath).exists(), "config.toml should have been written to disk");
    }

    @Test
    void resolveConfig_noFile_noGTDDataPath_defaultsToAppData() throws IOException {
        String tomlPath = tempDir.resolve("config.toml").toString();
        Map<String, String> env = Map.of("DISCORD_TOKEN", "some-token");

        GTD.Config config = GTD.resolveConfig(tomlPath, env);

        assertEquals("/app/data", config.dataPath);
    }

    @Test
    void resolveConfig_noFile_noDiscordToken_throwsIllegalStateException() {
        String tomlPath = tempDir.resolve("config.toml").toString();

        assertThrows(IllegalStateException.class,
            () -> GTD.resolveConfig(tomlPath, Map.of()));
    }
}
```

- [ ] **Step 2 : Vérifier que les tests échouent**

```bash
./gradlew test --tests "fr.enimaloc.gtd.GTDConfigTest" --no-daemon
```

Résultat attendu : FAIL avec `cannot find symbol: method resolveConfig`

- [ ] **Step 3 : Implémenter `resolveConfig` et mettre à jour `main()` dans GTD.java**

Remplacer la méthode `main()` existante et ajouter `resolveConfig()` dans `GTD.java` :

```java
static void main(String... args) throws IOException {
    String tomlPath = System.getenv().getOrDefault("GTD_CONFIG", "./config.toml");
    Config config;
    try {
        config = resolveConfig(tomlPath, System.getenv());
    } catch (IllegalStateException e) {
        System.err.println(e.getMessage());
        System.exit(1);
        return;
    }
    new GTD(config).init();
}

static Config resolveConfig(String tomlPath, Map<String, String> env) throws IOException {
    File configFile = new File(tomlPath);
    if (configFile.exists()) {
        return MAPPER.readValue(configFile, Config.class);
    }
    String discordToken = env.get("DISCORD_TOKEN");
    if (discordToken == null || discordToken.isBlank()) {
        throw new IllegalStateException(
            "[GTD] Aucun fichier de config trouvé à '" + tomlPath + "' et DISCORD_TOKEN n'est pas défini.\n" +
            "Définissez DISCORD_TOKEN ou montez un config.toml à l'emplacement GTD_CONFIG.");
    }
    Config config = new Config();
    config.botToken = discordToken;
    config.gitToken = env.get("GIT_TOKEN");
    String dataPath = env.get("GTD_DATA_PATH");
    config.dataPath = (dataPath != null && !dataPath.isBlank()) ? dataPath : "/app/data";
    MAPPER.writer().writeValue(configFile, config);
    return config;
}
```

Ajouter l'import manquant en haut de `GTD.java` (après les imports existants) :

```java
import java.util.Map;
```

- [ ] **Step 4 : Vérifier que les tests passent**

```bash
./gradlew test --tests "fr.enimaloc.gtd.GTDConfigTest" --no-daemon
```

Résultat attendu : `BUILD SUCCESSFUL`, 4 tests PASS.

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/GTD.java src/test/java/fr/enimaloc/gtd/GTDConfigTest.java
git commit -m "feat: extract resolveConfig() and support env-var-only Docker startup"
```

---

## Task 3 : Créer le Dockerfile

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1 : Créer le Dockerfile**

```dockerfile
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

COPY --from=builder /build/build/libs/gitToDiscord-1.0-SNAPSHOT-all.jar ./gtd.jar

RUN mkdir -p /app/data && chown -R gtd:gtd /app

VOLUME /app/data

USER gtd

# GTD_CONFIG : chemin vers le config.toml (généré automatiquement si absent + DISCORD_TOKEN défini)
# DISCORD_TOKEN : token du bot Discord (requis si pas de config.toml monté)
# GIT_TOKEN     : token GitHub (optionnel)
# GTD_DATA_PATH : chemin du dossier data dans le conteneur (défaut : /app/data)
ENV GTD_CONFIG=/app/config.toml

ENTRYPOINT ["java", "-jar", "gtd.jar"]
```

- [ ] **Step 2 : Vérifier la syntaxe**

```bash
docker build --no-cache -t gtd:latest . 2>&1 | tail -5
```

Résultat attendu : `Successfully built` (ou `naming to docker.io/library/gtd:latest done`).

- [ ] **Step 3 : Commit**

```bash
git add Dockerfile
git commit -m "feat: add multi-stage Dockerfile for containerised deployment"
```

---

## Task 4 : Créer le .dockerignore

**Files:**
- Create: `.dockerignore`

- [ ] **Step 1 : Créer le .dockerignore**

```
.git/
.gradle/
.idea/
build/
data/
net/
docs/
*.toml
*.md
```

- [ ] **Step 2 : Vérifier que .dockerignore est pris en compte**

```bash
docker build -t gtd:latest . --progress=plain 2>&1 | grep -E "COPY|context"
```

Le build ne doit pas copier `.git/`, `build/`, `data/` ni `*.toml`.

- [ ] **Step 3 : Commit**

```bash
git add .dockerignore
git commit -m "chore: add .dockerignore to reduce Docker build context"
```

---

## Vérification finale

- [ ] **Build complet de l'image**

```bash
docker build -t gtd:latest .
```

- [ ] **Test de démarrage avec env vars (sans token réel — vérifie juste le démarrage)**

```bash
docker run --rm -e DISCORD_TOKEN=test gtd:latest 2>&1 | head -5
```

Résultat attendu : le bot tente de se connecter à Discord (erreur d'authentification attendue avec un faux token, pas d'erreur de config).

- [ ] **Test sans DISCORD_TOKEN — vérifie le message d'erreur explicite**

```bash
docker run --rm gtd:latest 2>&1
```

Résultat attendu : message `[GTD] Aucun fichier de config trouvé` et exit code 1.

```bash
echo $?  # doit afficher 1
```
