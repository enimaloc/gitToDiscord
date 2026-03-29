package fr.enimaloc.gtd.pull;

import fr.enimaloc.gtd.file.ChannelFile;
import fr.enimaloc.gtd.file.RoleFile;
import fr.enimaloc.gtd.file.TextChannelFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PullReconcilerTest {

    private PullReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new PullReconciler();
    }

    // ── Roles ─────────────────────────────────────────────────────────────────

    @Test
    void roles_newRoleWithNoId_isCreated() {
        RoleFile desired = roleFile(0L, "Moderator");
        Map<Long, String> existing = Map.of(100L, "Admin");

        ReconcileResult<RoleFile> result = reconciler.reconcileRoles(existing, List.of(desired));

        assertEquals(1, result.toCreate().size());
        assertEquals(0, result.toUpdate().size());
        assertEquals("Moderator", result.toCreate().get(0).name);
    }

    @Test
    void roles_matchById_isUpdated() {
        RoleFile desired = roleFile(100L, "Admin-Renamed");
        Map<Long, String> existing = Map.of(100L, "Admin");

        ReconcileResult<RoleFile> result = reconciler.reconcileRoles(existing, List.of(desired));

        assertEquals(0, result.toCreate().size());
        assertEquals(1, result.toUpdate().size());
        assertEquals(100L, result.toUpdate().get(0).discordId());
        assertEquals("Admin-Renamed", result.toUpdate().get(0).file().name);
    }

    @Test
    void roles_matchByName_whenNoId_isUpdated() {
        RoleFile desired = roleFile(0L, "Admin");
        Map<Long, String> existing = Map.of(100L, "Admin");

        ReconcileResult<RoleFile> result = reconciler.reconcileRoles(existing, List.of(desired));

        assertEquals(0, result.toCreate().size());
        assertEquals(1, result.toUpdate().size());
        assertEquals(100L, result.toUpdate().get(0).discordId());
    }

    @Test
    void roles_idNotFoundInDiscord_fallsBackToNameMatch() {
        RoleFile desired = roleFile(999L, "Admin");
        Map<Long, String> existing = Map.of(100L, "Admin");

        ReconcileResult<RoleFile> result = reconciler.reconcileRoles(existing, List.of(desired));

        // ID 999 not in Discord, but name "Admin" matches → update
        assertEquals(0, result.toCreate().size());
        assertEquals(1, result.toUpdate().size());
        assertEquals(100L, result.toUpdate().get(0).discordId());
    }

    @Test
    void roles_emptyExisting_allDesiredAreCreated() {
        List<RoleFile> desired = List.of(roleFile(0L, "A"), roleFile(0L, "B"), roleFile(0L, "C"));

        ReconcileResult<RoleFile> result = reconciler.reconcileRoles(Map.of(), desired);

        assertEquals(3, result.toCreate().size());
        assertEquals(0, result.toUpdate().size());
    }

    @Test
    void roles_multipleDesired_mixedCreateAndUpdate() {
        RoleFile existing1 = roleFile(100L, "Admin");   // match by id
        RoleFile existing2 = roleFile(0L, "Member");    // match by name
        RoleFile newRole = roleFile(0L, "Visitor");     // not in Discord → create

        Map<Long, String> existing = Map.of(100L, "Admin", 200L, "Member");

        ReconcileResult<RoleFile> result = reconciler.reconcileRoles(existing, List.of(existing1, existing2, newRole));

        assertEquals(1, result.toCreate().size());
        assertEquals(2, result.toUpdate().size());
        assertEquals("Visitor", result.toCreate().get(0).name);
    }

    @Test
    void roles_duplicateNamesInDiscord_firstMatchWins() {
        // Two Discord roles named "Member" — first one in map should be matched
        RoleFile desired = roleFile(0L, "Member");
        Map<Long, String> existing = new java.util.LinkedHashMap<>();
        existing.put(200L, "Member");
        existing.put(300L, "Member");

        ReconcileResult<RoleFile> result = reconciler.reconcileRoles(existing, List.of(desired));

        assertEquals(1, result.toUpdate().size());
        assertEquals(200L, result.toUpdate().get(0).discordId());
    }

    // ── Channels ──────────────────────────────────────────────────────────────

    @Test
    void channels_newChannel_isCreated() {
        ChannelFile desired = channelFile(0L, "new-channel");
        Map<Long, String> existing = Map.of(500L, "general");

        ReconcileResult<ChannelFile> result = reconciler.reconcileChannels(existing, List.of(desired));

        assertEquals(1, result.toCreate().size());
        assertEquals(0, result.toUpdate().size());
    }

    @Test
    void channels_matchById_isUpdated() {
        ChannelFile desired = channelFile(500L, "general-renamed");
        Map<Long, String> existing = Map.of(500L, "general");

        ReconcileResult<ChannelFile> result = reconciler.reconcileChannels(existing, List.of(desired));

        assertEquals(0, result.toCreate().size());
        assertEquals(1, result.toUpdate().size());
        assertEquals(500L, result.toUpdate().get(0).discordId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RoleFile roleFile(long id, String name) {
        RoleFile f = new RoleFile();
        f.id = id;
        f.name = name;
        return f;
    }

    private ChannelFile channelFile(long id, String name) {
        TextChannelFile f = new TextChannelFile();
        f.id = id;
        f.name = name;
        return f;
    }
}
