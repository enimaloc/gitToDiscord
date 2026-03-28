# /status Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `/status` slash command that shows the current git repository state (branch + modified/added/deleted files) formatted for Discord.

**Architecture:** `GitOperationService` gets a new `gitStatus()` method returning a `StatusSummary` record. `GTD` registers the `/status` command and formats the response with truncation at 1900 chars.

**Tech Stack:** JGit 7.6 (`git.status().call()`), JDA 6.3.2, JUnit 5, Java 21, Gradle.

---

## File Map

| File | Action |
|------|--------|
| `src/main/java/fr/enimaloc/gtd/GitOperationService.java` | **Modify** — add `StatusSummary` record + `gitStatus()` method |
| `src/test/java/fr/enimaloc/gtd/GitOperationServiceTest.java` | **Modify** — add tests for `gitStatus()` |
| `src/main/java/fr/enimaloc/gtd/GTD.java` | **Modify** — register + route `/status` |

---

## Task 1 — `gitStatus()` in `GitOperationService` + tests

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/GitOperationService.java`
- Modify: `src/test/java/fr/enimaloc/gtd/GitOperationServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add these tests to `GitOperationServiceTest.java` (inside the class, after the existing tests):

```java
@Test
void gitStatus_cleanRepo_emptyLists() throws Exception {
    GitOperationService.StatusSummary status = service.gitStatus();
    assertEquals(defaultBranch, status.branch());
    assertTrue(status.modified().isEmpty());
    assertTrue(status.added().isEmpty());
    assertTrue(status.deleted().isEmpty());
}

@Test
void gitStatus_untrackedFile_appearsInAdded() throws Exception {
    Files.writeString(localDir.resolve("new.txt"), "content");
    GitOperationService.StatusSummary status = service.gitStatus();
    assertTrue(status.added().contains("new.txt"),
        "Untracked file should appear in added, got: " + status.added());
}

@Test
void gitStatus_modifiedFile_appearsInModified() throws Exception {
    // init.txt was committed in setUp — modify it
    Files.writeString(localDir.resolve("init.txt"), "modified content");
    GitOperationService.StatusSummary status = service.gitStatus();
    assertTrue(status.modified().contains("init.txt"),
        "Modified tracked file should appear in modified, got: " + status.modified());
}

@Test
void gitStatus_deletedFile_appearsInDeleted() throws Exception {
    Files.delete(localDir.resolve("init.txt"));
    GitOperationService.StatusSummary status = service.gitStatus();
    assertTrue(status.deleted().contains("init.txt"),
        "Deleted tracked file should appear in deleted, got: " + status.deleted());
}

@Test
void gitStatus_listsAreSorted() throws Exception {
    Files.writeString(localDir.resolve("z-file.txt"), "z");
    Files.writeString(localDir.resolve("a-file.txt"), "a");
    GitOperationService.StatusSummary status = service.gitStatus();
    List<String> added = status.added();
    assertTrue(added.indexOf("a-file.txt") < added.indexOf("z-file.txt"),
        "Added list should be sorted alphabetically, got: " + added);
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew test --tests "fr.enimaloc.gtd.GitOperationServiceTest.gitStatus*"
```

Expected: compilation error — `StatusSummary` and `gitStatus()` do not exist yet.

- [ ] **Step 3: Add `StatusSummary` record and `gitStatus()` to `GitOperationService`**

At the bottom of `GitOperationService.java`, before the closing `}`, add:

```java
// ── Status ────────────────────────────────────────────────────────────────

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

Also add the missing import at the top of the file (if not already present):
```java
import java.util.Collections;
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew test --tests "fr.enimaloc.gtd.GitOperationServiceTest"
```

Expected: all tests GREEN (previously passing + 5 new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/GitOperationService.java \
        src/test/java/fr/enimaloc/gtd/GitOperationServiceTest.java
git commit -m "feat: add gitStatus() to GitOperationService with StatusSummary record"
```

---

## Task 2 — `/status` command in `GTD.java`

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/GTD.java`

- [ ] **Step 1: Register `/status` in `init()`**

After the existing `if (!existing.contains("cherry-pick"))` block, add:

```java
if (!existing.contains("status")) {
    jda.upsertCommand("status", "Afficher l'état git du dépôt").queue();
}
```

- [ ] **Step 2: Route `/status` in `onSlashCommandInteraction()`**

After the existing `"cherry-pick"` block (before the outer closing `}`), add:

```java
} else if ("status".equals(event.getFullCommandName())) {
    event.deferReply(true).queue();
    if (server.gitOps() == null) {
        event.getHook().editOriginal("Git non initialisé — lancez /init d'abord").queue();
        return;
    }
    try {
        GitOperationService.StatusSummary status = server.gitOps().gitStatus();
        int totalChanged = status.modified().size() + status.added().size() + status.deleted().size();

        if (totalChanged == 0) {
            event.getHook().editOriginal(
                "Branche : `" + status.branch() + "` — Rien à signaler (working tree clean)").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Branche :** `").append(status.branch()).append("`\n");
        sb.append("**").append(status.modified().size()).append(" modifié(s), ")
          .append(status.added().size()).append(" ajouté(s), ")
          .append(status.deleted().size()).append(" supprimé(s)**\n\n");

        for (String f : status.modified()) sb.append("M  ").append(f).append("\n");
        for (String f : status.added())    sb.append("A  ").append(f).append("\n");
        for (String f : status.deleted())  sb.append("D  ").append(f).append("\n");

        String response = sb.toString();
        if (response.length() > 1900) {
            String header = "**Branche :** `" + status.branch() + "`\n"
                + "**" + status.modified().size() + " modifié(s), "
                + status.added().size() + " ajouté(s), "
                + status.deleted().size() + " supprimé(s)**\n"
                + "(liste tronquée — " + totalChanged + " fichier(s) au total)";
            response = header;
        }
        event.getHook().editOriginal(response).queue();
    } catch (Exception e) {
        e.printStackTrace();
        event.getHook().editOriginal("Erreur status : " + e.getMessage()).queue();
    }
```

- [ ] **Step 3: Build + run all tests**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/GTD.java
git commit -m "feat: add /status slash command to display git repository state"
```

---

## Self-Review

- ✅ `gitStatus()` combines `getAdded()+getUntracked()` for "A" and `getRemoved()+getMissing()` for "D"
- ✅ Lists are sorted alphabetically
- ✅ Clean repo returns a single-line message
- ✅ Response truncated at 1900 chars with summary header
- ✅ Guard for `gitOps() == null` before init
- ✅ 5 tests cover clean/untracked/modified/deleted/sorted
- ✅ No TBD or placeholders
