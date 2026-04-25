package com.oraskin.common.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderUtilTest {

    @Test
    void buildHeaderEncodesSmallMediumAndLargePayloadLengths() {
        assertThat(HeaderUtil.buildHeader(FrameType.TEXT, 5)).containsExactly((byte) 0x81, (byte) 0x05);
        assertThat(HeaderUtil.buildHeader(FrameType.TEXT, 126)).containsExactly((byte) 0x81, (byte) 0x7E, (byte) 0x00, (byte) 0x7E);
        assertThat(HeaderUtil.buildHeader(FrameType.TEXT, 65_536))
                .startsWith((byte) 0x81, (byte) 0x7F)
                .hasSize(10);
    }
}
