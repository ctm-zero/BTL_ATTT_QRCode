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
            (row, col) -> (Math.floor(row / 2) + Math.floor(col / 3)) % 2 == 0, // Mask 4
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

        // Finder-like patterns
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size - 6; j++) {
                if (matrix[i][j] == 1 && matrix[i][j + 1] == 0 && matrix[i][j + 2] == 1 &&
                        matrix[i][j + 3] == 1 && matrix[i][j + 4] == 1 && matrix[i][j + 5] == 0 &&
                        matrix[i][j + 6] == 1) {
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

    public int[][] applyMask(int[][] matrix, int maskPattern) {
        int size = matrix.length;
        int[][] maskedMatrix = new int[size][size];
        int lowestPenalty = Integer.MAX_VALUE;

        for (int mask = 0; mask < MASK_FUNC.length; mask++) {
            int[][] tempMatrix = new int[size][size];
            MaskFunction tempFunc = MASK_FUNC[mask];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    tempMatrix[i][j] = matrix[i][j];
                    if (tempFunc.apply(i, j)) {
                        tempMatrix[i][j] ^= 1;
                    }
                }
            }
            int penalty = penaltyScore(tempMatrix);
            if (penalty < lowestPenalty) {
                lowestPenalty = penalty;
                maskedMatrix = tempMatrix;
            }
        }
        return maskedMatrix;
    }
}
