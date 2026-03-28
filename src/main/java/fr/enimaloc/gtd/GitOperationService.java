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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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

    // ── Status ────────────────────────────────────────────────────────────────

    public record StatusSummary(
        String branch,
        List<String> modified,
        List<String> added,
        List<String> deleted
    ) {}

    public StatusSummary gitStatus() throws GitAPIException, IOException {
        String branch = currentBranch();
        Status status = git.status().call();

        List<String> modifiedList = new ArrayList<>(status.getModified());
        List<String> addedList = new ArrayList<>();
        addedList.addAll(status.getAdded());
        addedList.addAll(status.getUntracked());
        List<String> deletedList = new ArrayList<>();
        deletedList.addAll(status.getRemoved());
        deletedList.addAll(status.getMissing());

        Collections.sort(modifiedList);
        Collections.sort(addedList);
        Collections.sort(deletedList);

        return new StatusSummary(branch, List.copyOf(modifiedList), List.copyOf(addedList), List.copyOf(deletedList));
    }
}
