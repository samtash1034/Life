package com.sam.life.controller;

import com.sam.life.service.ExcelProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Excel處理控制器
 * 提供Excel檔案上傳和處理的API端點
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelProcessingService excelProcessingService;

    /**
     * 顯示Excel上傳頁面
     */
    @GetMapping("/")
    public String uploadPage() {
        return "upload";
    }

    @PostMapping(value = "/api/excel/upload-to-drive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> downloadProcessedExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sheetTitle", required = false) String sheetTitle) {

        try {
            // 檢查檔案是否為空
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("檔案不能為空".getBytes(StandardCharsets.UTF_8));
            }

            // 檢查檔案格式是否為Excel
            if (!isExcelFile(file)) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("請上傳Excel檔案 (.xlsx 或 .xls)".getBytes(StandardCharsets.UTF_8));
            }
            
            log.info("開始處理Excel檔案並生成可下載的檔案");

            byte[] processedFile = excelProcessingService.createProcessedExcel(file, sheetTitle);
            String downloadName = buildFilename(sheetTitle);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(downloadName))
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(processedFile);

        } catch (IOException e) {
            log.error("處理檔案時發生錯誤", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("檔案處理錯誤: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("未知錯誤", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("系統錯誤: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 檢查檔案是否為Excel格式
     * @param file 上傳的檔案
     * @return 是否為Excel檔案
     */
    private boolean isExcelFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls"));
    }

    private String buildFilename(String sheetTitle) {
        return "cost.xlsx";
    }

    private String buildContentDisposition(String filename) {
        String fallback = filename.replaceAll("[^\\x20-\\x7E]", "_");
        if (fallback.isBlank()) {
            fallback = "records.xlsx";
        }
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }
}
