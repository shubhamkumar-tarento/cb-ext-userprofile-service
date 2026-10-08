package com.igot.cb.masterdata.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DegreeTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() {
        Degree degree = new Degree();
        LocalDateTime now = LocalDateTime.now();
        degree.setId(1L);
        degree.setName("B.Tech");
        degree.setDescription("Bachelor of Technology");
        degree.setStatus(1);
        degree.setAddedOn(now);
        degree.setUpdatedOn(now);

        assertEquals(1L, degree.getId());
        assertEquals("B.Tech", degree.getName());
        assertEquals("Bachelor of Technology", degree.getDescription());
        assertEquals(1, degree.getStatus());
        assertEquals(now, degree.getAddedOn());
        assertEquals(now, degree.getUpdatedOn());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        Degree degree = new Degree(1L, "M.Tech", "Master of Technology", 1, now, now);
        assertEquals(1L, degree.getId());
        assertEquals("M.Tech", degree.getName());
        assertEquals("Master of Technology", degree.getDescription());
        assertEquals(1, degree.getStatus());
        assertEquals(now, degree.getAddedOn());
        assertEquals(now, degree.getUpdatedOn());
    }

    @Test
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        Degree degree = Degree.builder()
                .id(2L)
                .name("PhD")
                .description("Doctorate")
                .status(0)
                .addedOn(now)
                .updatedOn(now)
                .build();

        assertEquals(2L, degree.getId());
        assertEquals("PhD", degree.getName());
        assertEquals("Doctorate", degree.getDescription());
        assertEquals(0, degree.getStatus());
        assertEquals(now, degree.getAddedOn());
        assertEquals(now, degree.getUpdatedOn());
    }

    @Test
    void testEqualsHashCodeToString() {
        LocalDateTime now = LocalDateTime.now();
        Degree degree1 = new Degree(1L, "B.Tech", "desc", 1, now, now);
        Degree degree2 = new Degree(1L, "B.Tech", "desc", 1, now, now);

        assertEquals(degree1, degree2);
        assertEquals(degree1.hashCode(), degree2.hashCode());
        assertNotNull(degree1.toString());
        assertNotEquals(degree1, null);
        assertNotEquals(degree1, new Object());
    }

    @Test
    void testOnCreateSetsAddedOn() throws Exception {
        Degree degree = new Degree();
        Method onCreate = Degree.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        onCreate.invoke(degree);
        assertNotNull(degree.getAddedOn());
    }

    @Test
    void testOnUpdateSetsUpdatedOn() throws Exception {
        Degree degree = new Degree();
        Method onUpdate = Degree.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);
        onUpdate.invoke(degree);
        assertNotNull(degree.getUpdatedOn());
    }
}
