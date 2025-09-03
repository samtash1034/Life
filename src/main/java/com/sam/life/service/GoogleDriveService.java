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

    /**
     * 創建新的Google Sheets檔案並複製第一個sheet到目標檔案
     * @param expenseData 處理後的費用資料
     * @param parentFolderId 父資料夾ID (可選)
     * @param targetFileId 目標Google Sheets檔案ID，用於複製sheet
     * @param sheetTitle 自定義的sheet標題
     * @return 新建立的Google Sheets檔案ID
     * @throws IOException Google Drive API錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    public String createAndCopySheetToTarget(List<List<Object>> expenseData, String parentFolderId, 
            String targetFileId, String sheetTitle) 
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
            // 使用傳入的sheet標題，如果為空則使用預設值
            String actualSheetTitle = (sheetTitle != null && !sheetTitle.trim().isEmpty()) 
                    ? sheetTitle : "記帳資料_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd"));
            sheetProperties.setTitle(actualSheetTitle);
            sheet.setProperties(sheetProperties);
            spreadsheet.setSheets(Collections.singletonList(sheet));
            
            // 執行創建請求
            Spreadsheet createdSpreadsheet = sheetsService.spreadsheets()
                    .create(spreadsheet)
                    .execute();
            
            String newFileId = createdSpreadsheet.getSpreadsheetId();
            Integer sourceSheetId = createdSpreadsheet.getSheets().get(0).getProperties().getSheetId();
            
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
            
            // 3. 嘗試複製sheet到目標檔案或直接寫入資料
            if (targetFileId != null && !targetFileId.isEmpty()) {
                try {
                    copySheetToTargetFileAtPosition(newFileId, sourceSheetId, targetFileId, actualSheetTitle);
                    
                    // 複製成功後刪除臨時檔案
                    log.info("複製完成，準備刪除臨時檔案: {}", newFileId);
                    deleteFile(newFileId);
                    
                } catch (IOException e) {
                    // 如果複製失敗（可能是Office檔案），嘗試直接寫入資料
                    if (e.getMessage().contains("Office file") || e.getMessage().contains("badRequest")) {
                        log.warn("目標檔案可能是Office格式，嘗試直接寫入資料");
                        appendDataToTargetFile(targetFileId, expenseData);
                        
                        // 資料附加成功後刪除臨時檔案
                        log.info("資料附加完成，準備刪除臨時檔案: {}", newFileId);
                        deleteFile(newFileId);
                        
                    } else {
                        // 如果是其他錯誤，也嘗試刪除臨時檔案（但不拋出刪除錯誤）
                        try {
                            deleteFile(newFileId);
                        } catch (Exception deleteEx) {
                            log.warn("清理臨時檔案失敗，但不影響主要操作: {}", deleteEx.getMessage());
                        }
                        throw e;
                    }
                }
            }
            
            return newFileId;
            
        } catch (IOException e) {
            log.error("Google Sheets API操作失敗: {}", e.getMessage());
            throw new IOException("無法創建Google Sheets檔案: " + e.getMessage(), e);
        }
    }

    /**
     * 複製指定的sheet到目標Google Sheets檔案的特定位置
     * @param sourceFileId 來源檔案ID
     * @param sourceSheetId 來源sheet ID
     * @param targetFileId 目標檔案ID
     * @param sheetTitle 新sheet的標題
     * @throws IOException Google Sheets API錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    public void copySheetToTargetFileAtPosition(String sourceFileId, Integer sourceSheetId, 
            String targetFileId, String sheetTitle) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = createSheetsService();
        
        try {
            // 先取得目標檔案的所有sheets資訊
            Spreadsheet targetSpreadsheet = sheetsService.spreadsheets()
                    .get(targetFileId)
                    .execute();
            
            int totalSheets = targetSpreadsheet.getSheets().size();
            
            // 創建複製sheet的請求
            CopySheetToAnotherSpreadsheetRequest copyRequest = new CopySheetToAnotherSpreadsheetRequest();
            copyRequest.setDestinationSpreadsheetId(targetFileId);
            
            // 執行複製操作
            SheetProperties copiedSheetProperties = sheetsService.spreadsheets().sheets()
                    .copyTo(sourceFileId, sourceSheetId, copyRequest)
                    .execute();
            
            Integer copiedSheetId = copiedSheetProperties.getSheetId();
            
            // 計算倒數第六個位置的索引（考慮新增的sheet）
            int targetIndex = 0;
            
            // 建立批次更新請求來移動sheet和重命名
            List<Request> requests = new ArrayList<>();
            
            // 移動sheet到指定位置
            requests.add(new Request()
                    .setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                            .setProperties(new SheetProperties()
                                    .setSheetId(copiedSheetId)
                                    .setIndex(targetIndex)
                                    .setTitle(sheetTitle))
                            .setFields("index,title")));
            
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(requests);
            
            sheetsService.spreadsheets()
                    .batchUpdate(targetFileId, batchRequest)
                    .execute();
            
            log.info("已成功複製sheet到目標檔案 {} 的倒數第六個位置 (索引: {}, 新sheet ID: {}, 標題: {})", 
                    targetFileId, targetIndex, copiedSheetId, sheetTitle);
            
        } catch (IOException e) {
            log.error("複製sheet失敗: {}", e.getMessage());
            throw new IOException("無法複製sheet到目標檔案: " + e.getMessage(), e);
        }
    }
    
    /**
     * 複製指定的sheet到目標Google Sheets檔案
     * @param sourceFileId 來源檔案ID
     * @param sourceSheetId 來源sheet ID
     * @param targetFileId 目標檔案ID
     * @throws IOException Google Sheets API錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    public void copySheetToTargetFile(String sourceFileId, Integer sourceSheetId, String targetFileId) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = createSheetsService();
        
        try {
            // 創建複製sheet的請求
            CopySheetToAnotherSpreadsheetRequest copyRequest = new CopySheetToAnotherSpreadsheetRequest();
            copyRequest.setDestinationSpreadsheetId(targetFileId);
            
            // 執行複製操作
            SheetProperties copiedSheetProperties = sheetsService.spreadsheets().sheets()
                    .copyTo(sourceFileId, sourceSheetId, copyRequest)
                    .execute();
            
            log.info("已成功複製sheet到目標檔案 {} (新sheet ID: {})", 
                    targetFileId, copiedSheetProperties.getSheetId());
            
        } catch (IOException e) {
            log.error("複製sheet失敗: {}", e.getMessage());
            throw new IOException("無法複製sheet到目標檔案: " + e.getMessage(), e);
        }
    }

    /**
     * 直接將資料附加到目標Google Sheets檔案
     * @param targetFileId 目標檔案ID
     * @param expenseData 要附加的資料
     * @throws IOException Google Sheets API錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    public void appendDataToTargetFile(String targetFileId, List<List<Object>> expenseData) 
            throws IOException, GeneralSecurityException {
        
        Sheets sheetsService = createSheetsService();
        
        try {
            // 先嘗試讀取目標檔案的第一個工作表
            Spreadsheet targetSpreadsheet = sheetsService.spreadsheets()
                    .get(targetFileId)
                    .execute();
            
            String firstSheetName = targetSpreadsheet.getSheets().get(0).getProperties().getTitle();
            
            // 找到最後一行
            String range = firstSheetName + "!A:A";
            ValueRange result = sheetsService.spreadsheets().values()
                    .get(targetFileId, range)
                    .execute();
            
            int lastRow = result.getValues() != null ? result.getValues().size() : 0;
            
            // 如果檔案是空的，加上標題列
            if (lastRow == 0 && expenseData.size() > 0) {
                // 附加所有資料（包含標題）
                ValueRange valueRange = new ValueRange();
                valueRange.setValues(expenseData);
                
                sheetsService.spreadsheets().values()
                        .append(targetFileId, firstSheetName + "!A1", valueRange)
                        .setValueInputOption("RAW")
                        .execute();
                
                log.info("已將{}筆資料（含標題）附加到目標檔案", expenseData.size());
            } else if (expenseData.size() > 1) {
                // 只附加資料列（跳過標題）
                List<List<Object>> dataWithoutHeader = expenseData.subList(1, expenseData.size());
                ValueRange valueRange = new ValueRange();
                valueRange.setValues(dataWithoutHeader);
                
                String appendRange = String.format("%s!A%d", firstSheetName, lastRow + 1);
                sheetsService.spreadsheets().values()
                        .append(targetFileId, appendRange, valueRange)
                        .setValueInputOption("RAW")
                        .execute();
                
                log.info("已將{}筆資料附加到目標檔案第{}行", dataWithoutHeader.size(), lastRow + 1);
            }
            
        } catch (IOException e) {
            log.error("無法附加資料到目標檔案: {}", e.getMessage());
            throw new IOException("無法將資料附加到目標檔案: " + e.getMessage(), e);
        }
    }

    /**
     * 刪除指定的Google Drive檔案
     * @param fileId 要刪除的檔案ID
     * @throws IOException Google Drive API錯誤
     * @throws GeneralSecurityException 安全性錯誤
     */
    public void deleteFile(String fileId) throws IOException, GeneralSecurityException {
        Drive driveService = createDriveService();
        
        try {
            driveService.files().delete(fileId).execute();
            log.info("已成功刪除檔案: {}", fileId);
        } catch (IOException e) {
            log.error("刪除檔案失敗: {}", e.getMessage());
            throw new IOException("無法刪除檔案: " + e.getMessage(), e);
        }
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