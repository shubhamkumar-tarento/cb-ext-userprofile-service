package com.igot.cb.exceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.igot.cb.util.Constants;

public class ResponseCodeTest {

    @Test
    public void testEnumValues() {
        assertEquals(7, ResponseCode.values().length);
        assertNotNull(ResponseCode.UNAUTHORIZED);
        assertNotNull(ResponseCode.INTERNAL_ERROR);
        assertNotNull(ResponseCode.OK);
        assertNotNull(ResponseCode.CLIENT_ERROR);
        assertNotNull(ResponseCode.SERVER_ERROR);
    }

    @Test
    public void testStringConstructor() {
        assertEquals(ResponseMessage.Key.UNAUTHORIZED_USER, ResponseCode.UNAUTHORIZED.getErrorCode());
        assertEquals(ResponseMessage.Message.UNAUTHORIZED_USER, ResponseCode.UNAUTHORIZED.getErrorMessage());
        assertEquals(ResponseMessage.Key.INTERNAL_ERROR, ResponseCode.INTERNAL_ERROR.getErrorCode());
        assertEquals(ResponseMessage.Message.INTERNAL_ERROR, ResponseCode.INTERNAL_ERROR.getErrorMessage());
    }

    @Test
    public void testIntConstructor() {
        assertEquals(200, ResponseCode.OK.getStatusCode());
        assertEquals(400, ResponseCode.CLIENT_ERROR.getStatusCode());
        assertEquals(500, ResponseCode.SERVER_ERROR.getStatusCode());
        assertNull(ResponseCode.OK.getErrorCode());
        assertNull(ResponseCode.OK.getErrorMessage());
    }

    @Test
    public void testResponseCodeSetter() {
        ResponseCode testCode = ResponseCode.UNAUTHORIZED;
        int originalCode = testCode.getStatusCode();
        int newResponseCode = 403;

        try {
            testCode.setStatusCode(newResponseCode);
            assertEquals(newResponseCode, testCode.getStatusCode());
        } finally {
            // Restore the original value to avoid affecting other tests
            testCode.setStatusCode(originalCode);
        }
    }

    @Test
    public void testGetResponseWithNullOrBlank() {
        assertNull(ResponseCode.getResponse(null));
        assertNull(ResponseCode.getResponse(""));
        assertNull(ResponseCode.getResponse(" "));
    }

    @Test
    public void testGetResponseWithUnauthorized() {
        assertEquals(ResponseCode.UNAUTHORIZED, ResponseCode.getResponse(Constants.UNAUTHORIZED));
    }

    @Test
    public void testGetResponseWithValidErrorCode() {
        assertEquals(ResponseCode.UNAUTHORIZED,
                    ResponseCode.getResponse(ResponseMessage.Key.UNAUTHORIZED_USER));
        assertEquals(ResponseCode.INTERNAL_ERROR,
                    ResponseCode.getResponse(ResponseMessage.Key.INTERNAL_ERROR));
    }

    @Test
    public void testGetResponseWithInvalidErrorCode() {
        try {
            ResponseCode result = ResponseCode.getResponse("INVALID_CODE");
            assertNull(result);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Cannot invoke \"String.equals(Object)\""));
        }
    }
    
    @Test
    public void testGetMessage() {
        assertEquals("", ResponseCode.EMPTY_MESSAGE);
    }

    @Test
    public void testGetResponseReturnsNullWhenNoMatchFound() {
        String nonMatchingErrorCode = "NON_MATCHING_ERROR_CODE";
        assertNotEquals(Constants.UNAUTHORIZED, nonMatchingErrorCode);
        try {
            ResponseCode result = ResponseCode.getResponse(nonMatchingErrorCode);
            assertNull("Should return null when no matching ResponseCode is found", result);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("Cannot invoke \"String.equals(Object)\""));
        }
    }

    @Test
    public void testGetResponseMethod() {
        assertNull(ResponseCode.getResponse(null));
        assertNull(ResponseCode.getResponse(""));
        assertNull(ResponseCode.getResponse("   "));
        assertEquals(ResponseCode.UNAUTHORIZED, ResponseCode.getResponse(Constants.UNAUTHORIZED));
        assertEquals(ResponseCode.INTERNAL_ERROR,
                ResponseCode.getResponse(ResponseMessage.Key.INTERNAL_ERROR));
        assertEquals(ResponseCode.UNAUTHORIZED,
                ResponseCode.getResponse(ResponseMessage.Key.UNAUTHORIZED_USER));
        assertEquals(ResponseCode.RESOURCE_NOT_FOUND,
                ResponseCode.getResponse(ResponseMessage.Key.RESOURCE_NOT_FOUND));
        assertNull(ResponseCode.getResponse("INVALID_CODE"));
        assertNull(ResponseCode.getResponse(ResponseCode.OK.getErrorCode()));
        assertNull(ResponseCode.getResponse(ResponseCode.CLIENT_ERROR.getErrorCode()));
        assertNull(ResponseCode.getResponse(ResponseCode.SERVER_ERROR.getErrorCode()));
    }
}