package com.igot.cb.exceptions;

import com.igot.cb.util.Constants;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProjectCommonExceptionTest {

    @Test
    public void testConstructorWithResponseCode() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_001");
        
        ProjectCommonException exception = new ProjectCommonException(mockCode, "Test message", 400);
        
        assertEquals("ERR_001", exception.getErrorCode());
        assertEquals("Test message", exception.getErrorMessage());
        assertEquals(400, exception.getErrorResponseCode());
        assertEquals(mockCode, exception.getResponseCode());
    }

    @Test
    public void testConstructorWithPlaceholders() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_002");
        
        ProjectCommonException exception = new ProjectCommonException(
            mockCode, 
            "Error occurred for user {0} in operation {1}", 
            500, 
            "testUser", 
            "testOperation"
        );
        
        assertEquals("ERR_002", exception.getErrorCode());
        assertEquals("Error occurred for user testUser in operation testOperation", exception.getErrorMessage());
        assertEquals(500, exception.getErrorResponseCode());
    }

    @Test
    public void testCopyConstructor() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_003");
        
        ProjectCommonException original = new ProjectCommonException(mockCode, "Original message", 400);
        ProjectCommonException copy = new ProjectCommonException(original, "NewOperation");
        
        assertEquals(Constants.USER_ORG_SERVICE_PREFIX + "NewOperation" + "ERR_003", copy.getErrorCode());
        assertEquals("Original message", copy.getErrorMessage());
        assertEquals(400, copy.getErrorResponseCode());
    }

    @Test
    public void testGetters() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("NEW_ERR");

        ProjectCommonException exception = new ProjectCommonException(mockCode, "New message", 500);

        assertEquals("NEW_ERR", exception.getErrorCode());
        assertEquals("New message", exception.getErrorMessage());
        assertEquals(500, exception.getErrorResponseCode());
    }

    @Test
    public void testToString() {
        ResponseCode mockCode = mock(ResponseCode.class);
        when(mockCode.getErrorCode()).thenReturn("ERR_004");
        
        ProjectCommonException exception = new ProjectCommonException(mockCode, "Test message", 400);
        assertEquals("ERR_004: Test message", exception.toString());
    }


    @Test
    public void testThrowServerErrorException() {
        // Case 1: Custom error message provided
        ResponseCode mockResponseCode1 = mock(ResponseCode.class);
        when(mockResponseCode1.getErrorCode()).thenReturn("SERVER_ERR_001");
        when(mockResponseCode1.getErrorMessage()).thenReturn("Default error message");

        ProjectCommonException exception1 = assertThrows(
                ProjectCommonException.class,
                () -> ProjectCommonException.throwServerErrorException(mockResponseCode1, "Custom error message")
        );
        assertNotNull(exception1.getErrorCode());
        assertEquals("Custom error message", exception1.getErrorMessage());
        assertNotEquals(0, exception1.getErrorResponseCode());

        // Case 2: Empty custom message, should use default message
        ResponseCode mockResponseCode2 = mock(ResponseCode.class);
        when(mockResponseCode2.getErrorCode()).thenReturn("SERVER_ERR_001");
        when(mockResponseCode2.getErrorMessage()).thenReturn("Default error message");

        ProjectCommonException exception2 = assertThrows(
                ProjectCommonException.class,
                () -> ProjectCommonException.throwServerErrorException(mockResponseCode2, "")
        );
        assertNotNull(exception2.getErrorCode());
        assertEquals("Default error message", exception2.getErrorMessage());
        assertNotEquals(0, exception2.getErrorResponseCode());

        // Case 3: Null custom message, should use default message
        ResponseCode mockResponseCode3 = mock(ResponseCode.class);
        when(mockResponseCode3.getErrorCode()).thenReturn("SERVER_ERR_001");
        when(mockResponseCode3.getErrorMessage()).thenReturn("Default error message");

        ProjectCommonException exception3 = assertThrows(
                ProjectCommonException.class,
                () -> ProjectCommonException.throwServerErrorException(mockResponseCode3, null)
        );
        assertNotNull(exception3.getErrorCode());
        assertEquals("Default error message", exception3.getErrorMessage());
        assertNotEquals(0, exception3.getErrorResponseCode());
    }


    @Test
    public void testThrowServerErrorExceptionWithSingleParameter() {
        ResponseCode mockResponseCode = mock(ResponseCode.class);
        when(mockResponseCode.getErrorCode()).thenReturn("SERVER_ERR_001");
        when(mockResponseCode.getErrorMessage()).thenReturn("Default error message");

        ProjectCommonException exception = assertThrows(
                ProjectCommonException.class,
                () -> ProjectCommonException.throwServerErrorException(mockResponseCode)
        );

        assertNotNull(exception.getErrorCode());
        assertEquals("Default error message", exception.getErrorMessage());
        assertNotEquals(0, exception.getErrorResponseCode());
    }

    @Test
    public void testMessageFromConstructor() {
        ProjectCommonException exception = new ProjectCommonException(
                ResponseCode.SERVER_ERROR,
                "Initial message",
                ResponseCode.SERVER_ERROR.getStatusCode()
        );
        assertEquals("Initial message", exception.getMessage());

        ProjectCommonException withNullMessage = new ProjectCommonException(
                ResponseCode.SERVER_ERROR,
                null,
                ResponseCode.SERVER_ERROR.getStatusCode()
        );
        assertNull(withNullMessage.getMessage());

        ProjectCommonException withEmptyMessage = new ProjectCommonException(
                ResponseCode.SERVER_ERROR,
                "",
                ResponseCode.SERVER_ERROR.getStatusCode()
        );
        assertEquals("", withEmptyMessage.getMessage());
    }

    @Test
    public void testResponseCodeFromConstructor() {
        ResponseCode mockResponseCode = mock(ResponseCode.class);
        when(mockResponseCode.getErrorCode()).thenReturn("NEW_ERR_001");
        ProjectCommonException exception = new ProjectCommonException(
                mockResponseCode,
                "Test message",
                400
        );
        assertEquals(mockResponseCode, exception.getResponseCode());
    }
}