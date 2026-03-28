# Design : Commande /status

## Contexte

Le bot expose des commandes git (init, push, pull, branch, cherry-pick). Il manque un moyen de voir l'état du dépôt git sans accéder au serveur directement — utile pour diagnostiquer une désync ou vérifier ce qui est en attente de commit.

## Fonctionnalité

Nouvelle commande slash `/status` qui affiche l'état git du dépôt local.

### Format de la réponse

```
**Branche :** `main`
**2 modifié(s), 1 ajouté(s), 1 supprimé(s)**

M  roles/123456789.toml
A  channels/987654321.toml
D  emojis/111222333.toml
```

Si le working tree est propre :
```
Branche : `main` — Rien à signaler (working tree clean)
```

- Préfixe `M` pour fichiers modifiés, `A` pour ajoutés/untracked, `D` pour supprimés/missing
- Troncature à 1900 chars si la liste est trop longue (avec note "... et N fichier(s) supplémentaire(s)")
- Réponse éphémère (`deferReply(true)`)

## Architecture

### `GitOperationService` — nouvelle méthode `gitStatus()`

Retourne un record `StatusSummary` :

```java
public record StatusSummary(
    String branch,
    List<String> modified,
    List<String> added,
    List<String> deleted
) {}

public StatusSummary gitStatus() throws GitAPIException, IOException {
    String branch = currentBranch();
    org.eclipse.jgit.api.Status status = git.status().call();
    List<String> modified = new ArrayList<>(status.getModified());
    List<String> added = new ArrayList<>();
    added.addAll(status.getAdded());
    added.addAll(status.getUntracked());
    List<String> deleted = new ArrayList<>();
    deleted.addAll(status.getRemoved());
    deleted.addAll(status.getMissing());
    Collections.sort(modified);
    Collections.sort(added);
    Collections.sort(deleted);
    return new StatusSummary(branch, modified, added, deleted);
}
```

### `GTD` — nouveau handler `/status`

Enregistrement dans `init()` :
```java
if (!existing.contains("status")) {
    jda.upsertCommand("status", "Afficher l'état git du dépôt").queue();
}
```

Routing dans `onSlashCommandInteraction()` :
- Guard `server.gitOps() == null` → "Git non initialisé"
- Appelle `server.gitOps().gitStatus()`
- Formate la réponse
- Troncature à 1900 chars

## Fichiers à modifier

| Fichier | Action |
|---------|--------|
| `src/main/java/fr/enimaloc/gtd/GitOperationService.java` | Ajouter `StatusSummary` record + `gitStatus()` |
| `src/main/java/fr/enimaloc/gtd/GTD.java` | Enregistrer + router `/status` |
