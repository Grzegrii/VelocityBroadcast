package pl.gmbs.broadCaster;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;


import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.nio.file.Files;


@Plugin(
        id = "velobroadcast",
        name = "VeloBroadcast",
        version = "2.0"
)
public class BroadCaster implements SimpleCommand {

    private final ProxyServer proxy;
    private final Logger logger;
    private String prefix = "&6[Broadcast] ";
    @DataDirectory final Path dataDir;
    private final PluginContainer plugin;
    private Path bossBarFile;



    private static final LegacyComponentSerializer COLOR =
            LegacyComponentSerializer.legacyAmpersand();

    private final Map<String, BossBar> bossBars = new HashMap<>();
    private final Map<String, Component> actionBars = new HashMap<>();

    @Inject
    public BroadCaster(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, PluginContainer plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
        this.dataDir = dataDirectory;
    }


    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        proxy.getCommandManager().register("btitle", this);
        proxy.getCommandManager().register("bactionbar", this);
        proxy.getCommandManager().register("bbossbar", this);
        proxy.getCommandManager().register("delbar", this);
        loadConfig();
        proxy.getCommandManager().register("bcall", this);
        bossBarFile = dataDir.resolve("bossbardata.yml");
        loadBossBars();

    }

    private void saveBossBars() {
        try {
            YamlConfigurationLoader loader =
                    YamlConfigurationLoader.builder()
                            .path(bossBarFile)
                            .build();

            ConfigurationNode root = loader.load();
            root.node("bossbars").set(null); // czyść

            for (Map.Entry<String, BossBar> entry : bossBars.entrySet()) {
                String id = entry.getKey();
                BossBar bar = entry.getValue();

                ConfigurationNode node = root.node("bossbars", id);
                node.node("text").set(
                        LegacyComponentSerializer.legacyAmpersand()
                                .serialize(bar.name())
                );
                node.node("color").set(bar.color().name());
                node.node("style").set(bar.overlay().name());
            }

            loader.save(root);
        } catch (Exception e) {
            logger.error("Nie można zapisać bossbardata.yml", e);
        }
    }




    private void loadBossBars() {
        if (!Files.exists(bossBarFile)) return;

        try {
            YamlConfigurationLoader loader =
                    YamlConfigurationLoader.builder()
                            .path(bossBarFile)
                            .build();

            ConfigurationNode root = loader.load();
            ConfigurationNode bars = root.node("bossbars");

            if (bars.virtual()) return;

            for (ConfigurationNode node : bars.childrenMap().values()) {
                String id = node.key().toString();

                Component text = LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(node.node("text").getString(""));

                BossBar.Color color =
                        BossBar.Color.valueOf(
                                node.node("color").getString("PURPLE")
                        );

                BossBar.Overlay style =
                        BossBar.Overlay.valueOf(
                                node.node("style").getString("PROGRESS")
                        );

                BossBar bar = BossBar.bossBar(text, 1.0f, color, style);
                bossBars.put(id, bar);
                saveBossBars();

                proxy.getAllPlayers().forEach(p -> p.showBossBar(bar));
            }

            logger.info("Załadowano {} bossbarów", bossBars.size());

        } catch (Exception e) {
            logger.error("Błąd wczytywania bossbardata.yml", e);
        }
    }


    static class StoredBossBar {
        BossBar bar;
        Set<String> servers; // null lub puste = wszystkie serwery

        StoredBossBar(BossBar bar, Set<String> servers) {
            this.bar = bar;
            this.servers = servers;
        }
    }


    @Subscribe
    public void onPlayerJoin(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        bossBars.values().forEach(player::showBossBar);

        // actionBars.values().forEach(player::sendActionBar);
    }


    /* ================= COMMAND ================= */

    @Override
    public void execute(Invocation inv) {
        String cmd = inv.alias().toLowerCase();
        String raw = String.join(" ", inv.arguments());

        switch (cmd) {
            case "btitle" -> handleTitle(inv, raw);
            case "bactionbar" -> handleActionBar(inv, raw);
            case "bbossbar" -> handleBossBar(inv, raw);
            case "delbar" -> handleDelBar(inv);
            case "bcall" -> handleBCall(inv);

        }
    }

    private void loadConfig() {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            Path config = dataDir.resolve("velobroadcast.conf");

            if (!Files.exists(config)) {
                Files.writeString(config,
                        "prefix = \"&6[Broadcast] \"\n");
            }

            for (String line : Files.readAllLines(config)) {
                if (line.startsWith("prefix")) {
                    prefix = line.split("=", 2)[1]
                            .trim()
                            .replace("\"", "");
                }
            }
        } catch (IOException e) {
            logger.error("Błąd ładowania configu!", e);
        }
    }


    private void handleBCall(Invocation inv) {
        if (!inv.source().hasPermission("velobroadcast.send")) {
            inv.source().sendMessage(COLOR.deserialize("&cBrak permisji!"));
            return;
        }

        String[] args = inv.arguments();

        /* ---- /bcall reload ---- */
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!inv.source().hasPermission("velobroadcast.reload")) {
                inv.source().sendMessage(COLOR.deserialize("&cBrak permisji!"));
                return;
            }

            loadConfig();
            inv.source().sendMessage(
                    COLOR.deserialize("&aConfig przeładowany!")
            );
            return;
        }

        /* ---- /bcall <msg> ---- */
        if (args.length == 0) {
            inv.source().sendMessage(
                    COLOR.deserialize("&eUżycie: &6/bcall <wiadomość>")
            );
            return;
        }

        Component broadcast = COLOR.deserialize(
                prefix + String.join(" ", args)
        );

        proxy.getAllPlayers().forEach(p ->
                p.sendMessage(broadcast)
        );
    }



    /* ================= TITLE ================= */

    private void handleTitle(Invocation inv, String raw) {
        if (!inv.source().hasPermission("velobroadcast.title")) return;

        List<String> args = parseQuoted(raw);
        if (args.size() < 5) return;

        Component title = args.get(0).equals("$none") ? Component.empty() : COLOR.deserialize(args.get(0));
        Component sub = args.get(1).equals("$none") ? Component.empty() : COLOR.deserialize(args.get(1));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(Integer.parseInt(args.get(2)) * 50L),
                Duration.ofMillis(Integer.parseInt(args.get(3)) * 50L),
                Duration.ofMillis(Integer.parseInt(args.get(4)) * 50L)
        );

        Title t = Title.title(title, sub, times);

        proxy.getAllPlayers().forEach(p -> p.showTitle(t));
    }

    /* ================= ACTIONBAR ================= */

    private void handleActionBar(Invocation inv, String raw) {
        if (!inv.source().hasPermission("velobroadcast.actionbar")) return;

        List<String> args = parseQuoted(raw);
        if (args.size() < 2) return;

        String id = args.get(0);
        Component text = COLOR.deserialize(args.get(1));
        actionBars.put(id, text);

        proxy.getAllPlayers().forEach(p -> p.sendActionBar(text));
    }

    /* ================= BOSSBAR ================= */

    private void handleBossBar(Invocation inv, String raw) {
        if (!inv.source().hasPermission("velobroadcast.bossbar")) return;

        List<String> args = parseQuoted(raw);
        if (args.size() < 2) return;

        String id = args.get(0);
        Component text = COLOR.deserialize(args.get(1));

        BossBar.Color color = BossBar.Color.PURPLE;
        BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;

        if (args.size() >= 3) color = BossBar.Color.valueOf(args.get(2).toUpperCase());
        if (args.size() >= 4) overlay = BossBar.Overlay.valueOf(args.get(3).toUpperCase());

        BossBar bar = BossBar.bossBar(text, 1.0f, color, overlay);
        bossBars.put(id, bar);

        proxy.getAllPlayers().forEach(p -> p.showBossBar(bar));
    }

    /* ================= DELBAR ================= */

    private void handleDelBar(Invocation inv) {
        if (!inv.source().hasPermission("velobroadcast.delbar")) return;
        if (inv.arguments().length < 1) return;

        String id = inv.arguments()[0];

        if (bossBars.containsKey(id)) {
            BossBar bar = bossBars.remove(id);
            proxy.getAllPlayers().forEach(p -> p.hideBossBar(bar));
            saveBossBars();
        }

        actionBars.remove(id);
    }

    /* ================= UTILS ================= */

    private List<String> parseQuoted(String input) {
        List<String> list = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(input);
        while (m.find()) {
            list.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return list;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();

    }
}
