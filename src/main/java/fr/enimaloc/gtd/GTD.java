package fr.enimaloc.gtd;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import fr.enimaloc.gtd.archive.MessageArchiver;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class GTD extends ListenerAdapter {
    public static final TomlMapper MAPPER = new TomlMapper();
    private final JDA jda;
    private final Config config;
    private final Map<Long, Server> servers = new HashMap<>();

    static void main(String... args) throws IOException {
        String tomlPath = System.getenv().getOrDefault("GTD_CONFIG", "./config.toml");
        if (!Files.exists(new File(tomlPath).toPath())) {
            MAPPER.writer().writeValue(new File(tomlPath), new Config());
        }
        GTD gtd = new GTD(MAPPER.readValue(new File(tomlPath), Config.class));
        gtd.init();
    }

    public GTD(Config config) {
        this(JDABuilder.createDefault(config.botToken)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build(), config);
    }

    public GTD(JDA jda, Config config) {
        this.jda = jda;
        this.config = config;
    }

    public void init() {
        jda.addEventListener(this);
        jda.addEventListener(new DiscordEventSync(servers));
        List<Command> commands = jda.retrieveCommands().complete();
        Set<String> existing = new HashSet<>();
        for (Command command : commands) {
            existing.add(command.getFullCommandName());
        }

        if (!existing.contains("init")) {
            jda.upsertCommand("init", "Initialize the git repository")
                    .addOption(OptionType.STRING, "repository", "Repository URL", true)
                    .queue();
        }
        if (!existing.contains("push")) {
            jda.upsertCommand("push", "Push changes to the git repository")
                    .queue();
        }
        if (!existing.contains("pull")) {
            jda.upsertCommand("pull", "Pull changes from the git repository")
                    .queue();
        }
        if (!existing.contains("archive")) {
            jda.upsertCommand("archive", "Archive l'historique complet des messages du serveur")
                    .queue();
        }
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
        if (!existing.contains("status")) {
            jda.upsertCommand("status", "Afficher l'état git du dépôt").queue();
        }
    }

    public static class Config {
        public String dataPath = "./data";
        public String botToken = System.getenv("DISCORD_TOKEN");
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        super.onGuildReady(event);

        for (Guild guild : jda.getGuilds()) {
            try {
                System.out.println("Initializing server for guild: " + guild.getName());
                servers.put(guild.getIdLong(), new Server(config, guild));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        super.onSlashCommandInteraction(event);
        Server server = servers.get(event.getGuild().getIdLong());
        if (server == null) {
            return;
        }
        if ("init".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                server.initGit(event.getOption("repository").getAsString(), event.getGuild());
                event.getHook().editOriginal("Git initialisé + état Discord exporté").queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec de l'init : " + e.getMessage()).queue();
            }
        } else if ("push".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                server.synchroAll(event.getGuild());
                event.getHook().editOriginal("État Discord exporté dans les fichiers").queue();
            } catch (IOException e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec du push : " + e.getMessage()).queue();
            }
        } else if ("pull".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                Server.PullResult result = server.pull(event.getGuild());
                event.getHook().editOriginal("Pull terminé : " + result).queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec du pull : " + e.getMessage()).queue();
            }
        } else if ("archive".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            try {
                MessageArchiver archiver = new MessageArchiver(server.gitPath());
                Map<String, Integer> counts = archiver.archive(event.getGuild());
                StringBuilder sb = new StringBuilder("Archive terminée :\n");
                counts.forEach((name, count) -> sb.append("• ").append(name).append(" : ").append(count).append(" messages\n"));
                String response = sb.toString();
                if (response.length() > 1900) {
                    int total = counts.values().stream().mapToInt(Integer::intValue).sum();
                    response = "Archive terminée : " + counts.size() + " channels, " + total + " messages au total.";
                }
                event.getHook().editOriginal(response).queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("Échec de l'archive : " + e.getMessage()).queue();
            }
        } else if ("branch".equals(event.getFullCommandName())) {
            event.deferReply(true).queue();
            if (server.gitOps() == null) {
                event.getHook().editOriginal("Git non initialisé — lancez /init d'abord").queue();
                return;
            }
            try {
                String subCmd = event.getSubcommandName();
                if (subCmd == null) {
                    event.getHook().editOriginal("Sous-commande manquante").queue();
                    return;
                }
                switch (subCmd) {
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
                        String response = sb.toString();
                        if (response.length() > 1900) response = "Trop de branches à afficher (" + branches.size() + " branches).";
                        event.getHook().editOriginal(response).queue();
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
                    response = "**Branche :** `" + status.branch() + "`\n"
                        + "**" + status.modified().size() + " modifié(s), "
                        + status.added().size() + " ajouté(s), "
                        + status.deleted().size() + " supprimé(s)**\n"
                        + "(liste tronquée — " + totalChanged + " fichier(s) au total)";
                }
                event.getHook().editOriginal(response).queue();
            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().editOriginal("Erreur status : " + e.getMessage()).queue();
            }
        }
    }
}
