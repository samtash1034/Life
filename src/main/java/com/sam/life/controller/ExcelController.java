package com.sam.life.controller;

import com.sam.life.service.ExcelProcessingService;
import com.sam.life.service.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Excel處理控制器
 * 提供Excel檔案上傳和處理的API端點
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelProcessingService excelProcessingService;
    private final GoogleDriveService googleDriveService;

    /**
     * 顯示Excel上傳頁面
     */
    @GetMapping("/")
    public String uploadPage() {
        return "upload";
    }

    @Value("${google.drive.folder-id}")
    private String folderId;
    
    @Value("${google.drive.target-file-id}")
    private String targetFileId;

    @PostMapping(value = "/api/excel/upload-to-drive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<String> uploadToGoogleDrive(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sheetTitle") String sheetTitle) {

        try {
            // 檢查檔案是否為空
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("檔案不能為空");
            }

            // 檢查檔案格式是否為Excel
            if (!isExcelFile(file)) {
                return ResponseEntity.badRequest().body("請上傳Excel檔案 (.xlsx 或 .xls)");
            }
            
            // 檢查sheet標題是否為空
            if (sheetTitle == null || sheetTitle.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("請輸入Sheet標題");
            }

            log.info("開始處理Excel檔案並直接新增到目標檔案");

            // 轉換為Google Sheets格式，直接在目標檔案中創建新sheet
            List<List<Object>> sheetsData = excelProcessingService.convertToGoogleSheetsFormat(file);
            googleDriveService.addSheetDirectlyToTarget(sheetsData, targetFileId, sheetTitle);

            return ResponseEntity.ok("上傳成功！\n" +
                "已在目標檔案中新增sheet「" + sheetTitle + "」\n" +
                "檔案連結: https://docs.google.com/spreadsheets/d/" + targetFileId);

        } catch (IOException e) {
            log.error("處理檔案時發生錯誤", e);
            // 提供更詳細的錯誤訊息
            if (e.getMessage().contains("認證檔案")) {
                return ResponseEntity.internalServerError().body("Google認證設定錯誤: " + e.getMessage());
            } else if (e.getMessage().contains("無法新增工作表") || e.getMessage().contains("無法複製sheet")) {
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