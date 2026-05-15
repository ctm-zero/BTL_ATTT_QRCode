package com.qr_project.qrcode.service.layout;

public class MatrixMask {
    @FunctionalInterface
    private interface MaskFunction {
        boolean apply(int row, int col);
    }

    private static final MaskFunction[] MASK_FUNC = {
            (row, col) -> (row + col) % 2 == 0, // Mask 0
            (row, col) -> row % 2 == 0, // Mask 1
            (row, col) -> col % 3 == 0, // Mask 2
            (row, col) -> (row + col) % 3 == 0, // Mask 3
            (row, col) -> ((row / 2) + (col / 3)) % 2 == 0, // Mask 4
            (row, col) -> ((row * col) % 2) + ((row * col) % 3) == 0, // Mask 5
            (row, col) -> (((row * col) % 2) + ((row * col) % 3)) % 2 == 0, // Mask 6
            (row, col) -> (((row + col) % 2) + ((row * col) % 3)) % 2 == 0 // Mask 7
    };

    private int penaltyScore(int[][] matrix) {
        int score = 0;
        int size = matrix.length;

        // 5 or more same color in a row/column
        for (int i = 0; i < size; i++) {
            int rowCount = 1;
            int colCount = 1;
            for (int j = 1; j < size; j++) {
                if (matrix[i][j] == matrix[i][j - 1]) {
                    rowCount++;
                } else {
                    if (rowCount >= 5)
                        score += 3 + (rowCount - 5);
                    rowCount = 1;
                }
                if (matrix[j][i] == matrix[j - 1][i]) {
                    colCount++;
                } else {
                    if (colCount >= 5)
                        score += 3 + (colCount - 5);
                    colCount = 1;
                }
            }
            if (rowCount >= 5)
                score += 3 + (rowCount - 5);
            if (colCount >= 5)
                score += 3 + (colCount - 5);
        }

        // 2x2 blocks of same color
        for (int i = 0; i < size - 1; i++) {
            for (int j = 0; j < size - 1; j++) {
                if (matrix[i][j] == matrix[i][j + 1] &&
                        matrix[i][j] == matrix[i + 1][j] &&
                        matrix[i][j] == matrix[i + 1][j + 1]) {
                    score += 3;
                }
            }
        }

        // Finder-like patterns — rows
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size - 6; j++) {
                if (matrix[i][j] == 1 && matrix[i][j + 1] == 0 && matrix[i][j + 2] == 1 &&
                        matrix[i][j + 3] == 1 && matrix[i][j + 4] == 1 && matrix[i][j + 5] == 0 &&
                        matrix[i][j + 6] == 1) {
                    score += 40;
                }
            }
        }
        // Finder-like patterns — columns
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size - 6; i++) {
                if (matrix[i][j] == 1 && matrix[i + 1][j] == 0 && matrix[i + 2][j] == 1 &&
                        matrix[i + 3][j] == 1 && matrix[i + 4][j] == 1 && matrix[i + 5][j] == 0 &&
                        matrix[i + 6][j] == 1) {
                    score += 40;
                }
            }
        }

        // Dark module ratio
        int darkCount = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (matrix[i][j] == 1)
                    darkCount++;
            }
        }
        int totalModules = size * size;
        int darkRatio = (darkCount * 100) / totalModules;
        int prevMultipleOf5 = (darkRatio / 5) * 5;
        int nextMultipleOf5 = prevMultipleOf5 + 5;
        score += Math.min(Math.abs(darkRatio - prevMultipleOf5), Math.abs(darkRatio - nextMultipleOf5)) * 10;

        return score;
    }

    public int[][] applyMask(int[][] matrix, int version) {
        int size = matrix.length;
        int[][] maskedMatrix = new int[size][size];
        int lowestPenalty = Integer.MAX_VALUE;

        for (int mask = 0; mask < MASK_FUNC.length; mask++) {
            int[][] tempMatrix = new int[size][size];
            MaskFunction tempFunc = MASK_FUNC[mask];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    tempMatrix[i][j] = matrix[i][j];
                    if (tempFunc.apply(i, j) && !isFunctionModule(i, j, size, version)) {
                        tempMatrix[i][j] ^= 1;
                    }
                }
            }
            int penalty = penaltyScore(tempMatrix);
            if (penalty < lowestPenalty) {
                lowestPenalty = penalty;
                for (int r = 0; r < size; r++)
                    maskedMatrix[r] = tempMatrix[r].clone();
            }
        }
        return maskedMatrix;
    }

    private boolean isFunctionModule(int row, int col, int size, int version) {
        // Finder patterns + separators
        if (row < 9 && col < 9)
            return true;
        if (row < 9 && col >= size - 8)
            return true;
        if (row >= size - 8 && col < 9)
            return true;

        // Timing patterns
        if (row == 6 || col == 6)
            return true;

        // Dark module
        if (row == 4 * version + 9 && col == 8)
            return true;

        // Alignment patterns (only those actually placed)
        if (version >= 2) {
            int[] centers = getCenterAlignmentPattern(version);
            for (int cx : centers) {
                for (int cy : centers) {
                    if (overlapsFinderOrTiming(cx, cy, size))
                        continue;
                    if (Math.abs(row - cx) <= 2 && Math.abs(col - cy) <= 2)
                        return true;
                }
            }
        }

        // Version information areas
        if (version >= 7) {
            if (row < 6 && col >= size - 11 && col <= size - 9)
                return true;
            if (col < 6 && row >= size - 11 && row <= size - 9)
                return true;
        }

        return false;
    }

    private static boolean overlapsFinderOrTiming(int row, int col, int size) {
        if (row < 8 && col < 8)
            return true;
        if (row < 8 && col >= size - 8)
            return true;
        if (row >= size - 8 && col < 8)
            return true;
        if (row == 6 || col == 6)
            return true;
        return false;
    }
    private static int[] getCenterAlignmentPattern(int version) {
        if (version < 2)
            return new int[] {};
        int[][] centers = {
                { 6, 18 }, // 2
                { 6, 22 }, // 3
                { 6, 26 }, // 4
                { 6, 30 }, // 5
                { 6, 34 }, // 6
                { 6, 22, 38 }, // 7
                { 6, 24, 42 }, // 8
                { 6, 26, 46 }, // 9
                { 6, 28, 50 }, // 10
                { 6, 30, 54 }, // 11
                { 6, 32, 58 }, // 12
                { 6, 34, 62 }, // 13
                { 6, 26, 46, 66 }, // 14
                { 6, 26, 48, 70 }, // 15
                { 6, 26, 50, 74 }, // 16
                { 6, 30, 54, 78 }, // 17
                { 6, 30, 56, 82 }, // 18
                { 6, 30, 58, 86 }, // 19
                { 6, 34, 62, 90 }, // 20
                { 6, 28, 50, 72, 94 }, // 21
                { 6, 26, 50, 74, 98 }, // 22
                { 6, 30, 54, 78, 102 }, // 23
                { 6, 28, 54, 80, 106 }, // 24
                { 6, 32, 58, 84, 110 }, // 25
                { 6, 30, 58, 86, 114 }, // 26
                { 6, 34, 62, 90, 118 }, // 27
                { 6, 26, 50, 74, 98, 122 }, // 28
                { 6, 30, 54, 78, 102, 126 }, // 29
                { 6, 26, 52, 78, 104, 130 }, // 30
                { 6, 30, 56, 82, 108, 134 }, // 31
                { 6, 34, 60, 86, 112, 138 }, // 32
                { 6, 30, 58, 86, 114, 142 }, // 33
                { 6, 34, 62, 90, 118, 146 }, // 34
                { 6, 30, 54, 78, 102, 126, 150 }, // 35
                { 6, 24, 50, 76, 102, 128, 154 }, // 36
                { 6, 28, 54, 80, 106, 132, 158 }, // 37
                { 6, 32, 58, 84, 110, 136, 162 }, // 38
                { 6, 26, 54, 82, 110, 138, 166 }, // 39
                { 6, 30, 58, 86, 114, 142, 170 } // 40
        };
        return centers[version - 2];
    }
}
