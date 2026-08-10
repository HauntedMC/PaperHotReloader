package acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** A deliberately small plugin built into a temporary jar by the acceptance test. */
public final class AcceptancePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("PHR_SAMPLE_ENABLED name=" + getName() + " marker=" + marker());
        if (getCommand("acceptanceprobe") != null) {
            getCommand("acceptanceprobe").setExecutor(this::onProbe);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("PHR_SAMPLE_DISABLED name=" + getName());
    }

    private boolean onProbe(CommandSender sender, Command command, String label, String[] args) {
        getLogger().info("PHR_SAMPLE_COMMAND name=" + getName() + " marker=" + marker());
        return true;
    }

    private String marker() {
        try (InputStream input = getResource("marker.txt")) {
            if (input == null) {
                return "missing";
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read sample marker", exception);
        }
    }
}
