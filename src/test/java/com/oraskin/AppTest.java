package com.oraskin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppTest {

    @Test
    void mainRejectsNonNumericPort() {
        assertThatThrownBy(() -> App.main(new String[]{"invalid"}))
                .isInstanceOf(NumberFormatException.class);
    }
}
