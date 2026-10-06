package com.igot.cb.util;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import org.junit.Before;
import org.junit.Test;

public class BASE64DecoderTest {

    private BASE64Decoder decoder;

    @Before
    public void setup() {
        decoder = new BASE64Decoder();
    }

    @Test
    public void testBytesPerAtom() {
        assertEquals("Bytes per atom should be 4", 4, decoder.bytesPerAtom());
    }

    @Test
    public void testBytesPerLine() {
        assertEquals("Bytes per line should be 72", 72, decoder.bytesPerLine());
    }

    @Test
    public void testDecodeAtomWithTwoBytes() throws IOException {
        String input = "QQ==";
        byte[] expected = "A".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
            new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 2);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomWithThreeBytes() throws IOException {
        String input = "QUI=";
        byte[] expected = "AB".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
            new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 3);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomWithFourBytes() throws IOException {
        String input = "QUJD";
        byte[] expected = "ABC".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
            new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test(expected = IOException.class)
    public void testDecodeAtomWithInsufficientBytes() throws IOException {
        String input = "Q";
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
            new ByteArrayInputStream(input.getBytes())
        );

        decoder.decodeAtom(inStream, outStream, 1);
    }

    @Test
    public void testDecodeAtomWithNewlines() throws IOException {
        // Leading '\n' characters before the first atom character must be skipped by decodeAtom.
        String input = "\n\nQUJD";
        byte[] expected = "ABC".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomWithCarriageReturns() throws IOException {
        // Leading '\r' characters before the first atom character must be skipped by decodeAtom.
        String input = "\r\rQUJD";
        byte[] expected = "ABC".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test(expected = IOException.class)
    public void testDecodeAtomWithInvalidInput() throws IOException {
        String input = "";
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
    }

    @Test
    public void testDecodeAtomWithMixedWhitespace() throws IOException {
        String input1 = "QUI=";
        byte[] expected1 = "AB".getBytes();
        ByteArrayOutputStream outStream1 = new ByteArrayOutputStream();
        PushbackInputStream inStream1 = new PushbackInputStream(
                new ByteArrayInputStream(input1.getBytes()),
                10
        );
        decoder.decodeAtom(inStream1, outStream1, 3);
        assertArrayEquals(expected1, outStream1.toByteArray());
        String input2 = "QUI=";
        byte[] expected2 = "AB".getBytes();
        ByteArrayOutputStream outStream2 = new ByteArrayOutputStream();
        PushbackInputStream inStream2 = new PushbackInputStream(
                new ByteArrayInputStream(input2.getBytes()),
                10
        );
        decoder.decodeAtom(inStream2, outStream2, 3);
        assertArrayEquals(expected2, outStream2.toByteArray());
        String input3 = "QUI=";
        byte[] expected3 = "AB".getBytes();
        ByteArrayOutputStream outStream3 = new ByteArrayOutputStream();
        PushbackInputStream inStream3 = new PushbackInputStream(
                new ByteArrayInputStream(input3.getBytes()),
                10
        );
        decoder.decodeAtom(inStream3, outStream3, 3);
        assertArrayEquals(expected3, outStream3.toByteArray());
    }

    @Test
    public void testDecodeAtomWithConsecutiveWhitespace() throws IOException {
        String input = "QUJD\n\n\r";
        byte[] expected = "ABC".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomWithPadding() throws IOException {
        String input = "QQ==";
        byte[] expected = "A".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomWithWhitespaceBetweenPadding() throws IOException {
        String input = "QQ=\n=";
        byte[] expected = "A".getBytes();

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );

        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomWithLeadingWhitespace() throws IOException {
        String input = "\n\rQUJD";
        byte[] expected = "ABC".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomWithTrailingWhitespace() throws IOException {
        String input = "QUJD\n\r";
        byte[] expected = "ABC".getBytes();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PushbackInputStream inStream = new PushbackInputStream(
                new ByteArrayInputStream(input.getBytes())
        );
        decoder.decodeAtom(inStream, outStream, 4);
        assertArrayEquals(expected, outStream.toByteArray());
    }

    @Test
    public void testDecodeAtomEOFHandling() {
        String input1 = "";
        ByteArrayOutputStream outStream1 = new ByteArrayOutputStream();
        PushbackInputStream inStream1 = new PushbackInputStream(
                new ByteArrayInputStream(input1.getBytes()),
                10
        );
        assertThrows(IOException.class, () ->
                decoder.decodeAtom(inStream1, outStream1, 3));
        String input2 = "Q";
        ByteArrayOutputStream outStream2 = new ByteArrayOutputStream();
        PushbackInputStream inStream2 = new PushbackInputStream(
                new ByteArrayInputStream(input2.getBytes()),
                10
        );
        assertThrows(IOException.class, () ->
                decoder.decodeAtom(inStream2, outStream2, 3));
    }
}