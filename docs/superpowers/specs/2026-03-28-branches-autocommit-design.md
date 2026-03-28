# Design : Branch Support + Auto-commit + Cherry-pick

## Contexte

Le projet gitToDiscord synchronise l'état d'un serveur Discord vers un dépôt git.
Les commandes existantes sont `/init`, `/push`, `/pull`, `/archive`.
Ce design ajoute le support des branches, l'auto-commit sur event Discord, et le cherry-pick.

## Fonctionnalités

### 1. Gestion des branches

Nouvelles sous-commandes `/branch` :

| Commande | Comportement |
|----------|-------------|
| `/branch create <name>` | Crée la branche localement et la pousse upstream |
| `/branch switch <name>` | Checkout local uniquement — Discord non modifié, le prochain `/pull` utilise cette branche |
| `/branch list` | Liste les branches locales + distantes, indique la courante |
| `/branch delete <name>` | Supprime locale + distante (interdit si c'est la branche courante) |

`GitConfig.branch` (déjà présent mais inutilisé) devient la branche active persistée, mise à jour à chaque `/branch switch`.

### 2. Auto-commit sur events Discord

Chaque event Discord (rôle, channel, emoji, sticker, guild) déclenche automatiquement :
`git add -A → git commit → git push`

**Délai de 1.5s** avant le commit pour permettre la récupération de la raison depuis l'audit log Discord.

**Coalescing** : si plusieurs events arrivent dans la fenêtre de 1.5s, le timer est reset et le dernier message de commit l'emporte (évite les commits en rafale lors d'opérations bulk).

#### Format des messages de commit

```
role: create Moderator
```

Avec raison audit log (si disponible et si les permissions le permettent) :

```
role: create Moderator

Reason: Added for new moderation team
```

#### Mapping event → message

| Event JDA | Message |
|-----------|---------|
| `RoleCreateEvent` | `role: create <name>` |
| `RoleDeleteEvent` | `role: delete <name>` |
| `GenericRoleUpdateEvent` | `role: update <name>` |
| `ChannelCreateEvent` | `channel: create <name>` |
| `ChannelDeleteEvent` | `channel: delete <name>` |
| `GenericChannelUpdateEvent` | `channel: update <name>` |
| `EmojiAddedEvent` | `emoji: add <name>` |
| `EmojiRemovedEvent` | `emoji: remove <name>` |
| `GenericEmojiUpdateEvent` | `emoji: update <name>` |
| `GuildStickerAddedEvent` | `sticker: add <name>` |
| `GuildStickerRemovedEvent` | `sticker: remove <name>` |
| `GenericGuildStickerUpdateEvent` | `sticker: update <name>` |
| `GenericGuildUpdateEvent` | `guild: update` |

### 3. Cherry-pick

Nouvelle commande `/cherry-pick <commit>` (option STRING obligatoire) :

1. `git cherry-pick <hash>`
2. Appelle `server.pull(guild)` pour appliquer les fichiers modifiés sur Discord
3. `git push`

## Architecture

### Nouveau composant : `GitOperationService`

Encapsule toutes les opérations git. Reçoit le `Git` object de `Server`.

```
GitOperationService
  ├── commitAndPush(String message)        — add-all + commit + push
  ├── commitAfterDelay(String message, Guild guild, ActionType auditType)
  │     — schedule 1.5s, récupère raison audit log, commit + push
  ├── switchBranch(String name)            — checkout existant
  ├── createBranch(String name)            — crée + push upstream
  ├── deleteBranch(String name)            — supprime locale + distante
  ├── listBranches()                       — retourne List<String>
  └── cherryPick(String hash)              — cherry-pick + abort si conflit
```

Toutes les opérations git sont sérialisées via un `ScheduledExecutorService` à **thread unique**.

### Modifications existantes

- **`Server.java`** : instancie `GitOperationService`, expose-le à `DiscordEventSync`. `GitConfig.branch` est utilisé au démarrage pour checkout la bonne branche.
- **`DiscordEventSync.java`** : après chaque `writeFile` / `deleteFile`, appelle `gitOps.commitAfterDelay(message, guild, auditType)`.
- **`GTD.java`** : enregistre `/branch` (4 sous-commandes) et `/cherry-pick`, route dans `onSlashCommandInteraction`.

## Gestion d'erreurs

| Cas | Comportement |
|-----|-------------|
| `git push` échoue (conflit réseau) | Log erreur, pas de retry. Le fichier local est intact, `/push` manuel peut rattraper. |
| Repo non initialisé | Auto-commit silencieusement ignoré. |
| `/branch switch` branche inexistante | Erreur : "Branche `xyz` introuvable. Utilisez `/branch create` d'abord." |
| `/branch delete` branche courante | Erreur : "Impossible de supprimer la branche courante." |
| `/branch create` branche existante | Erreur claire. |
| Cherry-pick hash invalide | Message d'erreur JGit remonté proprement. |
| Cherry-pick avec conflit | Abort automatique + message explicatif. |
| Audit log inaccessible (permissions) | Commit sans raison, pas d'erreur. |

## Fichiers à créer / modifier

| Fichier | Action |
|---------|--------|
| `src/main/java/fr/enimaloc/gtd/GitOperationService.java` | Nouveau |
| `src/main/java/fr/enimaloc/gtd/Server.java` | Modifier — utiliser `GitConfig.branch`, exposer `GitOperationService` |
| `src/main/java/fr/enimaloc/gtd/DiscordEventSync.java` | Modifier — appeler auto-commit après chaque event |
| `src/main/java/fr/enimaloc/gtd/GTD.java` | Modifier — enregistrer et router `/branch` + `/cherry-pick` |
