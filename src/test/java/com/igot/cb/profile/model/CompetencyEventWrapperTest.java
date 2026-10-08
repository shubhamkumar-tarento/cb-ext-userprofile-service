package com.igot.cb.profile.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompetencyEventWrapper Tests")
class CompetencyEventWrapperTest {

    private CompetencyEventWrapper wrapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wrapper = new CompetencyEventWrapper();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        @Test
        void testNoArgsConstructor() {
            CompetencyEventWrapper localWrapper = new CompetencyEventWrapper();
            assertNotNull(localWrapper);
            assertNull(localWrapper.getEdata());
        }

        @Test
        void testAllArgsConstructor() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .contentId("content123")
                    .build();

            CompetencyEventWrapper localWrapper = new CompetencyEventWrapper(event);
            assertNotNull(localWrapper);
            assertNotNull(localWrapper.getEdata());
            assertEquals("user123", localWrapper.getEdata().getUserId());
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {
        @Test
        void testBuilder() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            assertNotNull(localWrapper);
            assertNotNull(localWrapper.getEdata());
            assertEquals("user123", localWrapper.getEdata().getUserId());
        }

        @Test
        void testBuilderWithNull() {
            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(null)
                    .build();

            assertNotNull(localWrapper);
            assertNull(localWrapper.getEdata());
        }
    }

    @Nested
    @DisplayName("Getter/Setter Tests")
    class GetterSetterTests {
        @Test
        void testGetterSetter() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .build();

            wrapper.setEdata(event);
            assertNotNull(wrapper.getEdata());
            assertEquals("user123", wrapper.getEdata().getUserId());
        }

        @Test
        void testSetNull() {
            wrapper.setEdata(null);
            assertNull(wrapper.getEdata());
        }
    }

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JSONSerializationTests {
        @Test
        void testSerialization_withCompleteEvent() throws Exception {
            List<Map<String, String>> competencies = new ArrayList<>();
            Map<String, String> competency = new HashMap<>();
            competency.put("competencyAreaId", "area1");
            competency.put("competencyThemeId", "theme1");
            competency.put("competencySubThemeId", "subtheme1");
            competencies.add(competency);

            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .contentId("content123")
                    .batchId("batch123")
                    .contextType("achievements")
                    .action("CREATE")
                    .competencyIds(competencies)
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            String json = objectMapper.writeValueAsString(localWrapper);

            assertTrue(json.contains("\"edata\""));
            assertTrue(json.contains("\"eventType\":\"COMPETENCY_ACQUIRED\""));
            assertTrue(json.contains("\"userId\":\"user123\""));
            assertTrue(json.contains("\"contentId\":\"content123\""));
        }

        @Test
        void testDeserialization_withCompleteEvent() throws Exception {
            String json = "{\"edata\":{\"eventType\":\"COMPETENCY_ACQUIRED\",\"userId\":\"user123\"," +
                    "\"contentId\":\"content123\",\"batchId\":\"batch123\",\"contextType\":\"achievements\"," +
                    "\"action\":\"CREATE\",\"competencyIds\":[{\"competencyAreaId\":\"area1\"," +
                    "\"competencyThemeId\":\"theme1\",\"competencySubThemeId\":\"subtheme1\"}]}}";

            CompetencyEventWrapper localWrapper = objectMapper.readValue(json, CompetencyEventWrapper.class);

            assertNotNull(localWrapper);
            assertNotNull(localWrapper.getEdata());
            assertEquals("COMPETENCY_ACQUIRED", localWrapper.getEdata().getEventType());
            assertEquals("user123", localWrapper.getEdata().getUserId());
            assertEquals("content123", localWrapper.getEdata().getContentId());
            assertEquals("achievements", localWrapper.getEdata().getContextType());
            assertEquals("CREATE", localWrapper.getEdata().getAction());
            assertNotNull(localWrapper.getEdata().getCompetencyIds());
            assertEquals(1, localWrapper.getEdata().getCompetencyIds().size());
        }

        @Test
        void testSerialization_withNullFields() throws Exception {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .contentId("content123")
                    .batchId("")
                    .contextType("achievements")
                    .action(null)
                    .competencyIds(null)
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            String json = objectMapper.writeValueAsString(localWrapper);

            assertTrue(json.contains("\"edata\""));
            assertTrue(json.contains("\"userId\":\"user123\""));
        }

        @Test
        void testDeserialization_withNullEdata() throws Exception {
            String json = "{\"edata\":null}";

            CompetencyEventWrapper localWrapper = objectMapper.readValue(json, CompetencyEventWrapper.class);

            assertNotNull(localWrapper);
            assertNull(localWrapper.getEdata());
        }

        @Test
        void testSerialization_createScenario() throws Exception {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .contentId("achv123")
                    .batchId("")
                    .contextType("achievements")
                    .action(null)
                    .competencyIds(null)
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            String json = objectMapper.writeValueAsString(localWrapper);

            assertTrue(json.contains("\"edata\":{"));
            assertTrue(json.contains("\"eventType\":\"COMPETENCY_ACQUIRED\""));
            assertTrue(json.contains("\"userId\":\"user123\""));
        }

        @Test
        void testSerialization_updateScenario() throws Exception {
            List<Map<String, String>> competencies = new ArrayList<>();
            Map<String, String> comp1 = new HashMap<>();
            comp1.put("competencyAreaId", "area1");
            comp1.put("competencyThemeId", "theme1");
            comp1.put("competencySubThemeId", "subtheme1");
            comp1.put("action", "removed");
            competencies.add(comp1);

            Map<String, String> comp2 = new HashMap<>();
            comp2.put("competencyAreaId", "area2");
            comp2.put("competencyThemeId", "theme2");
            comp2.put("competencySubThemeId", "subtheme2");
            comp2.put("action", "added");
            competencies.add(comp2);

            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .contentId("achv123")
                    .batchId("")
                    .contextType("achievements")
                    .action("UPDATE")
                    .competencyIds(competencies)
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            String json = objectMapper.writeValueAsString(localWrapper);

            assertTrue(json.contains("\"edata\":{"));
            assertTrue(json.contains("\"action\":\"UPDATE\""));
            assertTrue(json.contains("\"competencyIds\":["));
        }

        @Test
        void testSerialization_deleteScenario() throws Exception {
            List<Map<String, String>> competencies = new ArrayList<>();
            Map<String, String> comp = new HashMap<>();
            comp.put("competencyAreaId", "area1");
            comp.put("competencyThemeId", "theme1");
            comp.put("competencySubThemeId", "subtheme1");
            competencies.add(comp);

            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .contentId("achv123")
                    .batchId("")
                    .contextType("achievements")
                    .action("DELETE")
                    .competencyIds(competencies)
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            String json = objectMapper.writeValueAsString(localWrapper);

            assertTrue(json.contains("\"edata\":{"));
            assertTrue(json.contains("\"action\":\"DELETE\""));
            assertTrue(json.contains("\"competencyIds\":["));
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {
        @Test
        void testEquals_sameObject() {
            assertEquals(wrapper, wrapper);
        }

        @Test
        void testEquals_nullObject() {
            assertNotEquals(null, wrapper);
        }

        @Test
        void testEquals_differentClass() {
            assertNotEquals("string", wrapper);
        }

        @Test
        void testEquals_equalWrappers() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .build();

            CompetencyEventWrapper wrapper1 = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            CompetencyEventWrapper wrapper2 = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            assertEquals(wrapper1, wrapper2);
            assertEquals(wrapper1.hashCode(), wrapper2.hashCode());
        }

        @Test
        void testEquals_differentWrappers() {
            CompetencyAcquiredEvent event1 = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .build();

            CompetencyAcquiredEvent event2 = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user456")
                    .build();

            CompetencyEventWrapper wrapper1 = CompetencyEventWrapper.builder()
                    .edata(event1)
                    .build();

            CompetencyEventWrapper wrapper2 = CompetencyEventWrapper.builder()
                    .edata(event2)
                    .build();

            assertNotEquals(wrapper1, wrapper2);
        }

        @Test
        void testEquals_oneEdataNullOtherNot() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .build();

            CompetencyEventWrapper wrapper1 = CompetencyEventWrapper.builder().edata(null).build();
            CompetencyEventWrapper wrapper2 = CompetencyEventWrapper.builder().edata(event).build();

            assertNotEquals(wrapper1, wrapper2);
            assertNotEquals(wrapper2, wrapper1);
        }

        @Test
        void testHashCode_bothEdataNull() {
            CompetencyEventWrapper wrapper1 = CompetencyEventWrapper.builder().edata(null).build();
            CompetencyEventWrapper wrapper2 = CompetencyEventWrapper.builder().edata(null).build();

            assertEquals(wrapper1, wrapper2);
            assertEquals(wrapper1.hashCode(), wrapper2.hashCode());
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {
        @Test
        void testToString_withEvent() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .eventType("COMPETENCY_ACQUIRED")
                    .userId("user123")
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            String toString = localWrapper.toString();
            assertNotNull(toString);
            assertTrue(toString.contains("CompetencyEventWrapper"));
        }

        @Test
        void testToString_withNull() {
            CompetencyEventWrapper localWrapper = new CompetencyEventWrapper();
            String toString = localWrapper.toString();
            assertNotNull(toString);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        @Test
        void testWrapper_withEmptyEvent() {
            CompetencyAcquiredEvent event = new CompetencyAcquiredEvent();
            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            assertNotNull(localWrapper);
            assertNotNull(localWrapper.getEdata());
        }

        @Test
        void testWrapper_withPartialEvent() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            assertNotNull(localWrapper);
            assertNotNull(localWrapper.getEdata());
            assertEquals("user123", localWrapper.getEdata().getUserId());
            assertNull(localWrapper.getEdata().getEventType());
        }

        @Test
        void testWrapper_modifyEvent() {
            CompetencyAcquiredEvent event = CompetencyAcquiredEvent.builder()
                    .userId("user123")
                    .build();

            CompetencyEventWrapper localWrapper = CompetencyEventWrapper.builder()
                    .edata(event)
                    .build();

            // Modify event after wrapping
            localWrapper.getEdata().setUserId("user456");

            assertEquals("user456", localWrapper.getEdata().getUserId());
        }
    }
}

