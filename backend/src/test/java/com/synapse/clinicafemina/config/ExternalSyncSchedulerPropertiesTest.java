package com.synapse.clinicafemina.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExternalSyncSchedulerPropertiesTest {

    @Test
    void should_allow_disabled_scheduler_without_cron() {
        ExternalSyncSchedulerProperties properties = new ExternalSyncSchedulerProperties();

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void should_reject_enabled_scheduler_without_valid_cron() {
        ExternalSyncSchedulerProperties properties = new ExternalSyncSchedulerProperties();
        properties.setEnabled(true);
        properties.setCron("");

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void should_reject_invalid_timezone_and_oversized_window() {
        ExternalSyncSchedulerProperties invalidTimezone = configured();
        invalidTimezone.setTimezone("America/NoSuchPlace");

        ExternalSyncSchedulerProperties oversizedWindow = configured();
        oversizedWindow.setStartDaysBack(367);

        assertThrows(IllegalStateException.class, invalidTimezone::validate);
        assertThrows(IllegalStateException.class, oversizedWindow::validate);
    }

    private ExternalSyncSchedulerProperties configured() {
        ExternalSyncSchedulerProperties properties = new ExternalSyncSchedulerProperties();
        properties.setEnabled(true);
        properties.setCron("0 0 * * * *");
        return properties;
    }
}
