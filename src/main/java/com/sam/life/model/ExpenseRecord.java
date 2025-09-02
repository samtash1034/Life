package com.sam.life.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
public class ExpenseRecord {
    // B欄：記帳時間
    private LocalDateTime time;
    
    // F欄：交易金額
    private BigDecimal amount;
    
    // H欄：二級分類
    private String secondaryCategory;
    
    // J欄：備註
    private String notes;
}