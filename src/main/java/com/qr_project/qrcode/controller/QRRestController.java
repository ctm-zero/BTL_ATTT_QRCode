package com.qr_project.qrcode.controller;

import com.qr_project.qrcode.service.encoding.DataEncoding;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QRRestController {

    private final DataEncoding dataEncoding;

    public QRRestController(DataEncoding dataEncoding) {
        this.dataEncoding = dataEncoding;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, String> request) {
        String data = request.get("data");
        String version = request.getOrDefault("version", "auto");
        String errorCorrectionLevel = request.getOrDefault("errorCorrectionLevel", "auto");

        // Xác định ecLevel thực tế
        String ecLevel = errorCorrectionLevel.equals("auto") ? "M" : errorCorrectionLevel;

        // Xác định version thực tế
        int resolvedVersion;
        if (version.equals("auto")) {
            resolvedVersion = dataEncoding.versionDetermine(data, ecLevel);
        } else {
            resolvedVersion = Integer.parseInt(version);
        }

        // Xác định mode
        String mode;
        if (dataEncoding.isNumeric(data))
            mode = "NUMERIC";
        else if (dataEncoding.isAlphanumeric(data))
            mode = "ALPHANUMERIC";
        else
            mode = "BYTE";

        // Sinh bitstream
        String bitstream = dataEncoding.generateBitstream(data, version, errorCorrectionLevel);

        Map<String, Object> response = new HashMap<>();
        response.put("bitstream", bitstream);
        response.put("version", resolvedVersion);
        response.put("ecLevel", ecLevel);
        response.put("mode", mode);

        return ResponseEntity.ok(response);
    }


}