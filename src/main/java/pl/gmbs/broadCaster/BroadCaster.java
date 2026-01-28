package pl.gmbs.broadCaster;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Plugin(
        id = "velocitybroadcast",
        name = "VELOCITYBROADCAST",
        version = "1.0"
)
public class BroadCaster implements SimpleCommand {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private String prefix = "&6[MESSAGE] ";

    private static final LegacyComponentSerializer COLOR =
            LegacyComponentSerializer.legacyAmpersand();

    @Inject
    public BroadCaster(ProxyServer proxy, Logger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDirectory;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        loadConfig();
        proxy.getCommandManager().register("bcall", this);
        logger.info("Plugin enabled!");
    }

    private void loadConfig() {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            Path config = dataDir.resolve("velobroadcast.conf");

            if (!Files.exists(config)) {
                Files.writeString(config,
                        "prefix = \"&6[MESSAGE] \"\n");
            }

            for (String line : Files.readAllLines(config)) {
                if (line.startsWith("prefix")) {
                    prefix = line.split("=", 2)[1]
                            .trim()
                            .replace("\"", "");
                }
            }

            logger.info("Config reloaded. Prefix: {}", prefix);

        } catch (IOException e) {
            logger.error("Error while reloading.", e);
        }
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!source.hasPermission("velobroadcast.reload")) {
                source.sendMessage(COLOR.deserialize("&cNo permission."));
                return;
            }

            loadConfig();
            source.sendMessage(COLOR.deserialize("&aConfig file reloaded!"));
            return;
        }

        if (!source.hasPermission("velobroadcast.send")) {
            source.sendMessage(COLOR.deserialize("&c!"));
            return;
        }

        if (args.length == 0) {
            source.sendMessage(COLOR.deserialize("&eUsage: &6/bcall <message>"));
            return;
        }

        String message = String.join(" ", args);
        Component broadcast = COLOR.deserialize(prefix + message);

        proxy.getAllPlayers().forEach(player ->
                player.sendMessage(broadcast)
        );
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of("reload");
    }
}
