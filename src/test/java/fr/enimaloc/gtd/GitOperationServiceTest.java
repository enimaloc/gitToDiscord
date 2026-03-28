package fr.enimaloc.gtd;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
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
        StoredConfig cfg = localGit.getRepository().getConfig();
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();
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
    void listBranches_remotebranchHasRemotePrefix() throws Exception {
        service.createBranch("feature-test");
        List<String> branches = service.listBranches();
        assertTrue(branches.contains("remote/feature-test"),
            "Remote branch should appear as remote/feature-test, got: " + branches);
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
    void push_sendsLocalCommitToRemote(@TempDir Path verifyDir) throws Exception {
        Files.writeString(localDir.resolve("pushed.txt"), "data");
        localGit.add().addFilepattern(".").call();
        localGit.commit().setMessage("test: local commit").call();
        service.push();
        try (Git verify = Git.cloneRepository()
                .setURI(remoteDir.toUri().toString())
                .setDirectory(verifyDir.toFile()).call()) {
            String last = verify.log().setMaxCount(1).call().iterator().next().getShortMessage();
            assertEquals("test: local commit", last);
        }
    }

    @Test
    void scheduleCommit_coalescesMultipleCalls() throws Exception {
        // Use commitDelayMs=0 so commits fire immediately
        service = new GitOperationService(localGit, Executors.newSingleThreadScheduledExecutor(), 0L);

        // Write a file so there's something to commit
        Files.writeString(localDir.resolve("coalesce.txt"), "v1");

        // Call twice rapidly — only one commit should result
        service.scheduleCommit(12345L, "test: first call", null, null);
        service.scheduleCommit(12345L, "test: second call", null, null);

        // Give the executor time to run
        Thread.sleep(200);

        // Only 2 commits total (init + one from scheduleCommit — not two)
        var commits = new java.util.ArrayList<>();
        localGit.log().call().forEach(commits::add);
        // init commit + 1 scheduled commit = 2 total
        assertEquals(2, commits.size(), "scheduleCommit should coalesce — only 1 auto-commit expected");
    }

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
}
