package nl.hauntedmc.paperhotreloader.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PhrCommandTest {

    @Test
    void completesPluginNamesAndForceFlagsCaseInsensitively() {
        assertEquals(List.of("--force"), PhrCommand.matching("--", List.of("PhrSample", "--force", "-f")));
        assertEquals(List.of("PhrSample"), PhrCommand.matching("phrs", List.of("Other", "PhrSample")));
    }
}
