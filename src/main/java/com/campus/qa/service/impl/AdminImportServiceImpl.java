package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.qa.dto.ExcelImportResult;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.AdminImportService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminImportServiceImpl implements AdminImportService {

    private final QuestionMapper questionMapper;

    public AdminImportServiceImpl(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    @Override
    public ExcelImportResult importQuestionsFromExcel(MultipartFile file, String duplicateStrategy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String strategy = normalizeStrategy(duplicateStrategy);
        ExcelImportResult result = new ExcelImportResult();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                return result;
            }

            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                result.setTotalRows(result.getTotalRows() + 1);

                String q = getCellString(row.getCell(0));
                String a = getCellString(row.getCell(1));
                String c = getCellString(row.getCell(2));

                if (!isValid(q, 5, 200) || !isValid(a, 10, 2000)) {
                    result.setFailedCount(result.getFailedCount() + 1);
                    continue;
                }

                Question exist = questionMapper.selectOne(new LambdaQueryWrapper<Question>()
                        .eq(Question::getQuestion, q));

                if (exist != null) {
                    if ("skip".equals(strategy)) {
                        result.setSkippedCount(result.getSkippedCount() + 1);
                        continue;
                    }
                    exist.setAnswer(a);
                    exist.setCategory(c);
                    exist.setSource("excel");
                    questionMapper.updateById(exist);
                    result.setOverwrittenCount(result.getOverwrittenCount() + 1);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }

                Question item = new Question();
                item.setQuestion(q);
                item.setAnswer(a);
                item.setCategory(c);
                item.setSource("excel");
                item.setHitCount(0L);
                item.setCreateTime(LocalDateTime.now());
                questionMapper.insert(item);
                result.setSuccessCount(result.getSuccessCount() + 1);
            }
            return result;
        } catch (Exception ex) {
            throw new RuntimeException("Excel parse failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public byte[] buildTemplateFile() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("question_template");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("question");
            header.createCell(1).setCellValue("answer");
            header.createCell(2).setCellValue("category");

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("When does the canteen close?");
            sample.createCell(1).setCellValue("Canteen closes at 21:00 on weekdays and 20:30 on weekends.");
            sample.createCell(2).setCellValue("canteen");

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Template generation failed", e);
        }
    }

    private static String getCellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
    }

    private static boolean isValid(String text, int min, int max) {
        int len = text == null ? 0 : text.trim().length();
        return len >= min && len <= max;
    }

    private static String normalizeStrategy(String strategy) {
        if (strategy == null) {
            return "skip";
        }
        String s = strategy.trim().toLowerCase();
        if (!"skip".equals(s) && !"overwrite".equals(s)) {
            throw new IllegalArgumentException("duplicateStrategy only supports skip or overwrite");
        }
        return s;
    }
}
