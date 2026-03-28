# Branch Support + Auto-commit + Cherry-pick — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full branch management (`/branch create|switch|list|delete`), auto-commit on every Discord event with audit log reason, and `/cherry-pick <hash>` that applies a commit to Discord.

**Architecture:** A new `GitOperationService` centralizes all git operations with a single-threaded `ScheduledExecutorService` for coalescing auto-commits. `Server` exposes it and adds `applyGitState()` extracted from `pull()`. `DiscordEventSync` calls `scheduleCommit()` after every file write/delete. `GTD` routes the two new commands.

**Tech Stack:** JGit 7.6, JDA 6.3.2, JUnit 5 (no mocking — tests use real temp repos), Gradle.

---

## File Map

| File | Action |
|------|--------|
| `src/main/java/fr/enimaloc/gtd/GitOperationService.java` | **Create** — all git ops |
| `src/test/java/fr/enimaloc/gtd/GitOperationServiceTest.java` | **Create** — unit tests |
| `src/main/java/fr/enimaloc/gtd/Server.java` | **Modify** — integrate service, extract `applyGitState()`, `updateBranchConfig()` |
| `src/main/java/fr/enimaloc/gtd/DiscordEventSync.java` | **Modify** — call `scheduleCommit()` after each event |
| `src/main/java/fr/enimaloc/gtd/GTD.java` | **Modify** — register + route `/branch` and `/cherry-pick` |

---

## Task 1 — `GitOperationService`: sync git operations

**Files:**
- Create: `src/main/java/fr/enimaloc/gtd/GitOperationService.java`
- Create: `src/test/java/fr/enimaloc/gtd/GitOperationServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/fr/enimaloc/gtd/GitOperationServiceTest.java
package fr.enimaloc.gtd;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class GitOperationServiceTest {

    @TempDir Path remoteDir;
    @TempDir Path localDir;

    private Git localGit;
    private GitOperationService service;
    private String defaultBranch;

    @BeforeEach
    void setUp() throws Exception {
        Git.init().setBare(true).setDirectory(remoteDir.toFile()).call().close();
        localGit = Git.cloneRepository()
            .setURI(remoteDir.toUri().toString())
            .setDirectory(localDir.toFile())
            .call();
        Files.writeString(localDir.resolve("init.txt"), "init");
        localGit.add().addFilepattern(".").call();
        localGit.commit().setMessage("chore: init").call();
        localGit.push().call();
        defaultBranch = localGit.getRepository().getBranch();
        service = new GitOperationService(localGit, Executors.newSingleThreadScheduledExecutor(), 0L);
    }

    @AfterEach
    void tearDown() { localGit.close(); }

    @Test
    void commitAndPush_createsCommitAndPushes() throws Exception {
        Files.writeString(localDir.resolve("data.txt"), "hello");
        service.commitAndPush("test: add data.txt");
        String last = localGit.log().setMaxCount(1).call().iterator().next().getShortMessage();
        assertEquals("test: add data.txt", last);
    }

    @Test
    void createBranch_branchExistsLocally() throws Exception {
        service.createBranch("feature/new");
        List<Ref> branches = localGit.branchList().call();
        assertTrue(branches.stream().anyMatch(r -> r.getName().endsWith("feature/new")));
    }

    @Test
    void switchBranch_changesCurrentBranch() throws Exception {
        service.createBranch("feature/switch");
        service.switchBranch("feature/switch");
        assertEquals("feature/switch", localGit.getRepository().getBranch());
    }

    @Test
    void switchBranch_unknownBranch_throws() {
        assertThrows(Exception.class, () -> service.switchBranch("does-not-exist"));
    }

    @Test
    void deleteBranch_removedLocally() throws Exception {
        service.createBranch("to-delete");
        service.deleteBranch("to-delete");
        List<Ref> branches = localGit.branchList().call();
        assertTrue(branches.stream().noneMatch(r -> r.getName().endsWith("to-delete")));
    }

    @Test
    void listBranches_includesDefaultBranch() throws Exception {
        List<String> branches = service.listBranches();
        assertTrue(branches.contains(defaultBranch));
    }

    @Test
    void cherryPick_appliesCommitToCurrentBranch() throws Exception {
        service.createBranch("feat/cherry");
        service.switchBranch("feat/cherry");
        Files.writeString(localDir.resolve("cherry.txt"), "cherry content");
        service.commitAndPush("feat: add cherry.txt");
        String hash = localGit.log().setMaxCount(1).call().iterator().next().getName();
        service.switchBranch(defaultBranch);
        service.cherryPick(hash);
        assertTrue(Files.exists(localDir.resolve("cherry.txt")));
    }

    @Test
    void cherryPick_invalidHash_throws() {
        assertThrows(Exception.class,
            () -> service.cherryPick("0000000000000000000000000000000000000000"));
    }

    @Test
    void currentBranch_returnsDefaultBranch() throws Exception {
        assertEquals(defaultBranch, service.currentBranch());
    }

    @Test
    void push_sendsLocalCommitToRemote() throws Exception {
        Files.writeString(localDir.resolve("pushed.txt"), "data");
        localGit.add().addFilepattern(".").call();
        localGit.commit().setMessage("test: local commit").call();
        service.push();
        // verify remote has the commit by opening a fresh clone
        Path verifyDir = Files.createTempDirectory("verify");
        try (Git verify = Git.cloneRepository()
                .setURI(remoteDir.toUri().toString())
                .setDirectory(verifyDir.toFile()).call()) {
            String last = verify.log().setMaxCount(1).call().iterator().next().getShortMessage();
            assertEquals("test: local commit", last);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew test --tests "fr.enimaloc.gtd.GitOperationServiceTest"
```

