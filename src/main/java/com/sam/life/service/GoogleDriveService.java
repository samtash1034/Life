package com.sam.life.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Google Drive服務
 * 負責與Google Drive API進行互動，包括檔案上傳和工作表操作
 * 使用OAuth2 web應用程式認證流程
 */
@Slf4j
@Service
public class GoogleDriveService {

    @Value("${google.credentials.file.path:src/main/resources/google-credentials.json}")
    private String credentialsFilePath;

    @Value("${google.application.name:Life-App}")
    private String applicationName;

    // OAuth2權限範圍
    private static final List<String> SCOPES = List.of(
            SheetsScopes.SPREADSHEETS,
            "https://www.googleapis.com/auth/drive",
            "https://www.googleapis.com/auth/drive.file"
    );

    /**
     * 建立OAuth2認證
     * @return 認證憑據
     * @throws IOException 認證檔案讀取錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    private Credential getCredentials() throws IOException, GeneralSecurityException {
        // 檢查認證檔案是否存在
        java.io.File credFile = new java.io.File(credentialsFilePath);
        if (!credFile.exists()) {
            throw new IOException("Google認證檔案不存在: " + credentialsFilePath + 
                "\n請參考google-credentials.json.example建立認證檔案");
        }

        try {
            // 讀取OAuth2客戶端密鑰
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                    GsonFactory.getDefaultInstance(),
                    new InputStreamReader(new FileInputStream(credentialsFilePath))
            );

            // 建立OAuth2授權流程
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientSecrets,
                    SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                    .setAccessType("offline")
                    .build();

            // 建立本地伺服器接收器來處理授權回調
            // 使用範圍 8080-8090 來避免衝突
            LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                    .setPort(8889)
                    .build();

            // 執行授權流程
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        } catch (IOException e) {
            throw new IOException("讀取Google認證檔案失敗: " + e.getMessage() + 
                "\n請確認認證檔案格式正確，參考google-credentials.json.example", e);
        }
    }

    /**
     * 建立Google Drive服務客戶端
     * @return Drive服務實例
     * @throws IOException 認證檔案讀取錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    private Drive createDriveService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(HTTP_TRANSPORT, GsonFactory.getDefaultInstance(), getCredentials())
                .setApplicationName(applicationName)
                .build();
    }

    /**
     * 建立Google Sheets服務客戶端
     * @return Sheets服務實例
     * @throws IOException 認證檔案讀取錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    private Sheets createSheetsService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Sheets.Builder(HTTP_TRANSPORT, GsonFactory.getDefaultInstance(), getCredentials())
                .setApplicationName(applicationName)
                .build();
    }

    /**
     * 創建新的Google Sheets檔案並寫入資料
     * @param expenseData 處理後的費用資料
     * @param parentFolderId 父資料夾ID (可選)
     * @return 新建立的Google Sheets檔案ID
     * @throws IOException Google Drive API錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    public String createNewGoogleSheetsFile(List<List<Object>> expenseData, String parentFolderId) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = createSheetsService();
        
        try {
            // 1. 創建新的Google Sheets檔案
            String fileName = "記帳資料_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            Spreadsheet spreadsheet = new Spreadsheet();
            SpreadsheetProperties properties = new SpreadsheetProperties();
            properties.setTitle(fileName);
            spreadsheet.setProperties(properties);
            
            // 建立工作表
            Sheet sheet = new Sheet();
            SheetProperties sheetProperties = new SheetProperties();
            sheetProperties.setTitle("記帳資料");
            sheet.setProperties(sheetProperties);
            spreadsheet.setSheets(Collections.singletonList(sheet));
            
            // 執行創建請求
            Spreadsheet createdSpreadsheet = sheetsService.spreadsheets()
                    .create(spreadsheet)
                    .execute();
            
            String newFileId = createdSpreadsheet.getSpreadsheetId();
            
            // 如果指定了父資料夾，將檔案移動到該資料夾
            if (parentFolderId != null && !parentFolderId.isEmpty()) {
                Drive driveService = createDriveService();
                driveService.files().update(newFileId, null)
                        .setAddParents(parentFolderId)
                        .execute();
                log.info("已將檔案移動到指定資料夾: {}", parentFolderId);
            }
            
            log.info("已創建新的Google Sheets檔案: {} (ID: {})", fileName, newFileId);
            
            // 2. 將資料寫入新檔案
            ValueRange valueRange = new ValueRange();
            valueRange.setValues(expenseData);
            
            sheetsService.spreadsheets().values()
                    .update(newFileId, "A1", valueRange)
                    .setValueInputOption("RAW")
                    .execute();
            
            log.info("已將{}筆資料寫入新的Google Sheets檔案", expenseData.size() - 1);
            
            return newFileId;
            
        } catch (IOException e) {
            log.error("Google Sheets API操作失敗: {}", e.getMessage());
            throw new IOException("無法創建Google Sheets檔案: " + e.getMessage(), e);
        }
    }

}