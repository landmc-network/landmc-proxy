package pl.landmc.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the seams around plugins that may not be installed.
 *
 * <p>Written after the proxy refused to start on a server without LuckPerms. The provider did
 * catch {@code NoClassDefFoundError}, but that was never reached: a class is verified when it
 * is first loaded, and verification resolves the types its methods refer to, so a class that
 * mentions a missing library throws before any of its own code runs. Catching the error inside
 * such a class is too late by construction, and no unit test that runs with the library on the
 * classpath can notice.
 *
 * <p>So the rule is structural instead: exactly one class may refer to each optional library,
 * and nothing loads that class until the library is known to be present. This reads the
 * compiled classes and enforces it.
 */
class OptionalDependencyIsolationTest {

    /** Each optional library, and the single class allowed to name its types. */
    private static final Map<String, String> ISOLATED_TO = Map.of(
            "net/luckperms/", "pl/landmc/proxy/rank/LuckPermsRankProvider",
            "com/github/retrooper/packetevents/", "pl/landmc/proxy/cooldown/PacketEventsGuiInterceptor");

    @Test
    @DisplayName("only one class per optional plugin refers to it, so the rest load without it")
    void keepsOptionalDependenciesToASingleClass() {
        Path classes = compiledClasses();

        for (Map.Entry<String, String> rule : ISOLATED_TO.entrySet()) {
            // Nested classes are part of the isolating class and load with it.
            Set<String> offenders = classesReferring(classes, rule.getKey());
            offenders.removeIf(name -> name.equals(rule.getValue())
                    || name.startsWith(rule.getValue() + "$"));

            assertEquals(
                    Set.of(),
                    offenders,
                    rule.getKey() + " may only be named by " + rule.getValue()
                            + "; these classes would fail to load without the plugin installed");
        }
    }

    @Test
    @DisplayName("the isolating classes really are the ones holding the reference")
    void provesTheRuleIsNotVacuous() {
        Path classes = compiledClasses();

        // If a rename ever emptied these, the test above would pass while enforcing nothing.
        for (Map.Entry<String, String> rule : ISOLATED_TO.entrySet()) {
            assertTrue(
                    classesReferring(classes, rule.getKey()).contains(rule.getValue()),
                    rule.getValue() + " no longer refers to " + rule.getKey());
        }
    }

    /**
     * Class files whose constant pool mentions the package.
     *
     * <p>A crude scan of the bytes, deliberately: it needs no library, and a type reference is
     * always present verbatim as {@code some/package/Type} in the constant pool.
     */
    private static Set<String> classesReferring(Path root, String packagePath) {
        try (Stream<Path> files = Files.walk(root)) {
            Set<String> found = new TreeSet<>();
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                if (bytes.contains(packagePath)) {
                    String name = root.relativize(file).toString().replace('\\', '/');
                    found.add(name.substring(0, name.length() - ".class".length()));
                }
            }
            return found;
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path compiledClasses() {
        Path here = Path.of(
                OptionalDependencyIsolationTest.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .getPath()
                        .replaceFirst("^/([A-Za-z]:)", "$1"));

        // .../build/classes/java/test -> .../build/classes/java/main
        Path main = here.resolveSibling("main");
        assertTrue(Files.isDirectory(main), "compiled classes not found at " + main);
        assertTrue(
                Files.isDirectory(main.resolve("pl/landmc/proxy")),
                "compiled classes not found at " + main);
        return main;
    }

    @Test
    @DisplayName("SkinsRestorer is reached by reflection, so no class names its types")
    void doesNotLinkAgainstSkinsRestorerAtAll() {
        // The bridge holds the names as strings, which is what keeps them out of the constant
        // pool as type references - the check is that no class links against them.
        assertEquals(List.of(), List.copyOf(classesReferring(compiledClasses(), "net/skinsrestorer/")));
    }
}
