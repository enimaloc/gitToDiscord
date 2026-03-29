# Per-Server Git Token — Design Spec

**Date:** 2026-03-29
**Status:** Approved

## Overview

Permettre la configuration d'un token GitHub par serveur Discord via `/init <repository> <token>`. Le token global (`GTD.Config.gitToken`) est supprimé : chaque serveur doit fournir son propre token à l'initialisation.

---

## Architecture

### Changements de données

`Server.GitConfig` (stocké dans `data/{guildId}/config.toml`) reçoit un champ `gitToken` :

```java
public static class GitConfig {
    public String branch = "main";
    public String gitToken;   // nouveau — token GitHub/Git du serveur
}
```

`GTD.Config.gitToken` est **supprimé**. `GTD.Config` ne contient plus que `dataPath` et `botToken`.

Au démarrage de `Server`, le `CredentialsProvider` est construit depuis `this.config.gitToken` (per-server) au lieu du token global. Si `gitToken` est null (serveur pas encore initialisé), `credentials` reste null — même comportement qu'avant.

---

## Commande `/init`

Le slash command reçoit un second paramètre obligatoire `token` :

```
/init <repository> <token>
```

**Enregistrement** dans `GTD.java` :
```java
jda.upsertCommand("init", "Initialize the git repository")
    .addOption(OptionType.STRING, "repository", "Repository URL", true)
    .addOption(OptionType.STRING, "token", "GitHub/Git token", true)
    .queue();
```

**Handler** dans `GTD.java` :
```java
String url   = event.getOption("repository").getAsString();
String token = event.getOption("token").getAsString();
server.initGit(url, token, event.getGuild());
```

**`Server.initGit()`** reçoit le token, met à jour `this.config.gitToken`, reconstruit `this.credentials`, persiste `GitConfig` sur disque, puis clone le dépôt avec les nouvelles credentials.

---

## Fichiers modifiés

| Fichier | Changement |
|---|---|
| `src/main/java/fr/enimaloc/gtd/Server.java` | Ajouter `gitToken` à `GitConfig` ; adapter constructeur pour lire `config.gitToken` ; modifier signature `initGit(url, token, guild)` |
| `src/main/java/fr/enimaloc/gtd/GTD.java` | Supprimer `gitToken` de `Config` ; mettre à jour commande `/init` et son handler |

---

## Gestion des erreurs

Aucun changement : si le token est invalide, JGit lève une exception qui remonte au handler `/init` et produit un message d'erreur dans Discord. Le comportement existant est conservé.

---

## Sécurité

Le token est stocké en clair dans `data/{guildId}/config.toml`. Même niveau de sécurité qu'avant (le token global était déjà en clair dans `config.toml`). Ce fichier est exclu de git via `.gitignore` (`data/`).

---

## Tests

Vérifier que `GitConfig` persiste correctement le token après `initGit()` : lire le fichier `data/{guildId}/config.toml` après l'appel et vérifier que `gitToken` est bien écrit. Les opérations git réelles (clone, push) ne sont pas mockées.
