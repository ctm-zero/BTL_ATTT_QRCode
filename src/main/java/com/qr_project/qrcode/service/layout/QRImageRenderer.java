package com.qr_project.qrcode.service.layout;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Renders a completed QR matrix (int[][]) to a PNG image.
 *
 * Giá trị cell trong matrix:
 *   1  = dark module  -> màu đen
 *   0  = light module -> màu trắng
 *  -1  = chưa gán    -> coi là trắng (không nên xuất hiện trong matrix hoàn chỉnh)
 */
@Service
public class QRImageRenderer {

    private static final int DEFAULT_MODULE_SIZE = 10; // px mỗi module
    private static final int DEFAULT_QUIET_ZONE  = 4;  // số module viền trắng (ISO yêu cầu tối thiểu 4)

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Render matrix -> PNG bytes.
     *
     * @param matrix     ma trận QR hoàn chỉnh từ MatrixGenerator
     * @param moduleSize pixels mỗi module
     * @param quietZone  số module viền trắng xung quanh
     */
    public byte[] renderToPng(int[][] matrix, int moduleSize, int quietZone) throws IOException {
        BufferedImage image = buildImage(matrix, moduleSize, quietZone);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        }
    }

    /** Render với cài đặt mặc định (10px/module, quiet zone 4). */
    public byte[] renderToPng(int[][] matrix) throws IOException {
        return renderToPng(matrix, DEFAULT_MODULE_SIZE, DEFAULT_QUIET_ZONE);
    }

    /**
     * Render → Base64 PNG string, dùng trực tiếp cho:
     *   <img src="data:image/png;base64,{result}">
     */
    public String renderToBase64(int[][] matrix, int moduleSize, int quietZone) throws IOException {
        return Base64.getEncoder().encodeToString(renderToPng(matrix, moduleSize, quietZone));
    }

    /** Render với cài đặt mặc định, trả Base64 string. */
    public String renderToBase64(int[][] matrix) throws IOException {
        return renderToBase64(matrix, DEFAULT_MODULE_SIZE, DEFAULT_QUIET_ZONE);
    }

    // ─────────────────────────────────────────────────────────────
    // Core rendering
    // ─────────────────────────────────────────────────────────────

    private BufferedImage buildImage(int[][] matrix, int moduleSize, int quietZone) {
        int matrixSize   = matrix.length;
        int totalModules = matrixSize + quietZone * 2;
        int imagePx      = totalModules * moduleSize;

        BufferedImage image = new BufferedImage(imagePx, imagePx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Tắt antialiasing — QR cần pixel sắc nét, không blur cạnh
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);

        // Nền trắng toàn ảnh
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imagePx, imagePx);

        // Vẽ từng module đen
        g.setColor(Color.BLACK);
        for (int row = 0; row < matrixSize; row++) {
            for (int col = 0; col < matrixSize; col++) {
                if (matrix[row][col] == 1) {
                    int x = (col + quietZone) * moduleSize;
                    int y = (row + quietZone) * moduleSize;
                    g.fillRect(x, y, moduleSize, moduleSize);
                }
                // 0 và -1 đã được phủ bởi nền trắng, bỏ qua
            }
        }

        g.dispose();
        return image;
    }
}