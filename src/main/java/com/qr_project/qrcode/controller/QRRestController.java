package com.qr_project.qrcode.controller;

import com.qr_project.qrcode.service.encoding.DataEncoding;
import com.qr_project.qrcode.service.error.ErrorCorrection;
import com.qr_project.qrcode.service.layout.MatrixGenerator;
import com.qr_project.qrcode.service.layout.MatrixMask;
import com.qr_project.qrcode.service.layout.FormatAndVersion;
import com.qr_project.qrcode.service.layout.QRImageRenderer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QRRestController {

    private final DataEncoding dataEncoding;
    private final ErrorCorrection errorCorrection;
    private final MatrixGenerator matrixGenerator;
    private final FormatAndVersion formatAndVersion;
    private final QRImageRenderer imageRenderer;

    public QRRestController(DataEncoding dataEncoding,
            ErrorCorrection errorCorrection,
            MatrixGenerator matrixGenerator,
            FormatAndVersion formatAndVersion,
            QRImageRenderer imageRenderer) {
        this.dataEncoding = dataEncoding;
        this.errorCorrection = errorCorrection;
        this.matrixGenerator = matrixGenerator;
        this.formatAndVersion = formatAndVersion;
        this.imageRenderer = imageRenderer;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, String> request) {
        try {
            String data = request.get("data");
            String version = request.getOrDefault("version", "auto");
            String errorCorrectionLevel = request.getOrDefault("errorCorrectionLevel", "auto");

            // Resolve ecLevel
            String ecLevel = errorCorrectionLevel.equals("auto") ? "M" : errorCorrectionLevel;

            // Resolve version
            int resolvedVersion = version.equals("auto")
                    ? dataEncoding.versionDetermine(data, ecLevel)
                    : Integer.parseInt(version);

            // Xác định mode
            String mode;
            if (dataEncoding.isNumeric(data))
                mode = "NUMERIC";
            else if (dataEncoding.isAlphanumeric(data))
                mode = "ALPHANUMERIC";
            else
                mode = "BYTE";

            // Encode -> bitstream
            String dataBitstream = dataEncoding.generateBitstream(data, String.valueOf(resolvedVersion), errorCorrectionLevel);

            // Reed-Solomon EC + interleave + remainder bits
            String finalBitstream = errorCorrection.generateErrorCorrection(
                    dataBitstream, resolvedVersion, ecLevel);

            // Tạo ma trận
            int[][] matrix = matrixGenerator.dataBitsPlacement(resolvedVersion, finalBitstream);
            MatrixMask maskApplier = new MatrixMask();
            MatrixMask.MaskResult maskResult = maskApplier.applyMask(matrix, resolvedVersion);
            int[][] maskedMatrix = maskResult.matrix();
            int maskPattern = maskResult.maskPattern();
            formatAndVersion.placeFormatAndVersionInfo(maskedMatrix, ecLevel, maskPattern, resolvedVersion);

            // Bước 4: render -> Base64 PNG
            String base64Image = imageRenderer.renderToBase64(maskedMatrix);

            // Response
            Map<String, Object> response = new HashMap<>();
            response.put("version", resolvedVersion);
            response.put("ecLevel", ecLevel);
            response.put("mode", mode);
            response.put("bitstream", finalBitstream);
            response.put("maskPattern", maskPattern);
            response.put("image", "data:image/png;base64," + base64Image);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}