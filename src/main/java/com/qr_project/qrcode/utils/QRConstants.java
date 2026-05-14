package com.qr_project.qrcode.utils;

public class QRConstants {
    public static final String[] ERROR_CORRECTION_LEVELS = { "L", "M", "Q", "H" };
    public static final String PAD_CODEWORD_1 = "11101100";
    public static final String PAD_CODEWORD_2 = "00010001";
    public static final int PRIMITIVE_POLYNOMIAL = 285;

    /**
     * Số remainder bits cần append vào cuối bitstream (sau EC codewords)
     * trước khi đặt vào ma trận, theo ISO 18004:2015 Table 1.
     * Index = version - 1.
     */
    public static final int[] REMAINDER_BITS = {
            0, // v1
            7, 7, 7, 7, 7, // v2–6
            0, 0, 0, 0, // v7–10
            0, 0, 0, // v11–13
            3, 3, 3, 3, 3, 3, 3, // v14–20
            4, 4, 4, 4, 4, 4, 4, // v21–27
            3, 3, 3, 3, 3, 3, 3, // v28–34
            0, 0, 0, 0, 0, 0 // v35–40
    };
}