Expected: compilation error — `GitOperationService` does not exist.

- [ ] **Step 3: Create `GitOperationService`**

```java
// src/main/java/fr/enimaloc/gtd/GitOperationService.java
package fr.enimaloc.gtd;

import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class GitOperationService {

    private final Git git;
    private final ScheduledExecutorService scheduler;
    private final long commitDelayMs;
    private final Map<Long, ScheduledFuture<?>> pendingCommits = new ConcurrentHashMap<>();

    public GitOperationService(Git git) {
        this(git, Executors.newSingleThreadScheduledExecutor(), 1500L);
    }

    /** Package-private: injectable scheduler and delay for tests. */
    GitOperationService(Git git, ScheduledExecutorService scheduler, long commitDelayMs) {
        this.git = git;
        this.scheduler = scheduler;
        this.commitDelayMs = commitDelayMs;
    }

    // ── Sync operations ───────────────────────────────────────────────────────

    public void commitAndPush(String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        git.commit().setMessage(message).call();
        push();
    }

    public void push() throws GitAPIException {
        git.push().call();
    }

    public void createBranch(String name) throws GitAPIException {
        git.branchCreate().setName(name).call();
        git.push().setRemote("origin")
            .setRefSpecs(new RefSpec("refs/heads/" + name + ":refs/heads/" + name))
            .call();
    }

    public void switchBranch(String name) throws GitAPIException, IOException {
        git.checkout().setName(name).call();
    }

    public void deleteBranch(String name) throws GitAPIException {
        git.branchDelete().setBranchNames(name).setForce(true).call();
        git.push().setRemote("origin")
            .setRefSpecs(new RefSpec(":refs/heads/" + name))
            .call();
    }

    public List<String> listBranches() throws GitAPIException {
        return git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
            .stream()
            .map(Ref::getName)
            .map(n -> n.replace("refs/remotes/origin/", "remote/")
                       .replace("refs/heads/", ""))
            .distinct()
            .sorted()
            .toList();
    }

    public void cherryPick(String hash) throws GitAPIException, IOException {
        ObjectId id = git.getRepository().resolve(hash);
        if (id == null) throw new IllegalArgumentException("Commit introuvable : " + hash);
        CherryPickResult result = git.cherryPick().include(id).call();
        if (result.getStatus() != CherryPickResult.CherryPickStatus.OK) {
            git.reset().setMode(ResetCommand.ResetType.MERGE).call();
            String paths = result.getFailingPaths() != null
                ? result.getFailingPaths().keySet().toString() : "unknown";
            throw new IllegalStateException("Cherry-pick conflit : " + paths);
        }
    }

    public String currentBranch() throws IOException {
        return git.getRepository().getBranch();
    }

    // ── Scheduled commit with audit log ──────────────────────────────────────

    /**
     * Schedules a commit after commitDelayMs. If called again for the same guild
     * before the delay, the previous task is cancelled (last event wins).
     */
    public void scheduleCommit(long guildId, String message, Guild guild, ActionType auditType) {
        ScheduledFuture<?> existing = pendingCommits.remove(guildId);
        if (existing != null) existing.cancel(false);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingCommits.remove(guildId);
            String reason = fetchReason(guild, auditType);
            String fullMsg = reason != null ? message + "\n\nReason: " + reason : message;
            try {
                commitAndPush(fullMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, commitDelayMs, TimeUnit.MILLISECONDS);
        pendingCommits.put(guildId, future);
    }

    private String fetchReason(Guild guild, ActionType auditType) {
        if (guild == null || auditType == null) return null;
        try {
            List<AuditLogEntry> logs = guild.retrieveAuditLogs()
                .type(auditType).limit(1).complete();
            if (!logs.isEmpty()) return logs.get(0).getReason();
        } catch (Exception ignored) {}
        return null;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew test --tests "fr.enimaloc.gtd.GitOperationServiceTest"
```

