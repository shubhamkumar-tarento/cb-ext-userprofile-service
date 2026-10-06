package com.igot.cb.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.nio.ByteBuffer;

public class CharacterEncoderTest {
    
    private static class TestEncoder extends CharacterEncoder {
        @Override
        protected int bytesPerAtom() {
            return 2;
        }

        @Override
        protected int bytesPerLine() {
            return 6;
        }

        @Override
        protected void encodeAtom(OutputStream aStream, byte[] someBytes, int anOffset, int aLength) 
            throws IOException {
            for (int i = anOffset; i < anOffset + aLength; i++) {
                String hex = String.format("%02X", someBytes[i]);
                aStream.write(hex.getBytes());
            }
        }
    }

    private final TestEncoder encoder = new TestEncoder();

    @Test
    public void testEncodeBasic() {
        byte[] input = "Hello".getBytes();
        String result = encoder.encode(input);
        assertNotNull(result);
    }

    @Test
    public void testEncodeWithStream() throws IOException {
        byte[] input = "Test".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encode(input, outStream);
        assertNotNull(outStream.toByteArray());
    }

    @Test
    public void testEncodeBuffer() {
        byte[] input = "Buffer".getBytes();
        String result = encoder.encodeBuffer(input);
        assertNotNull(result);
    }

    @Test
    public void testEncodeByteBuffer() {
        ByteBuffer buffer = ByteBuffer.wrap("ByteBuffer".getBytes());
        String result = encoder.encode(buffer);
        assertNotNull(result);
    }

