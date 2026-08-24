package com.mobily.qalite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class QaliteApplicationTests {

    @Test
    void applicationClassIsAvailable() {
        assertDoesNotThrow(() -> Class.forName(QaliteApplication.class.getName()));
    }

}