Expected: all 10 tests GREEN.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/GitOperationService.java \
        src/test/java/fr/enimaloc/gtd/GitOperationServiceTest.java
git commit -m "feat: add GitOperationService for branch, commit, and cherry-pick operations"
```

---

## Task 2 — `Server.java`: integrate `GitOperationService`, extract `applyGitState()`

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/Server.java`

- [ ] **Step 1: Add `gitOps` field and update constructor / `initGit()`**

At the top of the class, after `private Git git;`, add:

```java
private GitOperationService gitOps;
```

In the constructor, replace:
```java
try {
    this.git = Git.open(gitPath.toFile());
    synchroAll(guild);
} catch (RepositoryNotFoundException ign) {}
```
with:
```java
try {
    this.git = Git.open(gitPath.toFile());
    this.gitOps = new GitOperationService(this.git);
    this.git.checkout().setName(this.config.branch).call();
    synchroAll(guild);
} catch (RepositoryNotFoundException ign) {
} catch (org.eclipse.jgit.api.errors.GitAPIException e) {
    throw new IOException("Failed to checkout branch " + config.branch, e);
}
```

In `initGit()`, replace the last line `synchroAll(guild);` with:
```java
this.gitOps = new GitOperationService(this.git);
synchroAll(guild);
```

- [ ] **Step 2: Expose `gitOps()` and add `updateBranchConfig()`**

Add after the existing `gitPath()` method:

```java
public GitOperationService gitOps() { return gitOps; }

public void updateBranchConfig(String branch) throws IOException {
    config.branch = branch;
    GTD.MAPPER.writer().writeValue(dataPath.resolve("config.toml").toFile(), config);
}
```

- [ ] **Step 3: Extract `applyGitState()` from `pull()`**

The current `pull()` method does `git.pull().call()` then reads files and applies them to Discord. Extract everything after `git.pull().call()` into a new `public PullResult applyGitState(Guild guild)` method.

The full new `pull()`:

```java
public PullResult pull(Guild guild) throws IOException, GitAPIException {
    if (git == null) throw new IllegalStateException("Git not initialized — run /init first");
    git.pull().call();
    return applyGitState(guild);
}
```

The new `applyGitState()` (everything that was after `git.pull().call()` in the old `pull()`):

