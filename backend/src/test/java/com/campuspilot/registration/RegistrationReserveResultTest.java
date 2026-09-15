package com.campuspilot.registration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistrationReserveResultTest {
    @Test
    void mapsLuaReturnCodesToStableBusinessResults() {
        assertEquals(RegistrationReserveResult.SUCCESS, RegistrationReserveResult.fromCode(0));
        assertEquals(RegistrationReserveResult.OUT_OF_STOCK, RegistrationReserveResult.fromCode(1));
        assertEquals(RegistrationReserveResult.DUPLICATE, RegistrationReserveResult.fromCode(2));
        assertEquals(RegistrationReserveResult.NOT_STARTED, RegistrationReserveResult.fromCode(3));
        assertEquals(RegistrationReserveResult.ENDED, RegistrationReserveResult.fromCode(4));
        assertEquals(RegistrationReserveResult.UNAVAILABLE, RegistrationReserveResult.fromCode(99));
    }
}
