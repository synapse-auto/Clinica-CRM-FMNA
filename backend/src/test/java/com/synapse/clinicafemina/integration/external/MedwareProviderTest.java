package com.synapse.clinicafemina.integration.external;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedwareProviderTest {

    @Test
    void should_fail_safely_when_medware_credentials_are_missing() {
        MedwareProvider provider = new MedwareProvider("", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.getPatients(null, null, 50));

        assertTrue(ex.getMessage().contains("MEDWARE_API_URL"));
        assertTrue(ex.getMessage().contains("MEDWARE_API_TOKEN"));
    }

    @Test
    void should_be_explicit_skeleton_even_when_medware_credentials_exist() {
        MedwareProvider provider = new MedwareProvider("https://medware.example", "token");

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> provider.getAppointments(null, null, 50));

        assertTrue(ex.getMessage().contains("skeleton"));
        assertTrue(ex.getMessage().contains("documentação"));
    }
}