```java
public PullResult applyGitState(Guild guild) throws IOException, GitAPIException {
    GuildFile guildFile                        = readToml(gitPath.resolve(GuildFile.FILE_PATH), GuildFile.class);
    Map<Path, RoleFile> rolePaths              = readTomlDirWithPaths(gitPath.resolve("roles"), RoleFile.class);
    Map<Path, CategoryFile> catPaths           = readTomlDirWithPaths(gitPath.resolve("categories"), CategoryFile.class);
    Map<Path, ChannelFile> chanPaths           = readChannelFilesWithPaths();

    List<RoleFile> roleFiles    = new ArrayList<>(rolePaths.values());
    List<CategoryFile> catFiles = new ArrayList<>(catPaths.values());
    List<ChannelFile> chanFiles = new ArrayList<>(chanPaths.values());

    PullReconciler reconciler  = new PullReconciler();
    PullExecutor executor      = new PullExecutor(guild);
    Map<Long, Long> tempToReal = new LinkedHashMap<>();

    int created = 0, updated = 0;

    if (guildFile != null) { executor.applyGuild(guildFile); updated++; }

    Map<Long, String> existingRoles = guild.getRoles().stream()
            .collect(Collectors.toMap(Role::getIdLong, Role::getName));
    ReconcileResult<RoleFile> roles = reconciler.reconcileRoles(existingRoles, roleFiles);
    for (RoleFile f : roles.toCreate()) {
        Role created_ = executor.createRole(f);
        if (TempIdUtils.isTemp(f.id)) tempToReal.put(f.id, created_.getIdLong());
        created++;
    }
    for (var e : roles.toUpdate()) { executor.applyRole(e.discordId(), e.file()); updated++; }

    Map<Long, String> existingCats = guild.getCategories().stream()
            .collect(Collectors.toMap(Category::getIdLong, Category::getName));
    ReconcileResult<CategoryFile> cats = reconciler.reconcileCategories(existingCats, catFiles);
    for (CategoryFile f : cats.toCreate()) {
        Category created_ = executor.createCategory(f);
        if (TempIdUtils.isTemp(f.id)) tempToReal.put(f.id, created_.getIdLong());
        created++;
    }
    for (var e : cats.toUpdate()) { executor.applyCategory(e.discordId(), e.file()); updated++; }

    for (ChannelFile f : chanFiles) {
        if (TempIdUtils.isTemp(f.parentCategoryId) && tempToReal.containsKey(f.parentCategoryId)) {
            f.parentCategoryId = tempToReal.get(f.parentCategoryId);
        }
    }
    Map<Long, String> existingChannels = guild.getChannels().stream()
            .filter(c -> !(c instanceof Category))
            .collect(Collectors.toMap(GuildChannel::getIdLong, GuildChannel::getName));
    ReconcileResult<ChannelFile> channels = reconciler.reconcileChannels(existingChannels, chanFiles);
    for (ChannelFile f : channels.toCreate()) { executor.createChannel(f); created++; }
    for (var e : channels.toUpdate()) { executor.applyChannel(e.discordId(), e.file()); updated++; }

    if (!tempToReal.isEmpty()) {
        resolveTempIds(tempToReal);
        gitOps.commitAndPush("fix: resolve temp IDs to Discord IDs");
    }

    return new PullResult(created, updated);
}
```

Note: `resolveTempIds()` no longer does `git.add/commit/push` inline — those three lines are removed from `resolveTempIds()` and replaced by the `gitOps.commitAndPush(...)` call above.

- [ ] **Step 4: Remove the inline git ops from `resolveTempIds()`**

In `resolveTempIds()`, delete these three lines that were at the end of the `if (!tempToReal.isEmpty())` block inside the old `pull()` (they no longer exist in `applyGitState` since we call `gitOps.commitAndPush` instead):

These lines no longer exist — `resolveTempIds()` itself never contained git operations. The git operations were in `pull()` after calling `resolveTempIds()`. In the new code, `applyGitState()` handles this via `gitOps.commitAndPush()`. No change needed to `resolveTempIds()` itself.

- [ ] **Step 5: Build to verify compilation**

```
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/Server.java
git commit -m "feat: integrate GitOperationService into Server, extract applyGitState()"
```

---

## Task 3 — `DiscordEventSync.java`: auto-commit on every Discord event

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/DiscordEventSync.java`

- [ ] **Step 1: Add `ActionType` import**

Add at the top of the file with the other imports:

```java
import net.dv8tion.jda.api.audit.ActionType;
```

- [ ] **Step 2: Replace all event handlers with auto-commit calls**

Replace the full contents of `DiscordEventSync.java` with the following. The `writeFile`/`deleteFile` helpers stay unchanged; each event handler gains a `scheduleCommit` call after writing.

```java
package fr.enimaloc.gtd;

