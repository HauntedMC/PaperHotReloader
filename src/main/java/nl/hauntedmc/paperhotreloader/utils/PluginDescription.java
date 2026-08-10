package nl.hauntedmc.paperhotreloader.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarFile;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;

/** Reads Bukkit plugin metadata from a jar without loading the jar. */
public final class PluginDescription {

    private PluginDescription() {
    }

    public static PluginDescriptionFile read(File file) throws IOException, InvalidDescriptionException {
        try (JarFile jar = new JarFile(file)) {
            java.util.jar.JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                throw new IOException("plugin.yml is missing");
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return new PluginDescriptionFile(input);
            }
        }
    }

    public static List<String> hardDependencies(PluginDescriptionFile description) {
        return description.getDepend();
    }
}
