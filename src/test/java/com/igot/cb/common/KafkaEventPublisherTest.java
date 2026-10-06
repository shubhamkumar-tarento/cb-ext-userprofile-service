package com.igot.cb.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventPublisher Tests")
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaEventPublisher kafkaEventPublisher;

    private TestEvent testEvent;
    private static final String TEST_TOPIC = "test-topic";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_IDENTIFIER = "test-identifier";

    @BeforeEach
    void setUp() {
        testEvent = new TestEvent("event-123", "Test Event Data");
    }

    // ==================== publish(topic, key, event) Tests ====================

    @Nested
    @DisplayName("publish(topic, key, event) Method Tests")
    class PublishWithKeyTests {

        @Test
        @DisplayName("Should successfully publish event with topic and key")
        void testPublishWithKeySuccess() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, testEvent);
        }

        @Test
        @DisplayName("Should publish event with correct arguments")
        void testPublishWithKeyArgumentVerification() {
            // Arrange
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, testEvent);

            // Assert
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertEquals(TEST_TOPIC, topicCaptor.getValue());
            assertEquals(TEST_KEY, keyCaptor.getValue());
            assertEquals(testEvent, eventCaptor.getValue());
        }

        @Test
        @DisplayName("Should handle exception gracefully without throwing")
        void testPublishWithKeyExceptionHandling() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka connection failed"));

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, testEvent));
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, testEvent);
        }

        @Test
        @DisplayName("Should publish with null key")
        void testPublishWithNullKey() {
            // Arrange
            when(kafkaTemplate.send(anyString(), isNull(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, null, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, null, testEvent);
        }

        @Test
        @DisplayName("Should publish with empty string key")
        void testPublishWithEmptyKey() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, "", testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, "", testEvent);
        }

        @Test
        @DisplayName("Should publish with null event")
        void testPublishWithNullEvent() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), isNull())).thenReturn(null);

            // Act
            Object nullEvent = null;
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, nullEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, null);
        }

        @Test
        @DisplayName("Should publish different event types")
        void testPublishDifferentEventTypes() {
            // Arrange
            String stringEvent = "String event";
            Integer intEvent = 123;

            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act - Cast to Object to avoid ambiguity
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, (Object) stringEvent);
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, (Object) intEvent);
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, stringEvent);
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, intEvent);
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, testEvent);
        }

        @Test
        @DisplayName("Should publish multiple events to same topic")
        void testPublishMultipleEvents() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            TestEvent event1 = new TestEvent("id1", "data1");
            TestEvent event2 = new TestEvent("id2", "data2");
            TestEvent event3 = new TestEvent("id3", "data3");

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, "key1", event1);
            kafkaEventPublisher.publish(TEST_TOPIC, "key2", event2);
            kafkaEventPublisher.publish(TEST_TOPIC, "key3", event3);

            // Assert
            verify(kafkaTemplate, times(3)).send(eq(TEST_TOPIC), anyString(), any());
        }
    }

    // ==================== publish(topic, event, identifier) Tests ====================

    @Nested
    @DisplayName("publish(topic, event, identifier) Method Tests")
    class PublishWithIdentifierTests {

        @Test
        @DisplayName("Should successfully publish event with topic and identifier")
        void testPublishWithIdentifierSuccess() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, TEST_IDENTIFIER);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should publish event with correct arguments")
        void testPublishWithIdentifierArgumentVerification() {
            // Arrange
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, TEST_IDENTIFIER);

            // Assert
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            assertEquals(TEST_TOPIC, topicCaptor.getValue());
            assertEquals(testEvent, eventCaptor.getValue());
        }

        @Test
        @DisplayName("Should handle exception gracefully without throwing")
        void testPublishWithIdentifierExceptionHandling() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka broker unreachable"));

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> kafkaEventPublisher.publish(TEST_TOPIC, testEvent, TEST_IDENTIFIER));
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should publish with null identifier")
        void testPublishWithNullIdentifier() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, null);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should publish with empty identifier")
        void testPublishWithEmptyIdentifier() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, "");

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should publish with null event")
        void testPublishWithIdentifierNullEvent() {
            // Arrange
            when(kafkaTemplate.send(anyString(), isNull())).thenReturn(null);

            // Act - Explicitly specify null as Object
            Object nullEvent = null;
            kafkaEventPublisher.publish(TEST_TOPIC, nullEvent, TEST_IDENTIFIER);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, null);
        }

        @Test
        @DisplayName("Should publish different event types with identifier")
        void testPublishDifferentEventTypesWithIdentifier() {
            // Arrange
            String stringEvent = "String event";
            Integer intEvent = 456;

            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act - Cast to Object to avoid ambiguity
            kafkaEventPublisher.publish(TEST_TOPIC, (Object) stringEvent, "string-id");
            kafkaEventPublisher.publish(TEST_TOPIC, (Object) intEvent, "int-id");
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, TEST_IDENTIFIER);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, stringEvent);
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, intEvent);
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }
    }

    // ==================== publish(topic, event) Tests ====================

    @Nested
    @DisplayName("publish(topic, event) Method Tests")
    class PublishMinimalTests {

        @Test
        @DisplayName("Should successfully publish event with minimal logging")
        void testPublishMinimalSuccess() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should publish event with correct arguments")
        void testPublishMinimalArgumentVerification() {
            // Arrange
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);

            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent);

            // Assert
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());
            assertEquals(TEST_TOPIC, topicCaptor.getValue());
            assertEquals(testEvent, eventCaptor.getValue());
        }

        @Test
        @DisplayName("Should handle exception gracefully without throwing")
        void testPublishMinimalExceptionHandling() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any()))
                    .thenThrow(new RuntimeException("Network error"));

            // Act & Assert - should not throw exception
            assertDoesNotThrow(() -> kafkaEventPublisher.publish(TEST_TOPIC, testEvent));
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should publish with null event")
        void testPublishMinimalNullEvent() {
            // Arrange
            when(kafkaTemplate.send(anyString(), isNull())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, null);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, null);
        }

        @Test
        @DisplayName("Should publish different event types")
        void testPublishMinimalDifferentEventTypes() {
            // Arrange
            String stringEvent = "Minimal event";
            Integer intEvent = 789;

            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, stringEvent);
            kafkaEventPublisher.publish(TEST_TOPIC, intEvent);
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, stringEvent);
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, intEvent);
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should handle high-frequency publishing")
        void testPublishMinimalHighFrequency() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act - Simulate high-frequency publishing
            for (int i = 0; i < 100; i++) {
                kafkaEventPublisher.publish(TEST_TOPIC, new TestEvent("id" + i, "data" + i));
            }

            // Assert
            verify(kafkaTemplate, times(100)).send(eq(TEST_TOPIC), any(TestEvent.class));
        }
    }

    // ==================== Edge Cases and Integration Tests ====================

    @Nested
    @DisplayName("Edge Cases and Integration Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty topic name")
        void testPublishWithEmptyTopic() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish("", TEST_KEY, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send("", TEST_KEY, testEvent);
        }

        @Test
        @DisplayName("Should handle very long topic name")
        void testPublishWithLongTopicName() {
            // Arrange
            String longTopic = "a".repeat(1000);
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(longTopic, TEST_KEY, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(longTopic, TEST_KEY, testEvent);
        }

        @Test
        @DisplayName("Should handle special characters in topic name")
        void testPublishWithSpecialCharactersInTopic() {
            // Arrange
            String specialTopic = "test-topic_123.abc";
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(specialTopic, TEST_KEY, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(specialTopic, TEST_KEY, testEvent);
        }

        @Test
        @DisplayName("Should handle special characters in key")
        void testPublishWithSpecialCharactersInKey() {
            // Arrange
            String specialKey = "user@example.com-123_456";
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, specialKey, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, specialKey, testEvent);
        }

        @Test
        @DisplayName("Should handle Unicode characters in identifier")
        void testPublishWithUnicodeIdentifier() {
            // Arrange
            String unicodeId = "用户123";
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, unicodeId);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should handle consecutive publish calls")
        void testConsecutivePublishCalls() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, testEvent);
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, TEST_IDENTIFIER);
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, testEvent);
            verify(kafkaTemplate, times(2)).send(TEST_TOPIC, testEvent);
        }

        @Test
        @DisplayName("Should handle different exception types")
        void testDifferentExceptionTypes() {
            // Arrange - Different exceptions
            when(kafkaTemplate.send(eq("topic1"), anyString(), any()))
                    .thenThrow(new RuntimeException("Runtime exception"));
            when(kafkaTemplate.send(eq("topic2"), any()))
                    .thenThrow(new IllegalStateException("Illegal state"));
            when(kafkaTemplate.send(eq("topic3"), any()))
                    .thenThrow(new NullPointerException("Null pointer"));

            // Act & Assert - All should be handled gracefully
            assertDoesNotThrow(() -> kafkaEventPublisher.publish("topic1", TEST_KEY, testEvent));
            assertDoesNotThrow(() -> kafkaEventPublisher.publish("topic2", testEvent, TEST_IDENTIFIER));
            assertDoesNotThrow(() -> kafkaEventPublisher.publish("topic3", testEvent));

            verify(kafkaTemplate, times(1)).send("topic1", TEST_KEY, testEvent);
            verify(kafkaTemplate, times(1)).send("topic2", testEvent);
            verify(kafkaTemplate, times(1)).send("topic3", testEvent);
        }

        @Test
        @DisplayName("Should be non-blocking when exception occurs")
        void testNonBlockingBehavior() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Kafka error"));

            // Act
            long startTime = System.currentTimeMillis();
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, testEvent);
            long endTime = System.currentTimeMillis();

            // Assert - Should complete quickly without blocking
            assertTrue(endTime - startTime < 1000, "Should not block for long");
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, testEvent);
        }

        @Test
        @DisplayName("Should handle complex event objects")
        void testPublishComplexEventObject() {
            // Arrange
            ComplexEvent complexEvent = new ComplexEvent(
                    "complex-id",
                    "complex data",
                    new NestedData("nested", 123),
                    java.util.Arrays.asList("item1", "item2", "item3")
            );
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, complexEvent);

            // Assert
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, complexEvent);
        }
    }

    // ==================== Method Overload Distinction Tests ====================

    @Nested
    @DisplayName("Method Overload Distinction Tests")
    class MethodOverloadTests {

        @Test
        @DisplayName("Should call correct overload: publish(topic, key, event)")
        void testCorrectOverloadWithKey() {
            // Arrange
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, TEST_KEY, testEvent);

            // Assert - Verify the 3-argument send method was called
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, TEST_KEY, testEvent);
            verify(kafkaTemplate, never()).send(eq(TEST_TOPIC), any(Object.class));
        }

        @Test
        @DisplayName("Should call correct overload: publish(topic, event, identifier)")
        void testCorrectOverloadWithIdentifier() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent, TEST_IDENTIFIER);

            // Assert - Verify the 2-argument send method was called
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should call correct overload: publish(topic, event)")
        void testCorrectOverloadMinimal() {
            // Arrange
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // Act
            kafkaEventPublisher.publish(TEST_TOPIC, testEvent);

            // Assert - Verify the 2-argument send method was called
            verify(kafkaTemplate, times(1)).send(TEST_TOPIC, testEvent);
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }
    }

    // ==================== Test Helper Classes ====================

    /**
     * Simple test event class
     */
    static class TestEvent {
        private String id;
        private String data;

        public TestEvent(String id, String data) {
            this.id = id;
            this.data = data;
        }

        public String getId() {
            return id;
        }

        public String getData() {
            return data;
        }

        @Override
        public String toString() {
            return "TestEvent{id='" + id + "', data='" + data + "'}";
        }
    }

    /**
     * Complex test event class with nested objects
     */
    static class ComplexEvent {

        public ComplexEvent(String id, String data, NestedData nested, java.util.List<String> items) {
            // Fields intentionally not stored: this test event only needs to be
            // constructed and passed through KafkaEventPublisher.publish() by reference.
        }
    }

    /**
     * Nested data class for complex events
     */
    static class NestedData {

        public NestedData(String name, int value) {
            // Fields intentionally not stored: this test event only needs to be
            // constructed and passed through KafkaEventPublisher.publish() by reference.
        }
    }
}
