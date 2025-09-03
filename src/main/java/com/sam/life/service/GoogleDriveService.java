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
import java.util.ArrayList;
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
     * 直接在目標檔案中創建新的sheet並寫入資料
     * @param expenseData 處理後的費用資料
     * @param targetFileId 目標Google Sheets檔案ID
     * @param sheetTitle 新sheet的標題
     * @throws IOException Google Sheets API錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    public void addSheetDirectlyToTarget(List<List<Object>> expenseData, String targetFileId, String sheetTitle) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = createSheetsService();
        
        try {
            // 1. 先取得目標檔案的資訊
            Spreadsheet targetSpreadsheet = sheetsService.spreadsheets()
                    .get(targetFileId)
                    .execute();
            
            int totalSheets = targetSpreadsheet.getSheets().size();
            
            // 2. 創建新的sheet並設定位置
            String actualSheetTitle = (sheetTitle != null && !sheetTitle.trim().isEmpty()) 
                    ? sheetTitle : "記帳資料_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd"));
            
            // 計算倒數第六個位置的索引
            int targetIndex = 0;
            
            // 3. 建立新增sheet的請求
            List<Request> requests = new ArrayList<>();
            
            // 新增sheet請求
            AddSheetRequest addSheetRequest = new AddSheetRequest();
            SheetProperties sheetProperties = new SheetProperties();
            sheetProperties.setTitle(actualSheetTitle);
            sheetProperties.setIndex(targetIndex);
            addSheetRequest.setProperties(sheetProperties);
            
            requests.add(new Request().setAddSheet(addSheetRequest));
            
            // 執行批次更新來新增sheet
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(requests);
            
            BatchUpdateSpreadsheetResponse batchResponse = sheetsService.spreadsheets()
                    .batchUpdate(targetFileId, batchRequest)
                    .execute();
            
            log.info("已在目標檔案中創建新的sheet: {} (位置: {})", actualSheetTitle, targetIndex);
            
            // 4. 將資料寫入新創建的sheet
            String range = actualSheetTitle + "!A1";
            ValueRange valueRange = new ValueRange();
            valueRange.setValues(expenseData);
            
            sheetsService.spreadsheets().values()
                    .update(targetFileId, range, valueRange)
                    .setValueInputOption("RAW")
                    .execute();
            
            log.info("已將{}筆資料寫入新創建的sheet: {}", expenseData.size() - 1, actualSheetTitle);
            
        } catch (IOException e) {
            log.error("直接創建sheet失敗: {}", e.getMessage());
            throw new IOException("無法在目標檔案中創建sheet: " + e.getMessage(), e);
        }
    }

}