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
    void push_sendsLocalCommitToRemote() throws Exception {
        Files.writeString(localDir.resolve("pushed.txt"), "data");
        localGit.add().addFilepattern(".").call();
        localGit.commit().setMessage("test: local commit").call();
        service.push();
        Path verifyDir = Files.createTempDirectory("verify");
        try (Git verify = Git.cloneRepository()
                .setURI(remoteDir.toUri().toString())
                .setDirectory(verifyDir.toFile()).call()) {
            String last = verify.log().setMaxCount(1).call().iterator().next().getShortMessage();
            assertEquals("test: local commit", last);
        }
    }
}