import fr.enimaloc.gtd.asset.AssetDownloader;
import fr.enimaloc.gtd.file.*;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.GenericEmojiUpdateEvent;
import net.dv8tion.jda.api.events.guild.update.GenericGuildUpdateEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.GenericRoleUpdateEvent;
import net.dv8tion.jda.api.events.sticker.GuildStickerAddedEvent;
import net.dv8tion.jda.api.events.sticker.GuildStickerRemovedEvent;
import net.dv8tion.jda.api.events.sticker.update.GenericGuildStickerUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class DiscordEventSync extends ListenerAdapter {

    private final Map<Long, Server> servers;

    public DiscordEventSync(Map<Long, Server> servers) {
        this.servers = servers;
    }

    // ── Guild ─────────────────────────────────────────────────────────────────

    @Override
    public void onGenericGuildUpdate(GenericGuildUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        writeFile(server.gitPath().resolve(GuildFile.FILE_PATH), POJOUtils.parse(event.getGuild()));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "guild: update",
            event.getGuild(),
            ActionType.GUILD_UPDATE);
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        writeFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())),
                POJOUtils.parse(event.getRole()));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "role: create " + event.getRole().getName(),
            event.getGuild(),
            ActionType.ROLE_CREATE);
    }

    @Override
    public void onGenericRoleUpdate(GenericRoleUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        writeFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())),
                POJOUtils.parse(event.getRole()));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "role: update " + event.getRole().getName(),
            event.getGuild(),
            ActionType.ROLE_UPDATE);
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        deleteFile(server.gitPath().resolve(RoleFile.FILE_PATH.formatted(event.getRole().getIdLong())));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "role: delete " + event.getRole().getName(),
            event.getGuild(),
            ActionType.ROLE_DELETE);
    }

    // ── Categories + Channels + Threads ──────────────────────────────────────

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        var channel = event.getChannel();
        String commitMsg;
        if (channel instanceof Category cat) {
            writeFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(cat.getIdLong())),
                    POJOUtils.parse(cat));
            commitMsg = "category: create " + cat.getName();
        } else if (channel instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            Path path = server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, thread.getIdLong()));
            writeFile(path, POJOUtils.parseThread(thread));
            commitMsg = "thread: create " + thread.getName();
        } else if (channel instanceof GuildChannel gc) {
            ChannelFile file = POJOUtils.parseChannel(gc);
            if (file == null) return;
            writeFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(gc.getIdLong())), file);
            commitMsg = "channel: create " + gc.getName();
        } else {
            return;
        }
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(), commitMsg, event.getGuild(), ActionType.CHANNEL_CREATE);
    }

    @Override
    public void onGenericChannelUpdate(GenericChannelUpdateEvent<?> event) {
        if (!(event.getChannel() instanceof GuildChannel gc)) return;
        Server server = servers.get(gc.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        String commitMsg;
        if (gc instanceof Category cat) {
            writeFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(cat.getIdLong())),
                    POJOUtils.parse(cat));
            commitMsg = "category: update " + cat.getName();
        } else if (gc instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            Path path = server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, thread.getIdLong()));
            writeFile(path, POJOUtils.parseThread(thread));
            commitMsg = "thread: update " + thread.getName();
        } else {
            ChannelFile file = POJOUtils.parseChannel(gc);
            if (file == null) return;
            writeFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(gc.getIdLong())), file);
            commitMsg = "channel: update " + gc.getName();
        }
        server.gitOps().scheduleCommit(
            gc.getGuild().getIdLong(), commitMsg, gc.getGuild(), ActionType.CHANNEL_UPDATE);
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        long id = event.getChannel().getIdLong();
        String commitMsg;
        if (event.getChannel() instanceof Category cat) {
            deleteFile(server.gitPath().resolve(CategoryFile.FILE_PATH.formatted(id)));
            commitMsg = "category: delete " + cat.getName();
        } else if (event.getChannel() instanceof ThreadChannel thread) {
            long parentId = thread.getParentChannel().getIdLong();
            deleteFile(server.gitPath().resolve(
                    ThreadChannelFile.FILE_PATH.formatted(parentId, id)));
            commitMsg = "thread: delete " + thread.getName();
        } else {
            deleteFile(server.gitPath().resolve(ChannelFile.FILE_PATH.formatted(id)));
            commitMsg = "channel: delete " + event.getChannel().getName();
        }
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(), commitMsg, event.getGuild(), ActionType.CHANNEL_DELETE);
    }

    // ── Emojis ────────────────────────────────────────────────────────────────

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        EmoteFile ef = POJOUtils.parse(event.getEmoji());
        writeFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())), ef);
        if (ef.url != null)
            AssetDownloader.download(ef.url,
                server.gitPath().resolve("emojis/" + event.getEmoji().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "emoji: add " + event.getEmoji().getName(),
            event.getGuild(),
            ActionType.EMOJI_CREATE);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onGenericEmojiUpdate(GenericEmojiUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        EmoteFile ef = POJOUtils.parse(event.getEmoji());
        writeFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())), ef);
        if (ef.url != null)
            AssetDownloader.download(ef.url,
                server.gitPath().resolve("emojis/" + event.getEmoji().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "emoji: update " + event.getEmoji().getName(),
            event.getGuild(),
            ActionType.EMOJI_UPDATE);
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        deleteFile(server.gitPath().resolve(EmoteFile.FILE_PATH.formatted(event.getEmoji().getIdLong())));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "emoji: remove " + event.getEmoji().getName(),
            event.getGuild(),
            ActionType.EMOJI_DELETE);
    }

    // ── Stickers ──────────────────────────────────────────────────────────────

    @Override
    public void onGuildStickerAdded(GuildStickerAddedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        StickerFile sf = POJOUtils.parse(event.getSticker());
        writeFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())), sf);
        if (sf.url != null)
            AssetDownloader.download(sf.url,
                server.gitPath().resolve("stickers/" + event.getSticker().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "sticker: add " + event.getSticker().getName(),
            event.getGuild(),
            ActionType.STICKER_CREATE);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void onGenericGuildStickerUpdate(GenericGuildStickerUpdateEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        StickerFile sf = POJOUtils.parse(event.getSticker());
        writeFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())), sf);
        if (sf.url != null)
            AssetDownloader.download(sf.url,
                server.gitPath().resolve("stickers/" + event.getSticker().getIdLong() + ".webp"));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "sticker: update " + event.getSticker().getName(),
            event.getGuild(),
            ActionType.STICKER_UPDATE);
    }

    @Override
    public void onGuildStickerRemoved(GuildStickerRemovedEvent event) {
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null || server.gitOps() == null) return;
        deleteFile(server.gitPath().resolve(StickerFile.FILE_PATH.formatted(event.getSticker().getIdLong())));
        server.gitOps().scheduleCommit(
            event.getGuild().getIdLong(),
            "sticker: remove " + event.getSticker().getName(),
            event.getGuild(),
            ActionType.STICKER_DELETE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeFile(Path path, Object file) {
        try {
            Files.createDirectories(path.getParent());
            GTD.MAPPER.writer().writeValue(path.toFile(), file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

- [ ] **Step 3: Build to verify compilation**

```
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL. If `ActionType.STICKER_CREATE/UPDATE/DELETE` do not exist in JDA 6.3.2, replace those three with `null` (the `fetchReason` method handles null safely).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/DiscordEventSync.java
git commit -m "feat: auto-commit on Discord events with audit log reason"
```

---

## Task 4 — `GTD.java`: register and route `/branch` + `/cherry-pick`

**Files:**
- Modify: `src/main/java/fr/enimaloc/gtd/GTD.java`

- [ ] **Step 1: Add missing imports**

Add to the existing imports block:

```java
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import java.util.List;
```

- [ ] **Step 2: Register the two new commands in `init()`**

After the existing `if (!existing.contains("archive"))` block, add:

```java
if (!existing.contains("branch")) {
    jda.upsertCommand(Commands.slash("branch", "Gérer les branches git")
        .addSubcommands(
            new SubcommandData("create", "Créer une nouvelle branche")
                .addOption(OptionType.STRING, "name", "Nom de la branche", true),
            new SubcommandData("switch", "Changer de branche (sans modifier Discord)")
                .addOption(OptionType.STRING, "name", "Nom de la branche", true),
            new SubcommandData("list", "Lister toutes les branches"),
            new SubcommandData("delete", "Supprimer une branche")
                .addOption(OptionType.STRING, "name", "Nom de la branche", true)
        )).queue();
}
if (!existing.contains("cherry-pick")) {
    jda.upsertCommand(Commands.slash("cherry-pick", "Appliquer un commit git sur Discord")
        .addOption(OptionType.STRING, "commit", "Hash du commit", true)
    ).queue();
}
```

- [ ] **Step 3: Route `/branch` in `onSlashCommandInteraction()`**

After the existing `"archive"` block and before the closing `}` of `onSlashCommandInteraction`, add:

```java
} else if ("branch".equals(event.getName())) {
    event.deferReply(true).queue();
    if (server.gitOps() == null) {
        event.getHook().editOriginal("Git non initialisé — lancez /init d'abord").queue();
        return;
    }
    try {
        switch (event.getSubcommandName()) {
            case "create" -> {
                String name = event.getOption("name").getAsString();
                server.gitOps().createBranch(name);
                event.getHook().editOriginal("Branche `" + name + "` créée et poussée").queue();
            }
            case "switch" -> {
                String name = event.getOption("name").getAsString();
                if (server.gitOps().currentBranch().equals(name)) {
                    event.getHook().editOriginal("Déjà sur la branche `" + name + "`").queue();
                    return;
                }
                server.gitOps().switchBranch(name);
                server.updateBranchConfig(name);
                event.getHook().editOriginal("Branche changée vers `" + name + "` — le prochain /pull utilisera cette branche").queue();
            }
            case "list" -> {
                List<String> branches = server.gitOps().listBranches();
                String current = server.gitOps().currentBranch();
                StringBuilder sb = new StringBuilder("**Branches :**\n");
                for (String b : branches) {
                    sb.append(b.equals(current) ? "• **" + b + "** ← courante\n" : "• " + b + "\n");
                }
                event.getHook().editOriginal(sb.toString()).queue();
            }
            case "delete" -> {
                String name = event.getOption("name").getAsString();
                if (server.gitOps().currentBranch().equals(name)) {
                    event.getHook().editOriginal(
                        "Impossible de supprimer la branche courante. Switchez d'abord avec `/branch switch`.").queue();
                    return;
                }
                server.gitOps().deleteBranch(name);
                event.getHook().editOriginal("Branche `" + name + "` supprimée").queue();
            }
            default -> event.getHook().editOriginal("Sous-commande inconnue").queue();
        }
    } catch (Exception e) {
        e.printStackTrace();
        event.getHook().editOriginal("Erreur branch : " + e.getMessage()).queue();
    }
```

- [ ] **Step 4: Route `/cherry-pick` in `onSlashCommandInteraction()`**

Directly after the `/branch` block (before the outer closing `}`), add:

```java
} else if ("cherry-pick".equals(event.getFullCommandName())) {
    event.deferReply(true).queue();
    if (server.gitOps() == null) {
        event.getHook().editOriginal("Git non initialisé — lancez /init d'abord").queue();
        return;
    }
    try {
        String hash = event.getOption("commit").getAsString();
        server.gitOps().cherryPick(hash);
        Server.PullResult result = server.applyGitState(event.getGuild());
        server.gitOps().push();
        event.getHook().editOriginal(
            "Cherry-pick `" + hash.substring(0, Math.min(7, hash.length())) + "` appliqué : " + result).queue();
    } catch (Exception e) {
        e.printStackTrace();
        event.getHook().editOriginal("Erreur cherry-pick : " + e.getMessage()).queue();
    }
```

- [ ] **Step 5: Build + run all tests**

```
./gradlew build
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/enimaloc/gtd/GTD.java
git commit -m "feat: add /branch and /cherry-pick slash commands"
```

---

## Self-Review Checklist

- ✅ Branch create/switch/list/delete — Task 4 (commands) + Task 1 (service logic)
- ✅ Auto-commit on every Discord event — Task 3 (DiscordEventSync)
- ✅ Audit log reason in commit message — `GitOperationService.scheduleCommit` + `fetchReason`
- ✅ Coalescing (multiple events → single commit per guild) — `pendingCommits` map with cancel
- ✅ `/cherry-pick` applies commit to Discord — Task 4 routing + `applyGitState()`
- ✅ `GitConfig.branch` actually used at startup — Task 2
- ✅ No inline `git.add/commit/push` left in `Server` — replaced by `gitOps.commitAndPush`
- ✅ `server.gitOps() == null` guard in all command handlers — prevents NPE before /init
- ✅ Tests use real JGit repos (no mocking) — consistent with existing test style
