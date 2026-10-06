package com.igot.cb.util;

import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.ByteBuffer;

public class CharacterDecoderTest {

    private static class TestDecoder extends CharacterDecoder {
        private boolean lineSuffixCalled = false;
        private int atomCalls = 0;

        @Override
        protected int bytesPerAtom() {
            return 2;
        }

        @Override
        protected int bytesPerLine() {
            return 6;
        }

        @Override
        protected void decodeBufferPrefix(PushbackInputStream aStream, OutputStream bStream)
                throws IOException {
        }

        @Override
        protected void decodeBufferSuffix(PushbackInputStream aStream, OutputStream bStream)
                throws IOException {
        }

        @Override
        protected int decodeLinePrefix(PushbackInputStream aStream, OutputStream bStream)
                throws IOException {
            return super.decodeLinePrefix(aStream, bStream);
        }

        @Override
        protected void decodeLineSuffix(PushbackInputStream aStream, OutputStream bStream)
                throws IOException {
            lineSuffixCalled = true;
        }

        @Override
        protected void decodeAtom(PushbackInputStream aStream, OutputStream bStream, int l)
                throws IOException {
            atomCalls++;
            if (l < 0 || l > 1024) {
                throw new IOException("Invalid length parameter: " + l);
            }
            for (int i = 0; i < l; i++) {
                bStream.write(65 + i); // Write 'A', 'B', etc.
            }
        }

        public void reset() {
            lineSuffixCalled = false;
            atomCalls = 0;
        }


    }

    private TestDecoder decoder;

    @Before
    public void setUp() {
        decoder = new TestDecoder();
    }

    @Test
    public void testBytesPerAtom() {
        assertEquals("bytesPerAtom should return correct value", 2, decoder.bytesPerAtom());
    }

    @Test
    public void testBytesPerLine() {
        assertEquals("bytesPerLine should return correct value", 6, decoder.bytesPerLine());
    }

    @Test
    public void testDecodeBuffer_InputStream_OutputStream() throws IOException {
        byte[] inputData = new byte[]{1, 2, 3, 4, 5};
        ByteArrayInputStream inputStream = new ByteArrayInputStream(inputData);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final boolean[] methodsCalled = {false, false, false}; // prefixCalled, suffixCalled, linePrefixCalled
        TestDecoder modifiedDecoder = new TestDecoder() {
            private int callCount = 0;

            @Override
            protected void decodeBufferPrefix(PushbackInputStream aStream, OutputStream bStream)
                    throws IOException {
                super.decodeBufferPrefix(aStream, bStream);
                methodsCalled[0] = true;
            }

            @Override
            protected void decodeBufferSuffix(PushbackInputStream aStream, OutputStream bStream)
                    throws IOException {
                super.decodeBufferSuffix(aStream, bStream);
                methodsCalled[1] = true;
            }

            @Override
            protected int decodeLinePrefix(PushbackInputStream aStream, OutputStream bStream)
                    throws IOException {
                methodsCalled[2] = true;
                callCount++;
                if (callCount > 2) {
                    throw new IOException("End of test input");
                }
                return 4; // Return a small, fixed number to avoid memory issues
            }
        };
        modifiedDecoder.decodeBuffer(inputStream, outputStream);
        assertTrue("Buffer prefix should be called", methodsCalled[0]);
        assertTrue("Buffer suffix should be called", methodsCalled[1]);
        assertTrue("Line prefix should be called", methodsCalled[2]);
    }

    @Test
    public void testDecodeBuffer_String() throws IOException {
        String testInput = "TestInput";
        TestDecoder customDecoder = new TestDecoder() {
            private int callCount = 0;

            @Override
            protected int decodeLinePrefix(PushbackInputStream aStream, OutputStream bStream)
                    throws IOException {
                super.decodeLinePrefix(aStream, bStream);
                callCount++;
                if (callCount > 2) {
                    throw new IOException("End of test input");
                }
                return 4; // Small, fixed value
            }
        };
        byte[] result = customDecoder.decodeBuffer(testInput);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain data", result.length > 0);
    }

    @Test
    public void testDecodeBufferToByteBuffer_String() throws IOException {
        String testInput = "TestInput";
        TestDecoder customDecoder = new TestDecoder() {
            private int callCount = 0;

            @Override
            protected int decodeLinePrefix(PushbackInputStream aStream, OutputStream bStream)
                    throws IOException {
                super.decodeLinePrefix(aStream, bStream);
                callCount++;
                if (callCount > 2) {
                    throw new IOException("End of test input");
                }
                return 4; // Small, fixed value
            }
        };
        ByteBuffer result = customDecoder.decodeBufferToByteBuffer(testInput);
        assertNotNull("Result should not be null", result);
        assertTrue("ByteBuffer should contain data", result.hasRemaining());
    }

    @Test
    public void testDecodeBufferToByteBuffer_InputStream() throws IOException {
        byte[] inputData = new byte[]{1, 2, 3, 4, 5}; // Small input data
        ByteArrayInputStream inputStream = new ByteArrayInputStream(inputData);
        TestDecoder customDecoder = new TestDecoder() {
            private int callCount = 0;

            @Override
            protected int decodeLinePrefix(PushbackInputStream aStream, OutputStream bStream)
                    throws IOException {
                callCount++;
                if (callCount > 2) {
                    throw new IOException("End of test input");
                }
                return 4; // Small, fixed value to avoid memory issues
            }
        };
        ByteBuffer result = customDecoder.decodeBufferToByteBuffer(inputStream);
        assertNotNull("Result should not be null", result);
    }

    @Test
    public void testReadFully() throws IOException {
        byte[] buffer = new byte[5];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5});
        int read = decoder.readFully(inputStream, buffer, 0, 5);
        assertEquals("Should read requested number of bytes", 5, read);
        assertEquals("First byte should be 1", 1, buffer[0]);
        assertEquals("Last byte should be 5", 5, buffer[4]);
    }

    @Test
    public void testReadFully_PartialRead() throws IOException {
        byte[] buffer = new byte[5];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[]{1, 2});
        int read = decoder.readFully(inputStream, buffer, 0, 5);
        assertEquals("Should read available bytes", 2, read);
        assertEquals("First byte should be 1", 1, buffer[0]);
        assertEquals("Second byte should be 2", 2, buffer[1]);
    }

    @Test
    public void testDecodeLineSuffix() throws IOException {
        PushbackInputStream inputStream = new PushbackInputStream(new ByteArrayInputStream(new byte[0]));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        decoder.decodeLineSuffix(inputStream, outputStream);
        assertTrue("decodeLineSuffix should be called", decoder.lineSuffixCalled);
    }
}