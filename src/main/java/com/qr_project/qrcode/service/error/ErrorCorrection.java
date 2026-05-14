package com.qr_project.qrcode.service.error;

import com.qr_project.qrcode.utils.QRConstants;
import com.qr_project.qrcode.utils.QRTable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reed-Solomon error correction for QR codes.
 *
 * Pipeline:
 * 1. splitIntoBlocks() — split data codewords into groups/blocks
 * 2. generateECForBlock() — RS division per block
 * 3. interleaveBlocks() — interleave data blocks, then EC blocks
 * 4. generateErrorCorrection() — full pipeline, returns final bitstream
 */
@Service
public class ErrorCorrection {

    // ─────────────────────────────────────────────────────────────
    // GF(256) tables (primitive polynomial 0x11D = 285)
    // ─────────────────────────────────────────────────────────────
    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0)
                x ^= QRConstants.PRIMITIVE_POLYNOMIAL;
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
        if (a == 0 || b == 0)
            return 0;
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
        int[] g = { 1 };
        for (int i = 0; i < numECCodewords; i++) {
            g = gfPolyMultiply(g, new int[] { 1, GF_EXP[i] });
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
            if (coef == 0)
                continue;
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
     * @param codewords flat array of data codewords
     * @param entry     EC table entry: [g1Blocks, g1CW, g2Blocks, g2CW, ecPerBlock]
     * @return list of blocks (each block is an int[])
     */
    public List<int[]> splitIntoBlocks(int[] codewords, int[] entry) {
        int g1Blocks = entry[0];
        int g1CWPerBlock = entry[1];
        int g2Blocks = entry[2];
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
     * @param dataBitstream output of DataEncoding.eightBitCodewords()
     * @param version       QR version (1–40)
     * @param ecLevel       "L", "M", "Q", or "H"
     * @return interleaved data + EC codewords as a binary string
     */
    public String generateErrorCorrection(String dataBitstream, int version, String ecLevel) {
        // Convert bitstream to codeword array
        int[] dataCodewords = bitstreamToCodewords(dataBitstream);

        // Look up EC table — dùng static method, nhất quán với QRTable
        int[] entry = QRTable.getECTableEntry(version, ecLevel);
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
        int[] interleavedEC = interleaveBlocks(ecBlocks);

        // Concatenate and convert back to bitstream
        StringBuilder sb = new StringBuilder();
        for (int cw : interleavedData)
            sb.append(toBinary8(cw));
        for (int cw : interleavedEC)
            sb.append(toBinary8(cw));

        // Append remainder bits (all zeros) — ISO 18004 Table 1
        int remainderBits = QRConstants.REMAINDER_BITS[version - 1];
        for (int i = 0; i < remainderBits; i++)
            sb.append('0');

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
        for (int i = b.length(); i < 8; i++)
            sb.append('0');
        sb.append(b);
        return sb.toString();
    }
}