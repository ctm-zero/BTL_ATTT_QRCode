package com.qr_project.qrcode.service.error;

import com.qr_project.qrcode.utils.QRConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reed-Solomon error correction for QR codes.
 *
 * Pipeline:
 *   1. splitIntoBlocks()  — split data codewords into groups/blocks
 *   2. generateECForBlock() — RS division per block
 *   3. interleaveBlocks()  — interleave data blocks, then EC blocks
 *   4. generateErrorCorrection() — full pipeline, returns final bitstream
 */
@Service
public class ErrorCorrection {

    // ─────────────────────────────────────────────────────────────
    // GF(256) tables  (primitive polynomial 0x11D = 285)
    // ─────────────────────────────────────────────────────────────
    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= QRConstants.PRIMITIVE_POLYNOMIAL;
        }
        // Extend exp table to avoid modulo in multiply
        for (int i = 255; i < 512; i++) {
            GF_EXP[i] = GF_EXP[i - 255];
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GF(256) arithmetic
    // ─────────────────────────────────────────────────────────────

    int gfMultiply(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return GF_EXP[GF_LOG[a] + GF_LOG[b]];
    }

    int[] gfPolyMultiply(int[] p, int[] q) {
        int[] result = new int[p.length + q.length - 1];
        for (int i = 0; i < p.length; i++) {
            for (int j = 0; j < q.length; j++) {
                result[i + j] ^= gfMultiply(p[i], q[j]);
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Generator polynomial
    // g(x) = (x - α^0)(x - α^1)...(x - α^(n-1))
    // where α = 2 in GF(256)
    // ─────────────────────────────────────────────────────────────

    int[] generatorPolynomial(int numECCodewords) {
        int[] g = {1};
        for (int i = 0; i < numECCodewords; i++) {
            g = gfPolyMultiply(g, new int[]{1, GF_EXP[i]});
        }
        return g;
    }

    // ─────────────────────────────────────────────────────────────
    // EC codewords for a single block via polynomial long division
    // ─────────────────────────────────────────────────────────────

    public int[] generateECForBlock(int[] dataCodewords, int numEC) {
        int[] generator = generatorPolynomial(numEC);

        // Multiply message polynomial by x^numEC (append numEC zeros)
        int[] msg = Arrays.copyOf(dataCodewords, dataCodewords.length + numEC);

        for (int i = 0; i < dataCodewords.length; i++) {
            int coef = msg[i];
            if (coef == 0) continue;
            for (int j = 1; j < generator.length; j++) {
                msg[i + j] ^= gfMultiply(generator[j], coef);
            }
        }

        // Remainder = EC codewords (last numEC bytes of msg)
        return Arrays.copyOfRange(msg, dataCodewords.length, msg.length);
    }

    // ─────────────────────────────────────────────────────────────
    // Block splitting
    // ─────────────────────────────────────────────────────────────

    /**
     * Splits a flat list of data codewords into blocks according to
     * the EC table entry for this version/ECL.
     *
     * @param codewords  flat array of data codewords
     * @param entry      EC table entry: [g1Blocks, g1CW, g2Blocks, g2CW, ecPerBlock]
     * @return           list of blocks (each block is an int[])
     */
    public List<int[]> splitIntoBlocks(int[] codewords, int[] entry) {
        int g1Blocks    = entry[0];
        int g1CWPerBlock = entry[1];
        int g2Blocks    = entry[2];
        int g2CWPerBlock = entry[3];

        List<int[]> blocks = new ArrayList<>();
        int idx = 0;

        for (int i = 0; i < g1Blocks; i++) {
            blocks.add(Arrays.copyOfRange(codewords, idx, idx + g1CWPerBlock));
            idx += g1CWPerBlock;
        }
        for (int i = 0; i < g2Blocks; i++) {
            blocks.add(Arrays.copyOfRange(codewords, idx, idx + g2CWPerBlock));
            idx += g2CWPerBlock;
        }

        return blocks;
    }

    // ─────────────────────────────────────────────────────────────
    // Interleaving
    // ─────────────────────────────────────────────────────────────

    /**
     * Interleaves blocks column-by-column.
     * For blocks of unequal length (group 2 has one extra codeword),
     * shorter blocks simply contribute nothing at that position.
     */
    public int[] interleaveBlocks(List<int[]> blocks) {
        int maxLen = blocks.stream().mapToInt(b -> b.length).max().orElse(0);
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < maxLen; i++) {
            for (int[] block : blocks) {
                if (i < block.length) {
                    result.add(block[i]);
                }
            }
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    // ─────────────────────────────────────────────────────────────
    // Full pipeline
    // ─────────────────────────────────────────────────────────────

    /**
     * Takes the data bitstream from eightBitCodewords, generates EC
     * codewords, interleaves everything, and returns the final
     * combined bitstream ready for matrix placement.
     *
     * @param dataBitstream  output of DataEncoding.eightBitCodewords()
     * @param version        QR version (1–40)
     * @param ecLevel        "L", "M", "Q", or "H"
     * @return               interleaved data + EC codewords as a binary string
     */
    public String generateErrorCorrection(String dataBitstream, int version, String ecLevel) {
        // Convert bitstream to codeword array
        int[] dataCodewords = bitstreamToCodewords(dataBitstream);

        // Look up EC table
        int[] entry = getECTableEntry(version, ecLevel);
        int ecPerBlock = entry[4];

        // Split data into blocks
        List<int[]> dataBlocks = splitIntoBlocks(dataCodewords, entry);

        // Generate EC for each block
        List<int[]> ecBlocks = new ArrayList<>();
        for (int[] block : dataBlocks) {
            ecBlocks.add(generateECForBlock(block, ecPerBlock));
        }

        // Interleave data blocks, then EC blocks
        int[] interleavedData = interleaveBlocks(dataBlocks);
        int[] interleavedEC   = interleaveBlocks(ecBlocks);

        // Concatenate and convert back to bitstream
        StringBuilder sb = new StringBuilder();
        for (int cw : interleavedData) sb.append(toBinary8(cw));
        for (int cw : interleavedEC)   sb.append(toBinary8(cw));

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private int[] bitstreamToCodewords(String bitstream) {
        int numCodewords = bitstream.length() / 8;
        int[] codewords = new int[numCodewords];
        for (int i = 0; i < numCodewords; i++) {
            codewords[i] = Integer.parseInt(bitstream.substring(i * 8, i * 8 + 8), 2);
        }
        return codewords;
    }

    private String toBinary8(int value) {
        String b = Integer.toBinaryString(value);
        StringBuilder sb = new StringBuilder();
        for (int i = b.length(); i < 8; i++) sb.append('0');
        sb.append(b);
        return sb.toString();
    }

    /**
     * EC table: for each (version, ECL) returns
     * [g1Blocks, g1CWperBlock, g2Blocks, g2CWperBlock, ecPerBlock]
     *
     * Source: ISO 18004:2015 Table 9
     */
    public int[] getECTableEntry(int version, String ecLevel) {
        // Index: 0=L, 1=M, 2=Q, 3=H
        int[][] table = {
            // v1
            {1,19,0,0,7},  {1,16,0,0,10}, {1,13,0,0,13}, {1,9,0,0,17},
            // v2
            {1,34,0,0,10}, {1,28,0,0,16}, {1,22,0,0,22}, {1,16,0,0,28},
            // v3
            {1,55,0,0,15}, {1,44,0,0,26}, {2,17,0,0,18}, {2,13,0,0,22},
            // v4
            {1,80,0,0,20}, {2,32,0,0,18}, {2,24,0,0,26}, {4,9,0,0,16},
            // v5
            {1,108,0,0,26},{2,43,0,0,24}, {2,15,2,16,18},{2,11,2,12,22},
            // v6
            {2,68,0,0,18}, {4,27,0,0,16}, {4,19,0,0,24}, {4,15,0,0,28},
            // v7
            {2,78,0,0,20}, {4,31,0,0,18}, {2,14,4,15,18},{4,13,1,14,26},
            // v8
            {2,97,0,0,24}, {2,38,2,39,22},{4,18,2,19,22},{4,14,2,15,26},
            // v9
            {2,116,0,0,30},{3,36,2,37,22},{4,16,4,17,20},{4,12,4,13,24},
            // v10
            {2,68,2,69,18},{4,43,1,44,26},{6,19,2,20,24},{6,15,2,16,28},
            // v11
            {4,81,0,0,20}, {1,50,4,51,30},{4,22,4,23,28},{3,12,8,13,24},
            // v12
            {2,92,2,93,24},{6,36,2,37,22},{4,20,6,21,26},{7,14,4,15,28},
            // v13
            {4,107,0,0,26},{8,37,1,38,22},{8,20,4,21,24},{12,11,4,12,22},
            // v14
            {3,115,1,116,30},{4,40,5,41,24},{11,16,5,17,20},{11,12,5,13,24},
            // v15
            {5,87,1,88,22},{5,41,5,42,24},{5,24,7,25,30},{11,12,7,13,24},
            // v16
            {5,98,1,99,24},{7,45,3,46,28},{15,19,2,20,24},{3,15,13,16,30},
            // v17
            {1,107,5,108,28},{10,46,1,47,28},{1,22,15,23,28},{2,14,17,15,28},
            // v18
            {5,120,1,121,30},{9,43,4,44,26},{17,22,1,23,28},{2,14,19,15,26},
            // v19
            {3,113,4,114,28},{3,44,11,45,26},{17,21,4,22,26},{9,13,16,14,26},
            // v20
            {3,107,5,108,28},{3,41,13,42,26},{15,24,5,25,30},{15,15,10,16,28},
            // v21
            {4,116,4,117,28},{17,42,0,0,28},{17,22,6,23,28},{19,16,6,17,28},
            // v22
            {2,111,7,112,28},{17,46,0,0,28},{7,24,16,25,30},{34,13,0,0,24},
            // v23
            {4,121,5,122,30},{4,47,14,48,28},{11,24,14,25,30},{16,15,14,16,30},
            // v24
            {6,117,4,118,30},{6,45,14,46,28},{11,24,16,25,30},{30,16,2,17,30},
            // v25
            {8,106,4,107,26},{8,47,13,48,28},{7,24,22,25,30},{22,15,13,16,30},
            // v26
            {10,114,2,115,28},{19,46,4,47,28},{28,22,6,23,28},{33,16,4,17,30},
            // v27
            {8,122,4,123,30},{22,45,3,46,28},{8,23,26,24,30},{12,15,28,16,30},
            // v28
            {3,117,10,118,30},{3,45,23,46,28},{4,24,31,25,30},{11,15,31,16,30},
            // v29
            {7,116,7,117,30},{21,45,7,46,28},{1,23,37,24,30},{19,15,26,16,30},
            // v30
            {5,115,10,116,30},{19,45,10,46,28},{15,24,25,25,30},{23,15,25,16,30},
            // v31
            {13,115,3,116,30},{2,45,29,46,28},{42,24,1,25,30},{23,15,28,16,30},
            // v32
            {17,115,0,0,30}, {10,45,23,46,28},{10,24,35,25,30},{19,15,35,16,30},
            // v33
            {17,115,1,116,30},{14,45,21,46,28},{29,24,19,25,30},{11,15,46,16,30},
            // v34
            {13,115,6,116,30},{14,46,23,47,28},{44,24,7,25,30},{59,16,1,17,30},
            // v35
            {12,121,7,122,30},{12,45,26,46,28},{39,24,14,25,30},{22,15,41,16,30},
            // v36
            {6,121,14,122,30},{6,45,34,46,28},{46,24,10,25,30},{2,15,64,16,30},
            // v37
            {17,122,4,123,30},{29,45,14,46,28},{49,24,10,25,30},{24,15,46,16,30},
            // v38
            {4,122,18,123,30},{13,45,32,46,28},{48,24,14,25,30},{42,15,32,16,30},
            // v39
            {20,117,4,118,30},{40,45,7,46,28},{43,24,22,25,30},{10,15,67,16,30},
            // v40
            {19,118,6,119,30},{18,45,31,46,28},{34,24,34,25,30},{20,15,61,16,30},
        };

        int eclIndex = java.util.Arrays.asList(QRConstants.ERROR_CORRECTION_LEVELS)
                .indexOf(ecLevel);
        if (eclIndex == -1)
            throw new IllegalArgumentException("Invalid error correction level: " + ecLevel);
        if (version < 1 || version > 40)
            throw new IllegalArgumentException("Invalid version: " + version);

        // Each version has 4 entries (L, M, Q, H) — row = (version-1)*4 + eclIndex
        return table[(version - 1) * 4 + eclIndex];
    }
}