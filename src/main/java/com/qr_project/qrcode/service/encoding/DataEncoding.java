package com.qr_project.qrcode.service.encoding;

import org.springframework.stereotype.Service;

@Service
public class DataEncoding {
    private static final String TERMINATOR = "0000";

    private static final String MODE_NUMERIC = "0001";
    private static final String MODE_ALPHANUMERIC = "0010";
    private static final String MODE_BYTE = "0100";

    private static final String NUMERIC_CHARSET = "0123456789";
    private static final String ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
    private static final String BYTE_CHARSET = "ISO-8859-1";

    private static final String[] ERROR_CORRECTION_LEVELS = {"L", "M", "Q", "H"};

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
        int eclIndex = java.util.Arrays.asList(ERROR_CORRECTION_LEVELS).indexOf(errorCorrectionLevel);
        if (eclIndex == -1) throw new IllegalArgumentException("Invalid error correction level");
        for (int v = 1; v <= 40; v++) {
            if (getCapacity(v, mode, eclIndex) >= length) {
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
        for (int i = 0; i < data.length(); i += 3) {
            String group = data.substring(i, Math.min(i + 3, data.length())); // Chia thành nhóm 3 số
            int bitLength;

            if (group.length() == 3) {
                bitLength = 10; 
            } else if (group.length() == 2) {
                bitLength = 7; 
            } else {
                bitLength = 4;
            }

            sb.append(toBinary(Integer.parseInt(group), bitLength));
        }
        return sb.toString();
    }

    private String encodeAlphanumeric(String data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length(); i += 2) {
            String group = data.substring(i, Math.min(i + 2, data.length())); // Chia thành cặp 2 ký tự
            int value = 0;
            if (group.length() == 2) {
                int firstCharValue = ALPHANUMERIC_CHARSET.indexOf(group.charAt(0));
                int secondCharValue = ALPHANUMERIC_CHARSET.indexOf(group.charAt(1));
                value = firstCharValue * 45 + secondCharValue;
                sb.append(toBinary(value, 11));
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

    public String eightBitCodewords(String data, String errorCorrectionLevel) {
        int version = versionDetermine(data, errorCorrectionLevel);
        int totalBits = getTotalCodewords(version, errorCorrectionLevel) * 8;

        String encodedData = encodeData(data, version);
        if (encodedData.length() > totalBits) {
            throw new IllegalArgumentException("Encoded data exceeds capacity");
        }

        StringBuilder sb = new StringBuilder(encodedData);
        for (int i = 0; i<4 && sb.length() < totalBits; i++) {
            sb.append('0'); // Terminator bits
        }

        int rem = sb.length() % 8;
        if (rem != 0) {
            for (int i = 0; i < (8 - rem) && sb.length() < totalBits; i++) {
                sb.append('0'); // Padding bits
            }
        }
        
        while (sb.length() < totalBits) {
            sb.append("11101100"); // Pad codeword 1
            if (sb.length() < totalBits) {
                sb.append("00010001"); // Pad codeword 2
            }
        }
        return sb.substring(0, totalBits);
    }

    private int getTotalCodewords(int version, String errorCorrectionLevel) {
        int[][] codewords = {
            {19, 16, 13, 9}, //1
            {34, 28, 22, 16}, //2
            {55, 44, 34, 26}, //3
            {80, 64, 48, 36}, //4
            {108, 86, 62, 46}, //5
            {136, 108, 76, 60}, //6
            {156, 124, 88, 66}, //7
            {194, 154, 110, 86}, //8
            {232, 182, 132, 100}, //9
            {274, 216, 154, 122}, //10
            {324, 254, 180, 140}, //11
            {370, 290, 206, 158}, //12
            {428, 334, 244, 180}, //13
            {461, 365, 261, 197}, //14
            {523, 415, 295, 223}, //15
            {589, 453 ,325 ,253}, //16
            {647 ,507 ,367 ,283}, //17
            {721 ,563 ,397 ,313 }, //18
            {795 ,627 ,445 ,341 }, //19
            {861 ,669 ,485 ,385 }, //20
            {932 ,714 ,512 ,406 }, //21
            {1006 ,782 ,568 ,442 }, //22
            {1094 ,860 ,614 ,464 }, //23
            {1174 ,914 ,664 ,514 }, //24
            {1276 ,1000 ,718 ,538 }, //25
            {1370 ,1062 ,754 ,596 }, //26
            {1468 ,1128 ,808 ,628 }, //27
            {1531 ,1193 ,871 ,661 }, //28
            {1631 ,1267 ,911 ,701 }, //29
            {1735 ,1373 ,985 ,745 }, //30
            {1843 ,1455 ,1033 ,793 }, //31
            {1955 ,1541 ,1115 ,845 }, //32
            {2071 ,1631 ,1171 ,901 }, //33
            {2191 ,1725 ,1231 ,961 }, //34
            {2306 ,1812 ,1286 ,986 }, //35
            {2434 ,1914 ,1354 ,1054 }, //36
            {2566 ,1992 ,1426 ,1096 }, //37
            {2702 ,2102 ,1502 ,1142 }, //38
            {2812 ,2216 ,1582,1222},   //39
            {2956, 2334, 1666, 1276}    //40
        };
        int eclIndex = java.util.Arrays.asList(ERROR_CORRECTION_LEVELS).indexOf(errorCorrectionLevel);
        if (eclIndex == -1) throw new IllegalArgumentException("Invalid error correction level");
        return codewords[version - 1][eclIndex];
    }
    private int getCapacity(int version, String mode, int eclIndex) {
        int[][] numeric = {
            {41,33,27,17},
            {77,63,53,34},
            {127,101,85,58},
            {187,149,125,82},
            {255,202,160,106},
            {322,255,202,139},
            {370,293,235,154},
            {461,365,296,202},
            {552,432,365,235},
            {652,513,428,288},
            {772,604,518,331},
            {883,691,604,374},
            {1022,796,691,427},
            {1101,871,796,468},
            {1250,991,871,530},
            {1408,1082,991,602},
            {1548,1212,1082,674},
            {1725,1346,1212,746},
            {1903,1500,1346,813},
            {2061,1600,1500,919},
            {2232,1708,1600,969},
            {2409,1872,1708,1056},
            {2620,2059,1872,1108},
            {2812,2188,2059,1228},
            {3057,2395,2188,1286},
            {3283,2544,2395,1425},
            {3517,2701,2544,1501},
            {3669,2857,2701,1581},
            {3909,3035,2857,1677},
            {4158,3289,3035,1782},
            {4417,3486,3289,1897},
            {4686,3693,3486,2022},
            {4965,3909,3693,2157},
            {5253,4134,3909,2301},
            {5529,4343,4134,2361},
            {5836,4588,4343,2524},
            {6153,4775,4588,2625},
            {6479,5039,4775,2735},
            {6743,5313,5039,2927},
            {7089,5596,5313,3057}
        };
        int[][] alphanumeric = {
            {25,20,16,10},
            {47,38,29,20},
            {77,61,49,35},
            {114,90,67,46},
            {154,122,87,60},
            {195,154,108,74},
            {224,178,125,86},
            {279,221,157,108},
            {335,262,189,130},
            {395,311,221,151},
            {468,366,259,177},
            {535,419,296,203},
            {619,483,352,241},
            {667,528,376,258},
            {758,600,426,292},
            {854,656,470,322},
            {938,734,531,364},
            {1046,816,574,394},
            {1153,909,644,442},
            {1249,970,718,482},
            {1352,1035,792,509},
            {1460,1134,858,565},
            {1588,1248,929,611},
            {1704,1326,1003,661},
            {1853,1451,1091,715},
            {1990,1542,1171,751},
            {2132,1637,1273,805},
            {2223,1732,1367,868},
            {2369,1839,1465,908},
            {2520,1994,1528,982},
            {2677,2113,1628,1030},
            {2840,2238,1732,1112},
            {3009,2369,1840,1168},
            {3183,2506,1952,1228},
            {3351,2632,2068,1283},
            {3537,2780,2188,1351},
            {3729,2894,2303,1423},
            {3927,3054,2431,1499},
            {4087,3220,2563,1618},
            {4296,3391,2699,1700}
        };
        int[][] byteCap = {
            {17,14,11,7},
            {32,26,20,14},
            {53,42,32,24},
            {78,62,46,34},
            {106,84,60,44},
            {134,106,74,58},
            {154,122,86,64},
            {192,152,108,84},
            {230,180,130,98},
            {271,213,151,119},
            {321,251,177,137},
            {367,287,203,155},
            {425,331,241,177},
            {458,362,258,194},
            {520,412,292,220},
            {586,450,322,250},
            {644,504,364,280},
            {718,560,394,310},
            {792,624,442,338},
            {858,666,482,382},
            {929,711,509,403},
            {1003,779,565,439},
            {1091,857,611,461},
            {1171,911,661,511},
            {1273,997,715,535},
            {1367,1059,751,593},
            {1465,1125,805,625},
            {1528,1190,868,658},
            {1628,1264,908,698},
            {1732,1370,982,742},
            {1840,1452,1030,790},
            {1952,1538,1112,842},
            {2068,1628,1168,898},
            {2188,1722,1228,958},
            {2303,1809,1283,983},
            {2431,1911,1351,1051},
            {2563,1989,1423,1093},
            {2699,2099,1499,1139},
            {2809,2213,1618,1229},
            {2953,2331,1700,1273}
        };
        int[][] cap;
        switch (mode) {
            case "numeric":
                cap = numeric;
                break;
            case "alphanumeric":
                cap = alphanumeric;
                break;
            case "byte":
                cap = byteCap;
                break;
            default:
                throw new IllegalArgumentException("Invalid mode");
        }
        return cap[version - 1][eclIndex];
    }
}