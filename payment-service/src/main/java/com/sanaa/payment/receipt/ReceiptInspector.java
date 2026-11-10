package com.sanaa.payment.receipt;

/**
 * The only receipt formats accepted by the service. Detection uses magic bytes, so a text file
 * renamed to {@code .pdf} or a real PNG with a wrong declared Content-Type is classified by its
 * actual content.
 */
public final class ReceiptInspector {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String PDF = "application/pdf";

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F'};

    private ReceiptInspector() {
    }

    /**
     * Returns the verified content type, or {@code null} when the bytes are not a supported
     * receipt. An empty array is not a valid receipt either.
     */
    public static String detectContentType(byte[] content) {
        if (content == null) {
            return null;
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return PNG;
        }
        if (startsWith(content, JPEG_SIGNATURE)) {
            return JPEG;
        }
        if (startsWith(content, PDF_SIGNATURE)) {
            return PDF;
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
