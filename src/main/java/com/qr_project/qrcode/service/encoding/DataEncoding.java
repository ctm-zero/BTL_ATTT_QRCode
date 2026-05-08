package com.qr_project.qrcode.service.encoding;

import org.springframework.stereotype.Service;

@Service
public class DataEncoding {
    private static final String MODE_NUMERIC = "0001";
    private static final String MODE_ALPHANUMERIC = "0010";
    private static final String MODE_BYTE = "0100";

    private static final String NUMERIC_CHARSET = "0123456789";
    private static final String ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
    private static final String BYTE_CHARSET = "ISO-8859-1";
    
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
}
