package com.igot.cb.masterdata.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InstituteTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() {
        Institute institute = new Institute();
        LocalDateTime now = LocalDateTime.now();
        institute.setId(1L);
        institute.setName("IIT Delhi");
        institute.setDescription("Indian Institute of Technology Delhi");
        institute.setStatus(1);
        institute.setAddedOn(now);
        institute.setUpdatedOn(now);

        assertEquals(1L, institute.getId());
        assertEquals("IIT Delhi", institute.getName());
        assertEquals("Indian Institute of Technology Delhi", institute.getDescription());
        assertEquals(1, institute.getStatus());
        assertEquals(now, institute.getAddedOn());
        assertEquals(now, institute.getUpdatedOn());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Institute institute = new Institute(1L, "IIT Bombay", "desc", 1, now, now);
        assertEquals(1L, institute.getId());
        assertEquals("IIT Bombay", institute.getName());
        assertEquals("desc", institute.getDescription());
        assertEquals(1, institute.getStatus());
        assertEquals(now, institute.getAddedOn());
        assertEquals(now, institute.getUpdatedOn());
    }

    @Test
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        Institute institute = Institute.builder()
                .id(2L)
                .name("IIT Madras")
                .description("desc")
                .status(0)
                .addedOn(now)
                .updatedOn(now)
                .build();

        assertEquals(2L, institute.getId());
        assertEquals("IIT Madras", institute.getName());
        assertEquals("desc", institute.getDescription());
        assertEquals(0, institute.getStatus());
        assertEquals(now, institute.getAddedOn());
        assertEquals(now, institute.getUpdatedOn());
    }

    @Test
    void testEqualsHashCodeToString() {
        LocalDateTime now = LocalDateTime.now();
        Institute institute1 = new Institute(1L, "IIT Delhi", "desc", 1, now, now);
        Institute institute2 = new Institute(1L, "IIT Delhi", "desc", 1, now, now);

        assertEquals(institute1, institute2);
        assertEquals(institute1.hashCode(), institute2.hashCode());
        assertNotNull(institute1.toString());
        assertNotEquals(institute1, null);
        assertNotEquals(institute1, new Object());
    }

    @Test
    void testOnCreateSetsAddedOn() throws Exception {
        Institute institute = new Institute();
        Method onCreate = Institute.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(institute);
        assertNotNull(institute.getAddedOn());
    }

    @Test
    void testOnUpdateSetsUpdatedOn() throws Exception {
        Institute institute = new Institute();
        Method onUpdate = Institute.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);
        onUpdate.invoke(institute);
        assertNotNull(institute.getUpdatedOn());
    }
}
