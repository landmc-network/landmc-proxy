package pl.landmc.proxy.help;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The backend credits tutorial progress by the name this produces, so two spellings of the same
 * command have to reduce to the same word - and the arguments must not travel with it.
 */
class HelpProgressProtocolTest {

    private static String root(String commandLine) {
        return new String(HelpProgressProtocol.commandRoot(commandLine), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @CsvSource({
        "msg, msg",
        "'/msg', msg",
        "'//msg', msg",
        "'/ msg', msg",
        "'/MSG', msg",
        "'  /Msg  ', msg",
        "'landmc:msg', msg",
        "'/landmc:msg', msg"
    })
    @DisplayName("every spelling of one command reduces to the same name")
    void normalisesTheCommandName(String commandLine, String expected) {
        assertEquals(expected, root(commandLine));
    }

    @Test
    @DisplayName("arguments stay on the proxy - a private message is not tutorial data")
    void keepsArgumentsOutOfTheMessage() {
        assertEquals("msg", root("/msg Crispi tajna wiadomość"));
        assertEquals("setrank", root("setrank Crispi vip 30d"));
    }

    @Test
    @DisplayName("nothing to report is reported as nothing, not as an empty command")
    void producesNothingForAnEmptyLine() {
        assertEquals("", root(""));
        assertEquals("", root("   "));
        assertEquals("", root("/"));
        assertEquals("", root(null));
    }

    @Test
    @DisplayName("a bare namespace is kept rather than reduced to nothing")
    void keepsATrailingNamespaceIntact() {
        // "landmc:" has no command after the colon; dropping it would send an empty name.
        assertEquals("landmc:", root("/landmc:"));
    }
}
