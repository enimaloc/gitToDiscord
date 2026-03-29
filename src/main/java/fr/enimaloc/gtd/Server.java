package fr.enimaloc.gtd;

import com.fasterxml.jackson.databind.ObjectWriter;
import fr.enimaloc.gtd.file.*;
import fr.enimaloc.gtd.asset.AssetDownloader;
import fr.enimaloc.gtd.pull.PullExecutor;
import fr.enimaloc.gtd.pull.PullReconciler;
import fr.enimaloc.gtd.pull.ReconcileResult;
import fr.enimaloc.gtd.pull.TempIdUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.sticker.GuildSticker;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Server {
    private final GitConfig config;
    private final long serverId;
    private final Path dataPath;
    private final Path gitPath;
    private final CredentialsProvider credentials;
    private Git git;
    private GitOperationService gitOps;

    public Path gitPath() {
        return gitPath;
    }

    public GitOperationService gitOps() { return gitOps; }

    public void updateBranchConfig(String branch) throws IOException {
        config.branch = branch;
        GTD.MAPPER.writer().writeValue(dataPath.resolve("config.toml").toFile(), config);
    }

    public Server(GTD.Config config, Guild guild) throws IOException {
        this.serverId = guild.getIdLong();
        this.dataPath = Path.of(config.dataPath).resolve(String.valueOf(guild.getIdLong()));
        Files.createDirectories(this.dataPath);
        Path lConfig = dataPath.resolve("config.toml");
        if (!Files.exists(lConfig)) {
            GTD.MAPPER.writer().writeValue(lConfig.toFile(), new GitConfig());
        }
        this.config = GTD.MAPPER.readValue(lConfig.toFile(), GitConfig.class);
        this.gitPath = dataPath.resolve("git");
        String t = config.gitToken;
        this.credentials = (t != null && !t.isBlank())
            ? new UsernamePasswordCredentialsProvider("oauth2", t) : null;
        try {
            this.git = Git.open(gitPath.toFile());
            this.gitOps = new GitOperationService(this.git, this.credentials);
            if (this.git.getRepository().resolve("HEAD") != null) {
                this.git.checkout().setName(this.config.branch).call();
            }
            synchroAll(guild);
        } catch (RepositoryNotFoundException ign) {
        } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
            this.git.close();
            throw new IOException("Failed to checkout branch " + this.config.branch, e);
        }
    }

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

    public void synchroAll(Guild guild) throws IOException {
        ObjectWriter writer = GTD.MAPPER.writer();

        // Guild
        GuildFile guildFile = POJOUtils.parse(guild);
        writer.writeValue(gitPath.resolve(GuildFile.FILE_PATH).toFile(), guildFile);

        // Assets guild
        Files.createDirectories(gitPath.resolve("assets"));
        if (guildFile.profil.icon      != null) AssetDownloader.download(guildFile.profil.icon,      gitPath.resolve("assets/icon.webp"));
        if (guildFile.profil.bannerUrl != null) AssetDownloader.download(guildFile.profil.bannerUrl,  gitPath.resolve("assets/banner.webp"));
        if (guildFile.profil.splashUrl != null) AssetDownloader.download(guildFile.profil.splashUrl,  gitPath.resolve("assets/splash.webp"));

        // Emojis
        Files.createDirectories(gitPath.resolve("emojis"));
        for (RichCustomEmoji emoji : guild.getEmojis()) {
            EmoteFile ef = POJOUtils.parse(emoji);
            writer.writeValue(
                gitPath.resolve(EmoteFile.FILE_PATH.formatted(emoji.getIdLong())).toFile(), ef);
            if (ef.url != null)
                AssetDownloader.download(ef.url, gitPath.resolve("emojis/" + emoji.getIdLong() + ".webp"));
        }

        // Stickers
        Files.createDirectories(gitPath.resolve("stickers"));
        for (GuildSticker sticker : guild.getStickers()) {
            StickerFile sf = POJOUtils.parse(sticker);
            writer.writeValue(
                gitPath.resolve(StickerFile.FILE_PATH.formatted(sticker.getIdLong())).toFile(), sf);
            if (sf.url != null)
                AssetDownloader.download(sf.url, gitPath.resolve("stickers/" + sticker.getIdLong() + ".webp"));
        }

        // Roles
        Files.createDirectories(gitPath.resolve("roles"));
        for (Role role : guild.getRoles()) {
            RoleFile rf = POJOUtils.parse(role);
            writer.writeValue(
                gitPath.resolve(RoleFile.FILE_PATH.formatted(role.getIdLong())).toFile(), rf);
            if (rf.iconUrl != null)
                AssetDownloader.download(rf.iconUrl, gitPath.resolve("roles/" + role.getIdLong() + ".webp"));
        }

        // Categories
        Files.createDirectories(gitPath.resolve("categories"));
        for (Category category : guild.getCategories()) {
            writer.writeValue(
                gitPath.resolve(CategoryFile.FILE_PATH.formatted(category.getIdLong())).toFile(),
                POJOUtils.parse(category));
        }

        // Channels (text, voice, news, stage, forum, media)
        List<GuildChannel> channels = guild.getChannels();
        Files.createDirectories(gitPath.resolve("channels"));
        for (GuildChannel channel : channels) {
            if (channel instanceof Category || channel instanceof ThreadChannel) continue;
            ChannelFile channelFile = POJOUtils.parseChannel(channel);
            if (channelFile == null) continue;
            writer.writeValue(
                gitPath.resolve(ChannelFile.FILE_PATH.formatted(channel.getIdLong())).toFile(),
                channelFile);
        }

        // Threads (actifs + archivés)
        for (GuildChannel channel : channels) {
            if (!(channel instanceof IThreadContainer container)) continue;
            for (ThreadChannel thread : container.getThreadChannels()) {
                writeThread(writer, thread);
            }
            for (ThreadChannel thread : container.retrieveArchivedPublicThreadChannels().cache(false)) {
                writeThread(writer, thread);
            }
        }
    }

    private void writeThread(ObjectWriter writer, ThreadChannel thread) throws IOException {
        long parentId = thread.getParentChannel().getIdLong();
        Path dir = gitPath.resolve("threads").resolve(String.valueOf(parentId));
        Files.createDirectories(dir);
        writer.writeValue(
            gitPath.resolve(ThreadChannelFile.FILE_PATH.formatted(parentId, thread.getIdLong())).toFile(),
            POJOUtils.parseThread(thread));
    }

    public record PullResult(int created, int updated, int deleted) {
        @Override public String toString() {
            return created + " créée(s), " + updated + " mise(s) à jour, " + deleted + " supprimée(s)";
        }
    }

    public PullResult pull(Guild guild) throws IOException, GitAPIException {
        if (git == null) throw new IllegalStateException("Git not initialized — run /init first");
        gitOps.pull();
        return applyGitState(guild);
    }

    public PullResult applyGitState(Guild guild) throws IOException, GitAPIException {
        if (git == null) throw new IllegalStateException("Git not initialized — run /init first");
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

        int created = 0, updated = 0, deleted = 0;

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
        for (long id : roles.toDelete()) { executor.deleteRole(id); deleted++; }

        Map<Long, String> existingCats = guild.getCategories().stream()
                .collect(Collectors.toMap(Category::getIdLong, Category::getName));
        ReconcileResult<CategoryFile> cats = reconciler.reconcileCategories(existingCats, catFiles);
        for (CategoryFile f : cats.toCreate()) {
            Category created_ = executor.createCategory(f);
            if (TempIdUtils.isTemp(f.id)) tempToReal.put(f.id, created_.getIdLong());
            created++;
        }
        for (var e : cats.toUpdate()) { executor.applyCategory(e.discordId(), e.file()); updated++; }
        for (long id : cats.toDelete()) { executor.deleteCategory(id); deleted++; }

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
        for (long id : channels.toDelete()) { executor.deleteChannel(id); deleted++; }

        if (!tempToReal.isEmpty()) {
            resolveTempIds(tempToReal);
            gitOps.commitAndPush("fix: resolve temp IDs to Discord IDs");
        }

        return new PullResult(created, updated, deleted);
    }

    private void resolveTempIds(Map<Long, Long> tempToReal) throws IOException {
        if (tempToReal.isEmpty()) return;

        // 1. Rename files and update the id field
        for (Map.Entry<Long, Long> entry : tempToReal.entrySet()) {
            long tempId = entry.getKey();
            long realId = entry.getValue();

            for (String folder : List.of("roles", "categories", "channels")) {
                Path tempFile = gitPath.resolve(folder).resolve(tempId + ".toml");
                if (Files.exists(tempFile)) {
                    Map<String, Object> raw = GTD.MAPPER.readValue(tempFile.toFile(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    raw.put("id", realId);
                    Path realFile = gitPath.resolve(folder).resolve(realId + ".toml");
                    GTD.MAPPER.writer().writeValue(realFile.toFile(), raw);
                    Files.delete(tempFile);
                    break;
                }
            }
        }

        // 2. Walk all files and replace cross-references
        for (String folder : List.of("roles", "categories", "channels")) {
            Path dir = gitPath.resolve(folder);
            if (!Files.exists(dir)) continue;
            try (var stream = Files.walk(dir, 1)) {
                stream.filter(p -> p.toString().endsWith(".toml")).forEach(p -> {
                    try {
                        Map<String, Object> raw = GTD.MAPPER.readValue(p.toFile(),
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                        boolean changed = false;

                        // parentCategoryId
                        if (raw.containsKey("parentCategoryId")) {
                            long val = ((Number) raw.get("parentCategoryId")).longValue();
                            if (tempToReal.containsKey(val)) {
                                raw.put("parentCategoryId", tempToReal.get(val));
                                changed = true;
                            }
                        }

                        // roleOverride keys
                        if (raw.containsKey("roleOverride")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> overrides = (Map<String, Object>) raw.get("roleOverride");
                            Map<String, Object> updated = new LinkedHashMap<>();
                            boolean overrideChanged = false;
                            for (Map.Entry<String, Object> e : overrides.entrySet()) {
                                long key = Long.parseLong(e.getKey());
                                long resolvedKey = tempToReal.getOrDefault(key, key);
                                updated.put(String.valueOf(resolvedKey), e.getValue());
                                if (resolvedKey != key) overrideChanged = true;
                            }
                            if (overrideChanged) { raw.put("roleOverride", updated); changed = true; }
                        }

                        // memberOverride keys
                        if (raw.containsKey("memberOverride")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> overrides = (Map<String, Object>) raw.get("memberOverride");
                            Map<String, Object> updated = new LinkedHashMap<>();
                            boolean overrideChanged = false;
                            for (Map.Entry<String, Object> e : overrides.entrySet()) {
                                long key = Long.parseLong(e.getKey());
                                long resolvedKey = tempToReal.getOrDefault(key, key);
                                updated.put(String.valueOf(resolvedKey), e.getValue());
                                if (resolvedKey != key) overrideChanged = true;
                            }
                            if (overrideChanged) { raw.put("memberOverride", updated); changed = true; }
                        }

                        if (changed) GTD.MAPPER.writer().writeValue(p.toFile(), raw);
                    } catch (IOException e) { throw new UncheckedIOException(e); }
                });
            }
        }
    }

    private <T> T readToml(Path path, Class<T> type) throws IOException {
        if (!Files.exists(path)) return null;
        return GTD.MAPPER.readValue(path.toFile(), type);
    }

    private <T> List<T> readTomlDir(Path dir, Class<T> type) throws IOException {
        if (!Files.exists(dir)) return List.of();
        try (var stream = Files.walk(dir, 1)) {
            return stream
                .filter(p -> p.toString().endsWith(".toml"))
                .map(p -> { try { return GTD.MAPPER.readValue(p.toFile(), type); }
                            catch (IOException e) { throw new UncheckedIOException(e); }})
                .toList();
        }
    }

    private <T> Map<Path, T> readTomlDirWithPaths(Path dir, Class<T> type) throws IOException {
        if (!Files.exists(dir)) return new LinkedHashMap<>();
        Map<Path, T> result = new LinkedHashMap<>();
        try (var stream = Files.walk(dir, 1)) {
            stream
                .filter(p -> p.toString().endsWith(".toml"))
                .forEach(p -> {
                    try { result.put(p, GTD.MAPPER.readValue(p.toFile(), type)); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                });
        }
        return result;
    }

    private Map<Path, ChannelFile> readChannelFilesWithPaths() throws IOException {
        Path dir = gitPath.resolve("channels");
        if (!Files.exists(dir)) return new LinkedHashMap<>();
        Map<Path, ChannelFile> result = new LinkedHashMap<>();
        try (var stream = Files.walk(dir, 1)) {
            stream
                .filter(p -> p.toString().endsWith(".toml"))
                .forEach(p -> {
                    try {
                        Map<?, ?> raw = GTD.MAPPER.readValue(p.toFile(), Map.class);
                        String type = (String) raw.get("type");
                        Class<? extends ChannelFile> clazz = switch (type != null ? type : "") {
                            case "text"  -> TextChannelFile.class;
                            case "voice" -> VoiceChannelFile.class;
                            case "news"  -> NewsChannelFile.class;
                            case "stage" -> StageChannelFile.class;
                            case "forum" -> ForumChannelFile.class;
                            case "media" -> MediaChannelFile.class;
                            default      -> ChannelFile.class;
                        };
                        result.put(p, GTD.MAPPER.readValue(p.toFile(), clazz));
                    } catch (IOException e) { throw new UncheckedIOException(e); }
                });
        }
        return result;
    }

    private List<ChannelFile> readChannelFiles() throws IOException {
        Path dir = gitPath.resolve("channels");
        if (!Files.exists(dir)) return List.of();
        List<ChannelFile> result = new ArrayList<>();
        try (var stream = Files.walk(dir, 1)) {
            stream
                .filter(p -> p.toString().endsWith(".toml"))
                .forEach(p -> {
                    try {
                        Map<?, ?> raw = GTD.MAPPER.readValue(p.toFile(), Map.class);
                        String type = (String) raw.get("type");
                        Class<? extends ChannelFile> clazz = switch (type != null ? type : "") {
                            case "text"  -> TextChannelFile.class;
                            case "voice" -> VoiceChannelFile.class;
                            case "news"  -> NewsChannelFile.class;
                            case "stage" -> StageChannelFile.class;
                            case "forum" -> ForumChannelFile.class;
                            case "media" -> MediaChannelFile.class;
                            default      -> ChannelFile.class;
                        };
                        result.add(GTD.MAPPER.readValue(p.toFile(), clazz));
                    } catch (IOException e) { throw new UncheckedIOException(e); }
                });
        }
        return result;
    }

    public static class GitConfig {
        public String branch = "main";
        public String gitToken;
    }
}
