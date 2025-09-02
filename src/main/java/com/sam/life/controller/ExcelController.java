package com.sam.life.controller;

import com.sam.life.service.ExcelProcessingService;
import com.sam.life.service.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel處理控制器
 * 提供Excel檔案上傳和處理的API端點
 */
@Slf4j
@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelProcessingService excelProcessingService;
    private final GoogleDriveService googleDriveService;

    /**
     * 處理Excel檔案並返回處理後的檔案
     * @param file 上傳的Excel檔案
     * @return 處理後的Excel檔案
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> processExcel(@RequestParam("file") MultipartFile file) {
        try {
            // 檢查檔案是否為空
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // 檢查檔案格式是否為Excel
            if (!isExcelFile(file)) {
                return ResponseEntity.badRequest().build();
            }

            // 處理Excel檔案
            byte[] processedExcel = excelProcessingService.processExcel(file);
            
            // 產生下載檔案名稱
            String filename = "processed_expenses_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            // 設定回應標頭
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(processedExcel);

        } catch (IOException e) {
            log.error("處理Excel檔案時發生錯誤", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 新增資料到Google Drive中的現有Excel檔案
     * @param file 上傳的Excel檔案
     * @return 處理結果
     */
    @PostMapping(value = "/upload-to-drive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadToGoogleDrive(
            @RequestParam("file") MultipartFile file) {

        String folderId = "1iGC8wxPTU0FntFwiA0_KlInnKO4Sz8nn";

        try {
            // 檢查檔案是否為空
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("檔案不能為空");
            }

            // 檢查檔案格式是否為Excel
            if (!isExcelFile(file)) {
                return ResponseEntity.badRequest().body("請上傳Excel檔案 (.xlsx 或 .xls)");
            }

            log.info("開始處理Excel檔案並創建到指定資料夾");

            // 轉換為Google Sheets格式並在指定資料夾創建新檔案
            List<List<Object>> sheetsData = excelProcessingService.convertToGoogleSheetsFormat(file);
            String newFileId = googleDriveService.createNewGoogleSheetsFile(sheetsData, folderId);

            return ResponseEntity.ok("成功創建Google Sheets檔案到指定資料夾\\n" +
                "檔案ID: " + newFileId + "\\n" +
                "檔案連結: https://docs.google.com/spreadsheets/d/" + newFileId + "/edit");

        } catch (IOException e) {
            log.error("處理檔案時發生錯誤", e);
            // 提供更詳細的錯誤訊息
            if (e.getMessage().contains("認證檔案")) {
                return ResponseEntity.internalServerError().body("Google認證設定錯誤: " + e.getMessage());
            } else if (e.getMessage().contains("無法新增工作表")) {
                return ResponseEntity.badRequest().body("Google Drive操作失敗: " + e.getMessage());
            }
            return ResponseEntity.internalServerError().body("檔案處理錯誤: " + e.getMessage());
        } catch (GeneralSecurityException e) {
            log.error("Google Drive認證錯誤", e);
            return ResponseEntity.internalServerError().body("Google Drive認證錯誤，請檢查認證檔案設定");
        } catch (Exception e) {
            log.error("未知錯誤", e);
            return ResponseEntity.internalServerError().body("系統錯誤: " + e.getMessage());
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
}