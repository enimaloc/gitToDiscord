# Docker Support — Design Spec

**Date:** 2026-03-29
**Status:** Approved

## Overview

Ajouter un support Docker au bot Discord `gitToDiscord` (GTD) pour permettre un déploiement containerisé sans prérequis local au-delà de Docker. La configuration sera injectée via variables d'environnement (secrets) ou fichier `config.toml` monté en volume.

---

## Architecture

### Multi-stage Dockerfile

**Stage 1 — builder (`gradle:8-jdk21`)**
- Copie le Gradle wrapper et les fichiers de build en premier (layer mis en cache si les dépendances n'ont pas changé)
- Copie les sources
- Exécute `./gradlew shadowJar` pour produire un fat JAR incluant toutes les dépendances

**Stage 2 — runtime (`eclipse-temurin:21-jre-jammy`)**
- Base Debian (pas Alpine) — requis par les dépendances natives `zstd-jni` et `webp-imageio` qui nécessitent `glibc`
- Copie le fat JAR depuis le stage builder
- Crée un utilisateur non-root `gtd:gtd` (sécurité)
- `WORKDIR /app`
- `VOLUME /app/data`
- `ENTRYPOINT ["java", "-jar", "gtd-1.0-SNAPSHOT-all.jar"]`

Pas de port exposé — le bot Discord utilise uniquement des connexions WebSocket sortantes.

---

## Gestion de la configuration

`GTD.main()` est enrichi pour gérer trois cas au démarrage :

1. **Fichier monté** : si `GTD_CONFIG` pointe vers un fichier existant → comportement actuel (lecture du TOML)
2. **Env vars** : si `GTD_CONFIG` n'existe pas mais que `DISCORD_TOKEN` est définie → génère un `config.toml` minimal à `/app/config.toml` avec les valeurs de `DISCORD_TOKEN`, `GIT_TOKEN`, et `GTD_DATA_PATH` (défaut : `/app/data`)
3. **Aucune config** : si ni fichier ni `DISCORD_TOKEN` → log d'erreur explicite + `System.exit(1)`

`GTD_DATA_PATH` est une nouvelle env var permettant de surcharger `dataPath` sans monter un fichier TOML complet.

---

## Fichiers modifiés/créés

| Fichier | Action | Description |
|---|---|---|
| `Dockerfile` | Créer | Multi-stage build |
| `.dockerignore` | Créer | Exclure `.gradle/`, `build/`, `data/`, `.git/`, `*.toml` |
| `build.gradle` | Modifier | Ajouter le plugin `com.github.johnrengelman.shadow` |
| `GTD.java` | Modifier | Enrichir `main()` pour la génération de config depuis env vars |

---

## Utilisation

```bash
# Build
docker build -t gtd:latest .

# Lancement avec env vars (volume nommé pour data)
docker run -d \
  -e DISCORD_TOKEN=xxx \
  -e GIT_TOKEN=yyy \
  -v gtd-data:/app/data \
  gtd:latest

# Lancement avec config.toml monté (bind mount pour data)
docker run -d \
  -e GTD_CONFIG=/app/config.toml \
  -v ./config.toml:/app/config.toml:ro \
  -v ./data:/app/data \
  gtd:latest
```

---

## Contraintes

- L'utilisateur `gtd` dans le conteneur doit avoir accès en écriture au volume `/app/data`
- Le nom du JAR shadowJar est `gtd-1.0-SNAPSHOT-all.jar` (convention Gradle Shadow)
- `config.toml` ne doit pas être copié dans l'image (`.dockerignore`) — il peut contenir des secrets
