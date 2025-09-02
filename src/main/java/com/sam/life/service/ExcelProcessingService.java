package com.sam.life.service;

import com.sam.life.model.ExpenseRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Excel處理服務
 * 負責讀取上傳的Excel檔案，提取指定欄位資料，並建立新的Excel檔案
 */
@Slf4j
@Service
public class ExcelProcessingService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 處理上傳的Excel檔案
     * @param file 上傳的Excel檔案
     * @return 處理後的Excel檔案位元組
     * @throws IOException 檔案讀取錯誤
     */
    public byte[] processExcel(MultipartFile file) throws IOException {
        // 1. 讀取Excel檔案並提取資料
        List<ExpenseRecord> records = readExcelFile(file);
        
        // 2. 按照記帳時間升序排列
        records.sort(Comparator.comparing(ExpenseRecord::getTime));
        
        // 3. 建立新的Excel檔案
        return createExcelFile(records);
    }

    /**
     * 讀取Excel檔案並提取指定欄位的資料
     * @param file 上傳的Excel檔案
     * @return 費用記錄清單
     * @throws IOException 檔案讀取錯誤
     */
    private List<ExpenseRecord> readExcelFile(MultipartFile file) throws IOException {
        List<ExpenseRecord> records = new ArrayList<>();
        
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // 從第2列開始讀取（第1列為標題列）
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                ExpenseRecord record = new ExpenseRecord();
                
                // B欄：記帳時間 (index 1)
                Cell timeCell = row.getCell(1);
                if (timeCell != null) {
                    String timeStr = getCellValueAsString(timeCell);
                    try {
                        record.setTime(LocalDateTime.parse(timeStr, DATE_FORMATTER));
                    } catch (Exception e) {
                        log.warn("Failed to parse date: {}", timeStr);
                        continue;
                    }
                }
                
                // F欄：交易金額 (index 5)
                Cell amountCell = row.getCell(5);
                if (amountCell != null) {
                    String amountStr = getCellValueAsString(amountCell);
                    try {
                        // 移除貨幣符號並轉換為數字
                        amountStr = amountStr.replace("NT$", "").trim();
                        record.setAmount(new BigDecimal(amountStr));
                    } catch (Exception e) {
                        log.warn("Failed to parse amount: {}", amountStr);
                        continue;
                    }
                }
                
                // H欄：二級分類 (index 7)
                Cell secondaryCategoryCell = row.getCell(7);
                if (secondaryCategoryCell != null) {
                    record.setSecondaryCategory(getCellValueAsString(secondaryCategoryCell));
                }
                
                // J欄：備註 (index 9)
                Cell notesCell = row.getCell(9);
                if (notesCell != null) {
                    record.setNotes(getCellValueAsString(notesCell));
                }
                
                records.add(record);
            }
        }
        
        return records;
    }

    /**
     * 將Excel儲存格的值轉換為字串
     * @param cell Excel儲存格
     * @return 儲存格的字串值
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    /**
     * 建立處理後的Excel檔案
     * @param records 費用記錄清單
     * @return Excel檔案位元組
     * @throws IOException 檔案建立錯誤
     */
    private byte[] createExcelFile(List<ExpenseRecord> records) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("處理後的記帳資料");
            
            // 建立標題列
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("記帳時間");    // A欄
            headerRow.createCell(1).setCellValue("交易金額");    // B欄
            headerRow.createCell(2).setCellValue("二級分類");    // C欄
            headerRow.createCell(3).setCellValue("備註");       // D欄
            
            // 填入資料列
            for (int i = 0; i < records.size(); i++) {
                ExpenseRecord record = records.get(i);
                Row row = sheet.createRow(i + 1);
                
                row.createCell(0).setCellValue(record.getTime().format(DATE_FORMATTER));
                row.createCell(1).setCellValue(record.getAmount().doubleValue());
                row.createCell(2).setCellValue(record.getSecondaryCategory());  // C欄：二級分類
                row.createCell(3).setCellValue(record.getNotes());              // D欄：備註
            }
            
            // 自動調整欄位寬度
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * 將費用記錄轉換為Google Sheets格式的二維陣列
     * @param file 上傳的Excel檔案
     * @return Google Sheets格式的資料陣列
     * @throws IOException 檔案讀取錯誤
     */
    public List<List<Object>> convertToGoogleSheetsFormat(MultipartFile file) throws IOException {
        // 1. 讀取Excel檔案並提取資料
        List<ExpenseRecord> records = readExcelFile(file);
        
        // 2. 按照記帳時間升序排列
        records.sort(Comparator.comparing(ExpenseRecord::getTime));
        
        // 3. 轉換為Google Sheets格式
        List<List<Object>> result = new ArrayList<>();
        
        // 添加標題列
        result.add(Arrays.asList("記帳時間", "交易金額", "二級分類", "備註"));
        
        // 添加資料列
        for (ExpenseRecord record : records) {
            List<Object> row = Arrays.asList(
                    record.getTime().format(DATE_FORMATTER),    // A欄：記帳時間
                    record.getAmount().toString(),              // B欄：交易金額
                    record.getSecondaryCategory(),              // C欄：二級分類
                    record.getNotes()                           // D欄：備註
            );
            result.add(row);
        }
        
        log.info("已轉換{}筆記錄為Google Sheets格式", records.size());
        return result;
    }
}