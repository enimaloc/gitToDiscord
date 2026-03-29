# Per-Server Git Token Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer le token GitHub global par un token par serveur, passé via `/init <repository> <token>` et persisté dans `data/{guildId}/config.toml`.

**Architecture:** `Server.GitConfig` (TOML par serveur) reçoit un champ `gitToken`. Le constructeur de `Server` lit les credentials depuis ce champ plutôt que depuis `GTD.Config`. `initGit()` accepte le token en paramètre, le persiste dans `GitConfig`, reconstruit les credentials, puis clone. `GTD.Config.gitToken` est supprimé.

**Tech Stack:** JDA slash commands, JGit `UsernamePasswordCredentialsProvider`, Jackson TOML mapper (existant)

---

## File Map

| Fichier | Action |
|---|---|
| `src/main/java/fr/enimaloc/gtd/Server.java` | Ajouter `gitToken` à `GitConfig` (Task 1) ; lire credentials depuis `this.config.gitToken` ; modifier `initGit()` (Task 2) |
| `src/main/java/fr/enimaloc/gtd/GTD.java` | Supprimer `gitToken` de `Config` ; ajouter option `token` à `/init` ; mettre à jour le handler (Task 2) |
| `src/test/java/fr/enimaloc/gtd/ServerGitConfigTest.java` | Nouveau : tests de sérialisation `GitConfig` (Task 1) |

---

## Task 1 : Ajouter `gitToken` à `GitConfig` + tests de sérialisation

Cette tâche est purement additive — elle n'affecte pas les méthodes existantes et ne casse pas la compilation.

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/Server.java`
- Create: `src/test/java/fr/enimaloc/gtd/ServerGitConfigTest.java`

- [ ] **Step 1 : Écrire les tests qui échouent**

Créer `src/test/java/fr/enimaloc/gtd/ServerGitConfigTest.java` :

```java
package fr.enimaloc.gtd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerGitConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void gitConfig_roundtrip_preservesGitToken() throws IOException {
        java.io.File configFile = tempDir.resolve("config.toml").toFile();
        Server.GitConfig config = new Server.GitConfig();
        config.branch = "develop";
        config.gitToken = "ghp_testtoken123";
        GTD.MAPPER.writer().writeValue(configFile, config);

        Server.GitConfig loaded = GTD.MAPPER.readValue(configFile, Server.GitConfig.class);

        assertEquals("develop", loaded.branch);
        assertEquals("ghp_testtoken123", loaded.gitToken);
    }

    @Test
    void gitConfig_missingToken_deserializesAsNull() throws IOException {
        java.io.File configFile = tempDir.resolve("config.toml").toFile();
        // Simulate an existing config.toml without gitToken (backward compat)
        Files.writeString(configFile.toPath(), "branch = 'main'\n");

        Server.GitConfig loaded = GTD.MAPPER.readValue(configFile, Server.GitConfig.class);

        assertEquals("main", loaded.branch);
        assertNull(loaded.gitToken);
    }
}
```

- [ ] **Step 2 : Vérifier que les tests échouent**

```bash
./gradlew test --tests "fr.enimaloc.gtd.ServerGitConfigTest" --no-daemon
```

Résultat attendu : FAIL avec erreur de compilation (`gitToken` n'existe pas encore dans `GitConfig`).

- [ ] **Step 3 : Ajouter `gitToken` à `Server.GitConfig`**

Dans `Server.java`, trouver la classe interne `GitConfig` (dernière ligne du fichier) et remplacer :

```java
public static class GitConfig {
    public String branch = "main";
}
```

Par :

```java
public static class GitConfig {
    public String branch = "main";
    public String gitToken;
}
```

- [ ] **Step 4 : Vérifier que les tests passent**

```bash
./gradlew test --tests "fr.enimaloc.gtd.ServerGitConfigTest" --no-daemon
```

Résultat attendu : BUILD SUCCESSFUL, 2 tests PASS.

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/Server.java src/test/java/fr/enimaloc/gtd/ServerGitConfigTest.java
git commit -m "feat: add gitToken field to Server.GitConfig with TOML serialization"
```

---

## Task 2 : Câbler le token dans Server + mettre à jour GTD.java

Cette tâche modifie la signature de `initGit()` et met à jour `GTD.java` en même temps pour garder le projet compilable.

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/Server.java`
- Modify: `src/main/java/fr/enimaloc/gtd/GTD.java`

- [ ] **Step 1 : Mettre à jour le constructeur de `Server` pour lire depuis `this.config.gitToken`**

Dans `Server.java`, dans le constructeur `Server(GTD.Config config, Guild guild)`, remplacer :

```java
String t = config.gitToken;
this.credentials = (t != null && !t.isBlank())
    ? new UsernamePasswordCredentialsProvider("oauth2", t) : null;
