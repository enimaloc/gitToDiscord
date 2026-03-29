package fr.enimaloc.gtd.pull;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TempIdUtilsTest {

    @Test
    void smallInteger_isTemp() {
        assertTrue(TempIdUtils.isTemp(1L));
        assertTrue(TempIdUtils.isTemp(42L));
        assertTrue(TempIdUtils.isTemp(999_999L));
        assertTrue(TempIdUtils.isTemp(999_999_999_999_999L)); // juste en-dessous du seuil
    }

    @Test
    void zero_isNotTemp() {
        assertFalse(TempIdUtils.isTemp(0L));
    }

    @Test
    void discordSnowflake_isNotTemp() {
        assertFalse(TempIdUtils.isTemp(1_038_139_412_753_694_814L)); // ID Discord réel
        assertFalse(TempIdUtils.isTemp(1_000_000_000_000_000L));     // exactement le seuil
    }
}
