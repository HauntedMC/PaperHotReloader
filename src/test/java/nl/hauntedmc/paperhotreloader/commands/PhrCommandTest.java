package nl.hauntedmc.paperhotreloader.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PhrCommandTest {

    @Test
    void completesPluginNamesAndForceFlagsCaseInsensitively() {
        assertEquals(List.of("--force"), PhrCommand.matching("--", List.of("PhrSample", "--force", "-f")));
        assertEquals(List.of("PhrSample"), PhrCommand.matching("phrs", List.of("Other", "PhrSample")));
    }

    @Test
    void hidesTheRootCommandFromPlayersWithoutAnyPhrPermission() {
        assertFalse(PhrCommand.isDiscoverableBy(senderWith()));
        assertTrue(PhrCommand.isDiscoverableBy(senderWith("paperhotreloader.help")));
        assertTrue(PhrCommand.isDiscoverableBy(senderWith("paperhotreloader.*")));
    }

    private static CommandSender senderWith(String... permissions) {
        return (CommandSender) Proxy.newProxyInstance(
                PhrCommandTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission") && args.length == 1 && args[0] instanceof String permission) {
                        return List.of(permissions).contains(permission);
                    }
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(double.class)) return 0D;
                    return null;
                }
        );
    }
}