```

Par :

```java
String t = this.config.gitToken;
this.credentials = (t != null && !t.isBlank())
    ? new UsernamePasswordCredentialsProvider("oauth2", t) : null;
```

- [ ] **Step 2 : Mettre à jour `initGit()` pour accepter et persister le token**

Dans `Server.java`, remplacer entièrement la méthode `initGit` :

```java
// Avant
public void initGit(String url, Guild guild) throws IOException {
    if (git != null) throw new IllegalStateException("Git repository already initialized");
    try {
        this.git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(dataPath.resolve("git").toFile())
                .setCredentialsProvider(this.credentials)
                .call();
    } catch (Exception e) {
        throw new RuntimeException("Failed to initialize Git repository", e);
    }
    this.gitOps = new GitOperationService(this.git, this.credentials);
    synchroAll(guild);
    try {
        if (this.git.getRepository().resolve("HEAD") == null) {
            gitOps.commitAndPushInitial("feat: initial Discord state snapshot");
        }
    } catch (GitAPIException e) {
        throw new IOException("Échec du commit initial", e);
    }
}
```

Par :

```java
// Après
public void initGit(String url, String token, Guild guild) throws IOException {
    if (git != null) throw new IllegalStateException("Git repository already initialized");
    this.config.gitToken = token;
    this.credentials = (token != null && !token.isBlank())
        ? new UsernamePasswordCredentialsProvider("oauth2", token) : null;
    GTD.MAPPER.writer().writeValue(dataPath.resolve("config.toml").toFile(), this.config);
    try {
        this.git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(dataPath.resolve("git").toFile())
                .setCredentialsProvider(this.credentials)
                .call();
    } catch (Exception e) {
        throw new RuntimeException("Failed to initialize Git repository", e);
    }
    this.gitOps = new GitOperationService(this.git, this.credentials);
    synchroAll(guild);
    try {
        if (this.git.getRepository().resolve("HEAD") == null) {
            gitOps.commitAndPushInitial("feat: initial Discord state snapshot");
        }
    } catch (GitAPIException e) {
        throw new IOException("Échec du commit initial", e);
    }
}
```

- [ ] **Step 3 : Supprimer `gitToken` de `GTD.Config`**

Dans `GTD.java`, trouver la classe interne `Config` et remplacer :

```java
public static class Config {
    public String dataPath = "./data";
    public String botToken;
    public String gitToken;
}
```

Par :

```java
public static class Config {
    public String dataPath = "./data";
    public String botToken;
}
```

- [ ] **Step 4 : Mettre à jour l'enregistrement de la commande `/init`**

Dans la méthode `init()` de `GTD.java`, remplacer :

```java
if (!existing.contains("init")) {
    jda.upsertCommand("init", "Initialize the git repository")
            .addOption(OptionType.STRING, "repository", "Repository URL", true)
            .queue();
}
```

Par :

```java
if (!existing.contains("init")) {
    jda.upsertCommand("init", "Initialize the git repository")
            .addOption(OptionType.STRING, "repository", "Repository URL", true)
            .addOption(OptionType.STRING, "token", "GitHub/Git token", true)
            .queue();
}
```

- [ ] **Step 5 : Mettre à jour le handler `/init`**

Dans `onSlashCommandInteraction()`, remplacer :

```java
if ("init".equals(event.getFullCommandName())) {
    event.deferReply(true).queue();
    try {
        server.initGit(event.getOption("repository").getAsString(), event.getGuild());
        event.getHook().editOriginal("Git initialisé + état Discord exporté").queue();
    } catch (Exception e) {
        e.printStackTrace();
        event.getHook().editOriginal("Échec de l'init : " + e.getMessage()).queue();
    }
}
```

Par :

```java
if ("init".equals(event.getFullCommandName())) {
    event.deferReply(true).queue();
    try {
        server.initGit(
            event.getOption("repository").getAsString(),
            event.getOption("token").getAsString(),
            event.getGuild()
        );
        event.getHook().editOriginal("Git initialisé + état Discord exporté").queue();
    } catch (Exception e) {
        e.printStackTrace();
        event.getHook().editOriginal("Échec de l'init : " + e.getMessage()).queue();
    }
}
```

- [ ] **Step 6 : Vérifier que tous les tests passent**

```bash
./gradlew test --no-daemon
```

Résultat attendu : BUILD SUCCESSFUL, tous les tests passent.

- [ ] **Step 7 : Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/Server.java src/main/java/fr/enimaloc/gtd/GTD.java
git commit -m "feat: wire per-server token into Server and /init command, remove global gitToken"
```
