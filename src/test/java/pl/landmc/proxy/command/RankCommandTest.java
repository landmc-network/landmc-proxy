package pl.landmc.proxy.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The time in "rank X for Y" is read by the person who just typed it, so it should come back
 * looking like what they wrote rather than as an ISO duration.
 */
class RankCommandTest {

    @ParameterizedTest
    @CsvSource({
        "P30D, 30d",
        "PT12H, 12h",
        "PT90M, 1h 30m",
        "P1DT2H30M, 1d 2h 30m",
        "PT45S, 45s",
        "PT0S, ''",
        "PT1H0M30S, 1h"
    })
    @DisplayName("a duration reads back the way it was typed in")
    void formatsADurationTheWayItWasTyped(String iso, String expected) {
        assertEquals(expected, RankCommand.format(Duration.parse(iso)));
    }
}
