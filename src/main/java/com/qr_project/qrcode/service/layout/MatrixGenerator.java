package com.qr_project.qrcode.service.layout;

import org.springframework.stereotype.Service;

@Service
public class MatrixGenerator {
    private int QRSize(int version) {
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
        int size = QRSize(version);
        int[][] matrix = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i][j] = -1;
            }
        }
        return matrix;
    }

    private void placeFinderPatternsAndSeperators(int[][] matrix) {
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
                matrix[i][j] = finderPatterns[i][j];                // Góc trên trái
                matrix[i][size - 7 + j] = finderPatterns[i][j];     // Góc trên phải
                matrix[size - 7 + i][j] = finderPatterns[i][j];     // Góc dưới trái
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
        int size = matrix.length;
        matrix[(4 * version) + 9][8] = 1; // Sửa row và col bị ngược
    }

    private void reserveFormatInformationAreas(int[][] matrix) {
        int size = matrix.length;
        for (int i=0; i<9; i++) {
            matrix[8][i] = 0; // top-left
            matrix[i][8] = 0; // top-left
            matrix[8][size - 1 - i] = 0; // top-right
            matrix[size - 1 - i][8] = 0; // bottom-left
        }
    }

    private void reserveVersionINformationAreas(int[][] matrix, int version) {
        if (version < 7 ) return;
        int size= matrix.length;
        for (int i=0; i<3; i++) {
            for (int j=0;  j<6; j++) {
                matrix[i][size -11 +j] = 0;
                matrix[size - 11 +j][i] = 0;
            }
        }
    }

    private int[][] generateBaseMatrix(int version) {
        int[][] matrix = generateMatrix(version);
        placeFinderPatternsAndSeperators(matrix);
        placeAlignmentPatterns(matrix, version);
        placeTimingPatterns(matrix);
        placeDarkModule(matrix, version);
        reserveFormatInformationAreas(matrix);
        reserveVersionINformationAreas(matrix, version);
        return matrix;
    }

    public int[][] dataBitsPlacement(int version, String bitStream){
        int[][] matrix = generateBaseMatrix(version);
        int size = matrix.length;
        int bitIndex = 0;
        for (int col=size-1; col>=0; col-=2) {
            if (col==6) col--; // skip vertical timing pattern
            //zigzag bottom to top
            for (int row =size-1; row>=0; row--) {
                for (int c=col; c>= col-1 && c>=0; c--) {
                    if (matrix[row][c] == -1 && bitIndex <bitStream.length()) {
                        matrix[row][c] = bitStream.charAt(bitIndex) - '0';
                        bitIndex++;
                    }
                }
            }
            //zigzag top to bottom
            for (int row=0; row<size; row++) {
                for (int c=col; c>= col-1 && c>=0; c--) {
                    if (matrix[row][c] == -1 && bitIndex <bitStream.length()) {
                        matrix[row][c] = bitStream.charAt(bitIndex) - '0';
                        bitIndex++;
                    }
                }
            }
        }
        return matrix;
    }

    private boolean overlapsFinderPattern(int row, int col, int size) {
        if (row <= 8 && col <= 8)
            return true; // top-left
        if (row <= 8 && col >= size - 8)
            return true; // top-right
        if (row >= size - 8 && col <= 8)
            return true; // bottom-left
        return false;
    }

    private static int[] getCenterAlignmentPattern(int version) {
        if (version < 2)
            return new int[] {};
        int[][] centers = {
                { 6, 18 },
                { 6, 22 },
                { 6, 26 },
                { 6, 30 },
                { 6, 34 },
                { 6, 22, 38 },
                { 6, 24, 42 },
                { 6, 26, 46 },
                { 6, 28, 50 },
                { 6, 30, 54 },
                { 6, 32, 58 },
                { 6, 34, 62 },
                { 6, 26, 46, 66 },
                { 6, 26, 48, 70 },
                { 6, 26, 50, 74 },
                { 6, 30, 54, 78 },
                { 6, 30, 56, 82 },
                { 6, 30, 58, 86 },
                { 6, 34, 62, 90 },
                { 6, 28, 50, 72, 94 },
                { 6, 26, 50, 74, 98 },
                { 6, 30, 54, 78, 102 },
                { 6, 28, 54, 80, 106 },
                { 6, 32, 58, 84, 110 },
                { 6, 30, 58, 86, 114 },
                { 6, 34, 62, 90, 118 },
                { 6, 26, 50, 74, 98, 122 },
                { 6, 30, 54, 78, 102, 126 },
                { 6, 26, 52, 78, 104, 130 },
                { 6, 30, 56, 82, 108, 134 },
                { 6, 34, 60, 86, 112, 138 },
                { 6, 30, 58, 86, 113, 142 },
                { 6, 34, 62, 90, 118, 146 },
                { 6, 30, 54, 78, 102, 126, 150 },
                { 6, 24, 50, 76, 102, 128, 154 },
                { 6, 28, 54, 80, 106, 132, 158 },
                { 6, 32, 58, 84, 110, 136, 162 },
                { 6, 26, 54, 82, 110, 138, 166 },
                { 6, 30, 58, 86, 114, 142, 170 }
        };
        return centers[version - 2];
    }
}
