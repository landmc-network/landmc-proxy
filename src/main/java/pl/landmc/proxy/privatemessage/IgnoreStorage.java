package pl.landmc.proxy.privatemessage;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code ignores.yml} - who has muted whom, kept across restarts.
 *
 * <p>A file rather than a database because the proxy has no database and this is a small,
 * rarely written map. If the network grows to several proxies it has to move somewhere shared,
 * since an ignore set on one proxy would not be seen by another - worth knowing before that
 * happens rather than after.
 */
public class IgnoreStorage extends OkaeriConfig {

    @Comment("UUID gracza -> lista UUID-ow, ktorych ignoruje.")
    @Comment("Plik zapisywany przy kazdej zmianie; edytowanie recznie nie jest potrzebne.")
    @CustomKey("ignored-players")
    public Map<UUID, Set<UUID>> ignoredPlayers = new LinkedHashMap<>();

    Set<UUID> ignoredBy(UUID playerId) {
        return this.ignoredPlayers.computeIfAbsent(playerId, key -> new LinkedHashSet<>());
    }
}
