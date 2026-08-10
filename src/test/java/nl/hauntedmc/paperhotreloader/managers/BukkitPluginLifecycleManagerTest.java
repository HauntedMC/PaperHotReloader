package nl.hauntedmc.paperhotreloader.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

class BukkitPluginLifecycleManagerTest {

    @Test
    void ordersHardDependenciesBeforeTheirConsumersRegardlessOfInputOrder() throws Exception {
        Map<String, PluginDescriptionFile> descriptions = new LinkedHashMap<>();
        descriptions.put("consumer", description("Consumer", "depend: [Dependency]"));
        descriptions.put("independent", description("Independent", ""));
        descriptions.put("dependency", description("Dependency", ""));

        assertEquals(List.of("dependency", "independent", "consumer"),
                BukkitPluginLifecycleManager.dependencyOrder(descriptions));
    }

    @Test
    void rejectsCircularHardDependencies() throws Exception {
        Map<String, PluginDescriptionFile> descriptions = new LinkedHashMap<>();
        descriptions.put("first", description("First", "depend: [Second]"));
        descriptions.put("second", description("Second", "depend: [First]"));

        assertNull(BukkitPluginLifecycleManager.dependencyOrder(descriptions));
    }

    private static PluginDescriptionFile description(String name, String additionalYaml) throws Exception {
        return new PluginDescriptionFile(new StringReader("name: " + name + "\nversion: 1.0\nmain: example."
                + name + "\n" + additionalYaml + "\n"));
    }
}
