package com.diosesmon.organizerpc;

import com.diosesmon.organizerpc.core.WikiPairs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiPairsTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID R = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void parsesPairsAndReserved() {
        WikiPairs w = WikiPairs.parse("{\"v\":1,\"crianzas\":[{\"id\":\"a\",\"nombre\":\"Lucario\",\"color\":\"#3DD6E0\","
                + "\"pares\":[{\"n\":2,\"a\":\"" + A + "\",\"b\":\"" + B + "\",\"paso\":\"PS + VEL\",\"cambio\":[\"" + B + "\"]}],"
                + "\"reservados\":[{\"u\":\"" + R + "\",\"paso\":\"ATQ\"}]}]}");
        assertEquals("L2", w.markOf(A).label());
        assertTrue(w.markOf(B).female());
        assertTrue(w.markOf(B).genderChange());
        assertEquals("L·", w.markOf(R).label());
        assertEquals(0xFF3DD6E0, w.markOf(A).color());
        assertEquals(List.of(A, B, R), w.orderedMembers());
        assertNull(w.markOf(UUID.randomUUID()));
    }

    @Test
    void emptyAndBrokenIdsAreIgnored() {
        assertTrue(WikiPairs.parse("").isEmpty());
        WikiPairs w = WikiPairs.parse("{\"crianzas\":[{\"pares\":[{\"a\":\"no-es-uuid\",\"b\":\"\"}]}]}");
        assertTrue(w.isEmpty());
    }
}
