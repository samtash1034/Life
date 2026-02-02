package com.sam.life.service;

import com.sam.life.model.ExpenseRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Excel處理服務
 * 負責讀取上傳的Excel檔案，提取指定欄位資料，並建立新的Excel檔案
 */
@Slf4j
@Service
public class ExcelProcessingService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
     * 建立新的Excel檔案，內容為整理後的費用紀錄
     * @param file 上傳的Excel檔案
     * @param sheetTitle 使用者指定的sheet標題
     * @return 產生的Excel檔案內容
     * @throws IOException 檔案處理錯誤
     */
    public byte[] createProcessedExcel(MultipartFile file, String sheetTitle) throws IOException {
        List<ExpenseRecord> records = readExcelFile(file);
        records.sort(Comparator.comparing(ExpenseRecord::getTime));

        String finalTitle = (sheetTitle != null && !sheetTitle.trim().isEmpty())
                ? sheetTitle.trim()
                : "記帳資料";

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(finalTitle);

            // 建立樣式
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle dateStyle = workbook.createCellStyle();
            short dateFormat = creationHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss");
            dateStyle.setDataFormat(dateFormat);

            // 建立標題列
            Row headerRow = sheet.createRow(0);
            String[] headers = {"記帳時間", "交易金額", "二級分類", "備註"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 寫入資料列
            int rowIndex = 1;
            for (ExpenseRecord record : records) {
                if (record.getTime() == null || record.getAmount() == null) {
                    log.warn("略過缺少必要欄位的紀錄: {}", record);
                    continue;
                }

                Row row = sheet.createRow(rowIndex++);

                Cell timeCell = row.createCell(0);
                timeCell.setCellValue(record.getTime());
                timeCell.setCellStyle(dateStyle);

                Cell amountCell = row.createCell(1);
                amountCell.setCellValue(record.getAmount().doubleValue());

                row.createCell(2).setCellValue(
                        Objects.toString(record.getSecondaryCategory(), "")
                );
                row.createCell(3).setCellValue(
                        Objects.toString(record.getNotes(), "")
                );
            }

            // 調整欄寬以便閱讀
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            log.info("已生成{}筆記錄的Excel檔案", records.size());
            return outputStream.toByteArray();
        }
    }
}
