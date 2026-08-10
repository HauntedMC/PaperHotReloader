package nl.hauntedmc.paperhotreloader.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginDescriptionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsMetadataWithoutLoadingThePluginJar() throws Exception {
        Path jar = writeJar("sample.jar", "name: Sample\nversion: 1.0\nmain: example.Sample\ndepend: [Base]\n");

        var description = PluginDescription.read(jar.toFile());

        assertEquals("Sample", description.getName());
        assertEquals("1.0", description.getVersion());
        assertEquals("example.Sample", description.getMain());
        assertEquals(java.util.List.of("Base"), PluginDescription.hardDependencies(description));
    }

    @Test
    void rejectsJarsWithoutPluginMetadata() throws Exception {
        Path jar = temporaryDirectory.resolve("not-a-plugin.jar");
        try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry("example.txt"));
            archive.write("not a plugin".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            archive.closeEntry();
        }

        IOException exception = assertThrows(IOException.class, () -> PluginDescription.read(jar.toFile()));

        assertEquals("plugin.yml is missing", exception.getMessage());
    }

    private Path writeJar(String name, String pluginYaml) throws IOException {
        Path jar = temporaryDirectory.resolve(name);
        try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
            archive.putNextEntry(new JarEntry("plugin.yml"));
            archive.write(pluginYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return jar;
    }
}
