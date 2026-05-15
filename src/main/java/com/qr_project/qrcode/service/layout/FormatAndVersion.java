package com.qr_project.qrcode.service.layout;

public class FormatAndVersion {
    private String formatString(String ecLevel, int maskPattern) {
        int ecBits = switch (ecLevel) {
            case "L" -> 0b01;
            case "M" -> 0b00;
            case "Q" -> 0b11;
            case "H" -> 0b10;
            default -> throw new IllegalArgumentException("Invalid EC level: " + ecLevel);
        };
        int data = (ecBits << 3) | maskPattern; // 5-bit data, no XOR yet

        // BCH on raw data
        int generator = 0b10100110111;
        int remainder = data << 10;
        for (int i = 14; i >= 10; i--) {
            if (((remainder >> i) & 1) == 1)
                remainder ^= generator << (i - 10);
        }
        int formatBits = (data << 10) | (remainder & 0b1111111111);
        formatBits ^= 0b101010000010010; // XOR mask applied last

        return String.format("%15s", Integer.toBinaryString(formatBits)).replace(' ', '0');
    }

    private String versionString(int version) {
        if (version < 7)
            return null; // Phiên bản < 7 không có thông tin version

        int versionInfo = version << 12; // Dịch sang trái để chừa chỗ cho BCH code
        int generator = 0b1111100100101; // Mã sinh BCH cho version info
        int remainder = versionInfo;
        for (int i = 17; i >= 12; i--) {
            if (((remainder >> i) & 1) == 1) {
                remainder ^= generator << (i - 12);
            }
        }
        int bchCode = remainder & 0b111111111111; // Lấy phần dư làm BCH code

        return String.format("%18s", Integer.toBinaryString((version << 12) | bchCode)).replace(' ', '0');
    }

    public void placeFormatAndVersionInfo(int[][] matrix, String ecLevel, int maskPattern, int version) {
        String formatBits = formatString(ecLevel, maskPattern);
        String versionBits = versionString(version);
        int size = matrix.length;

        // Format info — 15 bits, LSB (bit 0) first.
        // Per ISO 18004 Table C.1: bit i goes to two fixed positions.
        int[] topLeftRow = { 8, 8, 8, 8, 8, 8, 8, 8, 7, 5, 4, 3, 2, 1, 0 };
        int[] topLeftCol = { 0, 1, 2, 3, 4, 5, 7, 8, 8, 8, 8, 8, 8, 8, 8 };
        int[] otherRow = { size - 1, size - 2, size - 3, size - 4, size - 5, size - 6, size - 7, size - 8, 8, 8, 8, 8,
                8, 8, 8 };
        int[] otherCol = { 8, 8, 8, 8, 8, 8, 8, 8, size - 8, size - 7, size - 6, size - 5, size - 4, size - 3,
                size - 2 };

        for (int i = 0; i < 15; i++) {
            int bit = formatBits.charAt(14 - i) - '0'; // bit 0 = LSB = charAt(14)
            matrix[topLeftRow[i]][topLeftCol[i]] = bit;
            matrix[otherRow[i]][otherCol[i]] = bit;
        }

        // Version info — 18 bits, unchanged (this part was correct)
        if (version >= 7) {
            for (int i = 0; i < 18; i++) {
                int bit = versionBits.charAt(17 - i) - '0';
                int row = i / 3;
                int col = i % 3;
                matrix[row][size - 11 + col] = bit;
                matrix[size - 11 + col][row] = bit;
            }
        }
    }
}