    @Test
    public void testEncodeByteBufferToStream() throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap("Stream".getBytes());
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encode(buffer, outStream);
        assertNotNull(outStream.toByteArray());
    }

    @Test
    public void testEncodeBufferByteBuffer() {
        ByteBuffer buffer = ByteBuffer.wrap("BufferTest".getBytes());
        String result = encoder.encodeBuffer(buffer);
        assertNotNull(result);
    }

    @Test
    public void testEncodeEmptyInput() {
        byte[] input = new byte[0];
        String result = encoder.encode(input);
        assertNotNull(result);
    }

    @Test
    public void testEncodeWithDirectByteBuffer() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(10);
        buffer.put("Direct".getBytes());
        buffer.flip();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encode(buffer, outStream);
        assertNotNull(outStream.toByteArray());
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeWithNullStream() throws IOException {
        byte[] input = "Test".getBytes();
        encoder.encode(input, null);
    }

    @Test
    public void testEncodePartialLine() throws IOException {
        byte[] input = "Small".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encode(new ByteArrayInputStream(input), outStream);
        assertNotNull(outStream.toByteArray());
    }
    @Test
    public void testEncodeBufferWithByteBuffer() throws IOException {
        String testData = "TestData";
        ByteBuffer buffer = ByteBuffer.wrap(testData.getBytes());
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encodeBuffer(buffer, outStream);
        String result = outStream.toString();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testEncodeBufferWithDirectByteBuffer() throws IOException {
        String testData = "DirectBuffer";
        ByteBuffer buffer = ByteBuffer.allocateDirect(testData.length());
        buffer.put(testData.getBytes());
        buffer.flip();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encodeBuffer(buffer, outStream);
        String result = outStream.toString();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testEncodeBufferWithPartialByteBuffer() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("Half".getBytes());
        buffer.flip();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encodeBuffer(buffer, outStream);
        String result = outStream.toString();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void testEncodeBufferWithNullByteBuffer() throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        encoder.encodeBuffer((ByteBuffer)null, outStream);
    }

    @Test
    public void testEncodeBufferWithError() {
        TestEncoder errorEncoder = new TestEncoder() {
            @Override
            protected void encodeBufferPrefix(OutputStream aStream) throws IOException {
                throw new IOException("Forced error");
            }
        };

        byte[] input = "Test".getBytes();
        try {
            errorEncoder.encodeBuffer(input);
            fail("Expected Error to be thrown");
        } catch (Error e) {
            assertEquals("CharacterEncoder.encodeBuffer internal error", e.getMessage());
        }
    }

    @Test
    public void testEncodeBufferWithPartialAtom() throws IOException {
        // Create custom encoder with known bytesPerAtom and bytesPerLine
        TestEncoder testEncoder = new TestEncoder() {
            @Override
            protected int bytesPerAtom() {
                return 3; // Set to 3 to force partial atom encoding
            }

            @Override
            protected int bytesPerLine() {
                return 6;
            }

            @Override
            protected void encodeAtom(OutputStream aStream, byte[] someBytes, int anOffset, int aLength)
                    throws IOException {
                // Verify partial atom parameters
                if (anOffset == 3 && aLength == 1) { // Last byte of 4-byte input
                    super.encodeAtom(aStream, someBytes, anOffset, aLength);
                }
            }
        };

        // Create input that will result in a partial atom (4 bytes)
        byte[] input = new byte[] { 'T', 'E', 'S', 'T' };
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        testEncoder.encodeBuffer(new ByteArrayInputStream(input), outStream);

        // Verify output was generated
        String result = outStream.toString();
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testEncodeWithEncodingError() {
        TestEncoder errorEncoder = new TestEncoder() {
            @Override
            protected void encodeBufferPrefix(OutputStream aStream) throws IOException {
                throw new IOException("Forced encoding error");
            }
        };

        byte[] input = "Test".getBytes();
        try {
            errorEncoder.encode(input);
            fail("Expected Error to be thrown");
        } catch (Error e) {
            assertEquals("CharacterEncoder.encode internal error", e.getMessage());
        }
    }

    @Test
    public void testGetBytesWithExactArray() {
        byte[] original = "Test".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(original);
        byte[] result = encoder.encode(buffer).getBytes(); // This internally calls getBytes
        assertNotNull(result);
        assertEquals(original.length * 2, result.length); // Each byte becomes 2 hex chars
        assertEquals(buffer.limit(), buffer.position()); // Verify position was updated
    }

    @Test
    public void testGetBytesWithPartialBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("Test".getBytes());
        buffer.flip(); // Prepare for reading
        assertTrue(buffer.capacity() > buffer.remaining());
        byte[] result = encoder.encode(buffer).getBytes();
        assertNotNull(result);
        assertEquals(buffer.limit(), buffer.position()); // Verify position was updated
        assertEquals(8, result.length); // 4 bytes * 2 chars per byte = 8 bytes
    }

    @Test
    public void testGetBytesWithDirectBuffer() {
        // Create a direct ByteBuffer (no backing array)
        ByteBuffer buffer = ByteBuffer.allocateDirect(4);
        buffer.put("Test".getBytes());
        buffer.flip();

        byte[] result = encoder.encode(buffer).getBytes();

        assertNotNull(result);
        assertEquals(8, result.length);
        assertEquals(buffer.limit(), buffer.position());
    }

    @Test
    public void testByteBufferArrayReuseOptimization() {
        byte[] original = "Test".getBytes();
        ByteBuffer perfectBuffer = ByteBuffer.wrap(original);
        byte[] result1 = encoder.encode(perfectBuffer).getBytes();
        assertEquals(8, result1.length); // Each byte becomes 2 chars in hex
        assertEquals(perfectBuffer.limit(), perfectBuffer.position()); // Position updated
        ByteBuffer partiallyReadBuffer = ByteBuffer.wrap(original);
        partiallyReadBuffer.position(1); // Move position so remaining doesn't match capacity
        byte[] result2 = encoder.encode(partiallyReadBuffer).getBytes();
        assertEquals(6, result2.length); // 3 bytes * 2 chars = 6 bytes
        assertEquals(partiallyReadBuffer.limit(), partiallyReadBuffer.position());
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(4);
        directBuffer.put("Test".getBytes());
        directBuffer.flip();
        byte[] result3 = encoder.encode(directBuffer).getBytes();
        assertEquals(8, result3.length);
        assertEquals(directBuffer.limit(), directBuffer.position());
        ByteBuffer largeBuffer = ByteBuffer.allocate(10);
        largeBuffer.put("HelloWorld".getBytes());
        largeBuffer.position(2);
        largeBuffer.limit(7);
        ByteBuffer slicedBuffer = largeBuffer.slice(); // Creates view of part of the buffer
        byte[] result4 = encoder.encode(slicedBuffer).getBytes();
        assertEquals(10, result4.length); // 5 bytes * 2 chars = 10 bytes
        assertEquals(slicedBuffer.limit(), slicedBuffer.position());
    }
}