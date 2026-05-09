package com.qr_project.qrcode.service.encoding;

import org.springframework.stereotype.Service;
import com.qr_project.qrcode.utils.QRConstants;
import com.qr_project.qrcode.utils.QRTable;

@Service
public class DataEncoding {

    private static final String MODE_NUMERIC = "0001";
    private static final String MODE_ALPHANUMERIC = "0010";
    private static final String MODE_BYTE = "0100";

    private static final String NUMERIC_CHARSET = "0123456789";
    private static final String ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
    private static final String BYTE_CHARSET = "ISO-8859-1";

    // Kiểm tra loại dữ liệu
    public boolean isNumeric(String data) {
        for (char c : data.toCharArray()) {
            if (NUMERIC_CHARSET.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }

    public boolean isAlphanumeric(String data) {
        for (char c : data.toCharArray()) {
            if (ALPHANUMERIC_CHARSET.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }

    public boolean isByte(String data) {
        try {
            data.getBytes(BYTE_CHARSET);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String toBinary(int value, int bitlength) {
        String binaryString = Integer.toBinaryString(value);
        StringBuilder sb = new StringBuilder();
        while (sb.length() + binaryString.length() < bitlength) {
            sb.append('0');
        }
        sb.append(binaryString);
        return sb.toString();
    }

    // Mã hóa dữ liệu
    public int versionDetermine(String data, String errorCorrectionLevel) {
        int length = data.length();
        String mode;
        if (isNumeric(data)) {
            mode = "numeric";
        } else if (isAlphanumeric(data)) {
            mode = "alphanumeric";
        } else if (isByte(data)) {
            mode = "byte";
        } else {
            throw new IllegalArgumentException("Unsupported data format");
        }
        int eclIndex = java.util.Arrays.asList(QRConstants.ERROR_CORRECTION_LEVELS).indexOf(errorCorrectionLevel);
        if (eclIndex == -1)
            throw new IllegalArgumentException("Invalid error correction level");
        for (int v = 1; v <= 40; v++) {
            if (QRTable.getCapacity(v, mode, eclIndex) >= length) {
                return v;
            }
        }
        throw new IllegalArgumentException("Data too long for QR code");
    }

    public String encodeData(String data, int version) {
        if (isNumeric(data)) {
            int ccBits = version <= 9 ? 10 : (version <= 26 ? 12 : 14);
            return MODE_NUMERIC + toBinary(data.length(), ccBits) + encodeNumeric(data);
        } else if (isAlphanumeric(data)) {
            int ccBits = version <= 9 ? 9 : (version <= 26 ? 11 : 13);
            return MODE_ALPHANUMERIC + toBinary(data.length(), ccBits) + encodeAlphanumeric(data);
        } else if (isByte(data)) {
            int ccBits = version <= 9 ? 8 : 16;
            return MODE_BYTE + toBinary(data.length(), ccBits) + encodeByte(data);
        } else {
            throw new IllegalArgumentException("Unsupported data format");
        }
    }

    private String encodeNumeric(String data) {
        StringBuilder sb = new StringBuilder();
        int remainder = data.length() % 3;
        int fullGroups = data.length() / 3;
        int i = 0;

        for (int j = 0; j < fullGroups; j++) {
            String group = data.substring(i, i + 3);
            sb.append(toBinary(Integer.parseInt(group), 10));
            i += 3;
        }

        if (remainder == 2) {
            String group = data.substring(i, i + 2);
            sb.append(toBinary(Integer.parseInt(group), 7));
        } else if (remainder == 1) {
            String group = data.substring(i, i + 1);
            sb.append(toBinary(Integer.parseInt(group), 4));
        }

        return sb.toString();
    }

    private String encodeAlphanumeric(String data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length(); i += 2) {
            String group = data.substring(i, Math.min(i + 2, data.length()));
            if (group.length() == 2) {
                int firstCharValue = ALPHANUMERIC_CHARSET.indexOf(group.charAt(0));
                int secondCharValue = ALPHANUMERIC_CHARSET.indexOf(group.charAt(1));
                sb.append(toBinary(firstCharValue * 45 + secondCharValue, 11));
            } else {
                int charValue = ALPHANUMERIC_CHARSET.indexOf(group.charAt(0));
                sb.append(toBinary(charValue, 6));
            }
        }
        return sb.toString();
    }

    private String encodeByte(String data) {
        StringBuilder sb = new StringBuilder();
        try {
            byte[] bytes = data.getBytes(BYTE_CHARSET);
            for (byte b : bytes) {
                sb.append(toBinary(b & 0xFF, 8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Encoding error", e);
        }
        return sb.toString();
    }

    public String eightBitCodewords(String data, String errorCorrectionLevel, int version) {
        int totalBits = QRTable.getTotalCodewords(version, errorCorrectionLevel) * 8;
        String encodedData = encodeData(data, version);

        if (encodedData.length() > totalBits) {
            throw new IllegalArgumentException("Encoded data exceeds capacity");
        }

        StringBuilder sb = new StringBuilder(encodedData);

        // Terminator
        for (int i = 0; i < 4 && sb.length() < totalBits; i++) {
            sb.append('0');
        }

        // Padding bits để chia hết cho 8
        int rem = sb.length() % 8;
        if (rem != 0) {
            for (int i = 0; i < (8 - rem) && sb.length() < totalBits; i++) {
                sb.append('0');
            }
        }

        // Pad codewords
        while (sb.length() < totalBits) {
            sb.append(QRConstants.PAD_CODEWORD_1);
            if (sb.length() < totalBits) {
                sb.append(QRConstants.PAD_CODEWORD_2);
            }
        }

        return sb.substring(0, totalBits);
    }

    public String generateBitstream(String data, String version, String errorCorrectionLevel) {

        // Xử lý EC level auto - mặc định M
        String ecLevel = errorCorrectionLevel.equals("auto") ? "M" : errorCorrectionLevel;

        // Xử lý version auto hoặc thủ công
        int resolvedVersion;
        if (version.equals("auto")) {
            resolvedVersion = versionDetermine(data, ecLevel);
        } else {
            resolvedVersion = Integer.parseInt(version);

            String mode;
            if (isNumeric(data)) mode = "numeric";
            else if (isAlphanumeric(data)) mode = "alphanumeric";
            else mode = "byte";

            int eclIndex = java.util.Arrays.asList(QRConstants.ERROR_CORRECTION_LEVELS).indexOf(ecLevel);
            if (QRTable.getCapacity(resolvedVersion, mode, eclIndex) < data.length()) {
                throw new IllegalArgumentException(
                        "Data too long for version " + resolvedVersion + " with EC level " + ecLevel);
            }
        }

        return eightBitCodewords(data, ecLevel, resolvedVersion);
    }
}