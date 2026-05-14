package com.qr_project.qrcode.service.layout;

import org.springframework.stereotype.Service;

@Service
public class MatrixGenerator {
    private int qrSize(int version) {
        return 21 + 4 * (version - 1);
    }

    /*
     * Tạo ma trận QR code trống với kích thước theo phiên bản đã chọn
     * 
     * @param version: phiên bản QR code (1-40)
     * 
     * @return ma trận 2D chưa gán
     */
    public int[][] generateMatrix(int version) {
        int size = qrSize(version);
        int[][] matrix = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i][j] = -1;
            }
        }
        return matrix;
    }

    private void placeFinderPatternsAndSeparators(int[][] matrix) {
        int[][] finderPatterns = {
                { 1, 1, 1, 1, 1, 1, 1 },
                { 1, 0, 0, 0, 0, 0, 1 },
                { 1, 0, 1, 1, 1, 0, 1 },
                { 1, 0, 1, 1, 1, 0, 1 },
                { 1, 0, 1, 1, 1, 0, 1 },
                { 1, 0, 0, 0, 0, 0, 1 },
                { 1, 1, 1, 1, 1, 1, 1 }
        };

        int size = matrix.length;

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                matrix[i][j] = finderPatterns[i][j]; // Góc trên trái
                matrix[i][size - 7 + j] = finderPatterns[i][j]; // Góc trên phải
                matrix[size - 7 + i][j] = finderPatterns[i][j]; // Góc dưới trái
            }
        }

        // Separator xung quanh finder patterns
        for (int i = 0; i < 8; i++) {
            matrix[i][7] = 0; // Separator bên phải finder pattern trên trái
            matrix[7][i] = 0; // Separator bên dưới finder pattern trên trái
        }

        for (int i = 0; i < 8; i++) {
            matrix[i][size - 8] = 0; // Separator bên trái finder pattern trên phải
            matrix[7][size - 8 + i] = 0; // Separator bên dưới finder pattern trên phải
        }

        for (int i = 0; i < 8; i++) {
            matrix[size - 8][i] = 0; // Separator bên trên finder pattern dưới trái
            matrix[size - 8 + i][7] = 0; // Separator bên phải finder pattern dưới trái
        }
    }

    private void placeAlignmentPatterns(int[][] matrix, int version) {
        if (version < 2)
            return;
        int[][] alignmentPattern = {
                { 1, 1, 1, 1, 1 },
                { 1, 0, 0, 0, 1 },
                { 1, 0, 1, 0, 1 },
                { 1, 0, 0, 0, 1 },
                { 1, 1, 1, 1, 1 }
        };
        int[] centers = getCenterAlignmentPattern(version);
        for (int x : centers) {
            for (int y : centers) {
                if (overlapsFinderPattern(x, y, matrix.length))
                    continue;
                for (int i = 0; i < 5; i++) {
                    for (int j = 0; j < 5; j++) {
                        matrix[x - 2 + i][y - 2 + j] = alignmentPattern[i][j];
                    }
                }
            }
        }
    }

    private void placeTimingPatterns(int[][] matrix) {
        int size = matrix.length;
        for (int i = 8; i < size - 8; i++) {
            matrix[6][i] = (i % 2 == 0) ? 1 : 0;
            matrix[i][6] = (i % 2 == 0) ? 1 : 0;
        }
    }

    private void placeDarkModule(int[][] matrix, int version) {
        matrix[(4 * version) + 9][8] = 1; // Sửa row và col bị ngược
    }

    private void reserveFormatInformationAreas(int[][] matrix) {
        int size = matrix.length;
        for (int i = 0; i < 8; i++) {
            matrix[8][i] = 0; // top-left
            matrix[i][8] = 0; // top-left
            matrix[8][size - 1 - i] = 0; // top-right
            matrix[size - 1 - i][8] = 0; // bottom-left
        }
        matrix[8][8] = 0;
    }

    private void reserveVersionInformationAreas(int[][] matrix, int version) {
        if (version < 7)
            return;
        int size = matrix.length;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 3; j++) {
                matrix[size - 11 + j][i] = 0;
                matrix[i][size - 11 + j] = 0;
            }
        }
    }

    private int[][] generateBaseMatrix(int version) {
        int[][] matrix = generateMatrix(version);
        placeFinderPatternsAndSeparators(matrix);
        placeAlignmentPatterns(matrix, version);
        placeTimingPatterns(matrix);
        reserveFormatInformationAreas(matrix);
        reserveVersionInformationAreas(matrix, version);
        placeDarkModule(matrix, version);
        return matrix;
    }

    public int[][] dataBitsPlacement(int version, String bitStream) {
        int[][] matrix = generateBaseMatrix(version);
        int size = matrix.length;
        int bitIndex = 0;
        boolean upwards = true; // Zigzag direction
        for (int col = size - 1; col >= 0; col -= 2) {
            if (col == 6)
                col--; // skip vertical timing pattern
            // zigzag bottom to top
            if (upwards) {
                for (int row = size - 1; row >= 0; row--) {
                    for (int c = col; c >= col - 1 && c >= 0; c--) {
                        if (matrix[row][c] == -1 && bitIndex < bitStream.length()) {
                            matrix[row][c] = bitStream.charAt(bitIndex) - '0';
                            bitIndex++;
                        }
                    }
                }
            } else {
                for (int row = 0; row < size; row++) {
                    for (int c = col; c >= col - 1 && c >= 0; c--) {
                        if (matrix[row][c] == -1 && bitIndex < bitStream.length()) {
                            matrix[row][c] = bitStream.charAt(bitIndex) - '0';
                            bitIndex++;
                        }
                    }
                }
            }
            upwards = !upwards; // đổi hướng zigzag
        }
        return matrix;
    }

    private boolean overlapsFinderPattern(int row, int col, int size) {
        if (row <= 8 && col <= 8)
            return true; // top-left
        if (row <= 8 && col >= size - 9)
            return true; // top-right
        if (row >= size - 9 && col <= 8)
            return true; // bottom-left
        return false;
    }

    private static int[] getCenterAlignmentPattern(int version) {
        if (version < 2)
            return new int[] {};
        int[][] centers = {
                { 6, 18 }, //2
                { 6, 22 }, //3
                { 6, 26 }, //4
                { 6, 30 }, //5
                { 6, 34 }, //6
                { 6, 22, 38 }, //7
                { 6, 24, 42 }, //8
                { 6, 26, 46 }, //9
                { 6, 28, 50 }, //10
                { 6, 30, 54 }, //11
                { 6, 32, 58 }, //12
                { 6, 34, 62 }, //13
                { 6, 26, 46, 66 }, //14
                { 6, 26, 48, 70 }, //15
                { 6, 26, 50, 74 }, //16
                { 6, 30, 54, 78 }, //17
                { 6, 30, 56, 82 }, //18
                { 6, 30, 58, 86 }, //19
                { 6, 34, 62, 90 }, //20
                { 6, 28, 50, 72, 94 }, //21
                { 6, 26, 50, 74, 98 }, //22
                { 6, 30, 54, 78, 102 }, //23
                { 6, 28, 54, 80, 106 }, //24
                { 6, 32, 58, 84, 110 }, //25
                { 6, 30, 58, 86, 114 }, //26
                { 6, 34, 62, 90, 118 }, //27
                { 6, 26, 50, 74, 98, 122 }, //28
                { 6, 30, 54, 78, 102, 126 }, //29
                { 6, 26, 52, 78, 104, 130 }, //30
                { 6, 30, 56, 82, 108, 134 }, //31
                { 6, 34, 60, 86, 112, 138 }, //32
                { 6, 30, 58, 86, 113, 142 }, //33
                { 6, 34, 62, 90, 118, 146 }, //34
                { 6, 30, 54, 78, 102, 126, 150 }, //35
                { 6, 24, 50, 76, 102, 128, 154 }, //36
                { 6, 28, 54, 80, 106, 132, 158 }, //37
                { 6, 32, 58, 84, 110, 136, 162 }, //38
                { 6, 26, 54, 82, 110, 138, 166 }, //39
                { 6, 30, 58, 86, 114, 142, 170 } //40
        };
        return centers[version - 2];
    }
}
