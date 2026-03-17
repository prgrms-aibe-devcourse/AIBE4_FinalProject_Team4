package kr.java.documind.domain.archive.vector.infrastructure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.java.documind.domain.archive.vector.model.enums.ExtractedContentType;
import kr.java.documind.global.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.TextChunk;
import technology.tabula.TextElement;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

@Slf4j
public class PdfDocumentReader implements DocumentReader {

    private static final int MIN_TABLE_DATA_ROWS = 2;

    private final Path pdfPath;
    private final TableRefiner tableRefiner = new TableRefiner();

    public PdfDocumentReader(Path pdfPath) {
        this.pdfPath = pdfPath;
    }

    @Override
    public List<Document> get() {
        List<Document> documents = new ArrayList<>();
        int textCount = 0;
        int tableCount = 0;

        try (PDDocument pdDocument = Loader.loadPDF(pdfPath.toFile());
                ObjectExtractor objectExtractor = new ObjectExtractor(pdDocument)) {
            int totalPages = pdDocument.getNumberOfPages();
            PDFTextStripper textStripper = new PDFTextStripper();

            for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
                int pageNumber = pageIdx + 1;

                try {
                    Page tabulaPage = objectExtractor.extract(pageNumber);
                    List<Table> validTables = extractTablesWithBestAlgorithm(tabulaPage);

                    TableExtractionResult tableResult =
                            extractTables(tabulaPage, validTables, pageNumber, totalPages);
                    documents.addAll(tableResult.documents);
                    tableCount += tableResult.documents.size();

                    Document textDoc =
                            extractText(
                                    textStripper,
                                    pdDocument,
                                    pageNumber,
                                    totalPages,
                                    tableResult.refinedTables);
                    if (textDoc != null) {
                        documents.add(textDoc);
                        textCount++;
                    }
                } catch (Exception e) {
                    log.warn("페이지 {} 처리 실패, 건너뜀 - {}", pageNumber, e.getMessage());
                }
            }

            log.info("PDF 추출 완료 - TEXT: {}, TABLE: {}", textCount, tableCount);
        } catch (IOException e) {
            throw new StorageException("PDF 파일 읽기 실패: " + pdfPath.getFileName(), e);
        }

        return documents;
    }

    // ── 듀얼 알고리즘 비교 ──

    private List<Table> extractTablesWithBestAlgorithm(Page tabulaPage) {
        List<Table> spreadsheetTables =
                filterValidTables(new SpreadsheetExtractionAlgorithm().extract(tabulaPage));
        List<Table> basicTables =
                filterValidTables(new BasicExtractionAlgorithm().extract(tabulaPage));

        if (spreadsheetTables.isEmpty()) {
            return basicTables;
        }
        if (basicTables.isEmpty()) {
            return spreadsheetTables;
        }

        TableStructureScore spreadsheetScore = calculateStructureScore(spreadsheetTables);
        TableStructureScore basicScore = calculateStructureScore(basicTables);
        if (basicScore.isBetterThan(spreadsheetScore)) {
            return basicTables;
        }
        if (spreadsheetScore.isBetterThan(basicScore)) {
            return spreadsheetTables;
        }

        double spreadsheetDensity = calculateCellDensity(spreadsheetTables);
        double basicDensity = calculateCellDensity(basicTables);

        if (basicDensity > spreadsheetDensity) {
            log.debug(
                    "BasicExtractionAlgorithm 선택 (밀도 {} > Spreadsheet {})",
                    basicDensity,
                    spreadsheetDensity);
            return basicTables;
        }
        return spreadsheetTables;
    }

    private TableStructureScore calculateStructureScore(List<Table> tables) {
        int tableCount = tables.size();
        int totalRows = 0;
        int totalCells = 0;
        int maxCols = 0;

        for (Table table : tables) {
            totalRows += table.getRowCount();
            totalCells += table.getRowCount() * table.getColCount();
            maxCols = Math.max(maxCols, table.getColCount());
        }

        return new TableStructureScore(maxCols, totalCells, totalRows, tableCount);
    }

    private double calculateCellDensity(List<Table> tables) {
        int totalCells = 0;
        int nonEmptyCells = 0;
        for (Table table : tables) {
            for (List<RectangularTextContainer> row : table.getRows()) {
                totalCells += row.size();
                for (RectangularTextContainer cell : row) {
                    if (!cell.getText().trim().isEmpty()) {
                        nonEmptyCells++;
                    }
                }
            }
        }
        return totalCells == 0 ? 0.0 : (double) nonEmptyCells / totalCells;
    }

    // ── 표 유효성 필터 ──

    private List<Table> filterValidTables(List<Table> rawTables) {
        List<Table> validTables = rawTables.stream().filter(this::hasEnoughDataRows).toList();
        int skipped = rawTables.size() - validTables.size();
        if (skipped > 0) {
            log.debug("헤더만 감지된 표 {} 건 제외 (데이터 행 부족)", skipped);
        }
        return validTables;
    }

    private boolean hasEnoughDataRows(Table table) {
        long nonEmptyRows =
                table.getRows().stream()
                        .filter(
                                row ->
                                        row.stream()
                                                .anyMatch(cell -> !cell.getText().trim().isEmpty()))
                        .count();
        return nonEmptyRows >= MIN_TABLE_DATA_ROWS;
    }

    // ── 표 추출 (TableRefiner 위임) ──

    private record TableExtractionResult(List<Document> documents, List<Table> refinedTables) {}

    private TableExtractionResult extractTables(
            Page page, List<Table> tables, int pageNumber, int totalPages) {
        List<Document> tableDocs = new ArrayList<>();
        List<Table> refinedTables = new ArrayList<>();

        for (int i = 0; i < tables.size(); i++) {
            String tableText = tableRefiner.refine(buildGridWithLeftLabels(page, tables.get(i)));
            if (tableText.isEmpty()) {
                continue;
            }

            tableDocs.add(
                    createDocument(tableText, ExtractedContentType.TABLE, pageNumber, totalPages));
            refinedTables.add(tables.get(i));
            log.debug("표 추출 - page {}, table {}", pageNumber, i + 1);
        }

        return new TableExtractionResult(tableDocs, refinedTables);
    }

    private List<List<String>> buildGridWithLeftLabels(Page page, Table table) {
        List<List<String>> baseGrid = tableRefiner.extractRawGrid(table);
        if (baseGrid.isEmpty()) {
            return baseGrid;
        }

        float firstColumnLeft = findFirstColumnLeft(table);
        List<TextChunk> leftCandidates =
                TextElement.mergeWords(page.getText()).stream()
                        .filter(text -> isLeftSideLabel(text, table, firstColumnLeft))
                        .sorted(
                                Comparator.comparing(TextChunk::getTop)
                                        .thenComparing(TextChunk::getLeft))
                        .toList();

        if (leftCandidates.isEmpty()) {
            return baseGrid;
        }

        // grid의 column 0에 이미 라벨이 존재하면 enrichment 건너뛰기 (중복 방지)
        Set<String> existingColumn0Values = new HashSet<>();
        for (List<String> row : baseGrid) {
            if (!row.isEmpty()) {
                String val = normalizeText(row.get(0));
                if (!val.isEmpty()) {
                    existingColumn0Values.add(val);
                }
            }
        }

        boolean allLabelsAlreadyInGrid =
                leftCandidates.stream()
                        .allMatch(
                                candidate ->
                                        existingColumn0Values.contains(
                                                normalizeText(candidate.getText())));
        if (allLabelsAlreadyInGrid) {
            return baseGrid;
        }

        int headerRowCount = detectHeaderRowCount(baseGrid);
        Map<Integer, String> groupLabelByRow =
                buildGroupLabelMap(baseGrid, leftCandidates, headerRowCount);
        List<List<String>> enrichedGrid = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < baseGrid.size(); rowIndex++) {
            String rowLabel = groupLabelByRow.getOrDefault(rowIndex, "");
            List<String> enrichedRow =
                    reshapeRow(baseGrid.get(rowIndex), rowIndex, rowLabel, headerRowCount);
            enrichedGrid.add(enrichedRow);
        }

        return enrichedGrid;
    }

    private int detectHeaderRowCount(List<List<String>> grid) {
        if (grid.size() < 2) {
            return 0;
        }

        return isHeaderLike(grid.get(0)) && isHeaderLike(grid.get(1)) ? 2 : 0;
    }

    private boolean isHeaderLike(List<String> row) {
        long textCells =
                row.stream()
                        .map(this::normalizeText)
                        .filter(text -> !text.isEmpty())
                        .filter(text -> !text.matches("-?[\\d,.]+|·|-"))
                        .count();
        return textCells >= 2;
    }

    private List<String> reshapeRow(
            List<String> row, int rowIndex, String rowLabel, int headerRowCount) {
        List<String> reshaped = new ArrayList<>();

        if (headerRowCount == 2 && rowIndex == 0 && !row.isEmpty()) {
            reshaped.add(row.get(0));
            reshaped.add("");
            reshaped.addAll(row.subList(1, row.size()));
            return reshaped;
        }

        if (headerRowCount == 2 && rowIndex == 1) {
            reshaped.add("");
            reshaped.addAll(row);
            return reshaped;
        }

        reshaped.add(rowLabel);
        reshaped.addAll(row);
        return reshaped;
    }

    private Map<Integer, String> buildGroupLabelMap(
            List<List<String>> baseGrid, List<TextChunk> leftCandidates, int headerRowCount) {
        java.util.LinkedHashMap<Integer, String> groupLabelByRow = new java.util.LinkedHashMap<>();
        if (leftCandidates.isEmpty()) {
            return groupLabelByRow;
        }

        int currentRow = headerRowCount;
        for (TextChunk candidate : leftCandidates) {
            currentRow = findNextDataRow(baseGrid, currentRow);
            if (currentRow < 0) {
                break;
            }

            groupLabelByRow.put(currentRow, normalizeText(candidate.getText()));

            int subtotalRow = findNextSubtotalRow(baseGrid, currentRow + 1);
            if (subtotalRow >= 0) {
                currentRow = subtotalRow + 1;
            } else {
                currentRow = currentRow + 1;
            }
        }

        return groupLabelByRow;
    }

    private int findNextDataRow(List<List<String>> baseGrid, int startRow) {
        for (int i = startRow; i < baseGrid.size(); i++) {
            List<String> row = baseGrid.get(i);
            if (row.stream().anyMatch(cell -> !normalizeText(cell).isEmpty())) {
                return i;
            }
        }
        return -1;
    }

    private int findNextSubtotalRow(List<List<String>> baseGrid, int startRow) {
        for (int i = startRow; i < baseGrid.size(); i++) {
            if (isSubtotalRow(baseGrid.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSubtotalRow(List<String> row) {
        return !row.isEmpty() && "소계".equals(normalizeText(row.get(0)));
    }

    private float findFirstColumnLeft(Table table) {
        float firstColumnLeft = Float.MAX_VALUE;
        for (List<RectangularTextContainer> row : table.getRows()) {
            if (!row.isEmpty()) {
                firstColumnLeft = Math.min(firstColumnLeft, row.get(0).getLeft());
            }
        }
        return firstColumnLeft == Float.MAX_VALUE ? table.getLeft() : firstColumnLeft;
    }

    private boolean isLeftSideLabel(TextChunk text, Table table, float firstColumnLeft) {
        String normalized = normalizeText(text.getText());
        if (normalized.isEmpty()) {
            return false;
        }
        double centerX = text.getLeft() + (text.getWidth() / 2d);
        if (centerX >= firstColumnLeft) {
            return false;
        }
        if (normalized.length() <= 1) {
            return false;
        }
        return text.getTop() >= table.getTop() - 5f && text.getBottom() <= table.getBottom() + 5f;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace("\r", " ").replaceAll("\\s+", " ").trim();
    }

    private record TableStructureScore(int maxCols, int totalCells, int totalRows, int tableCount) {
        private boolean isBetterThan(TableStructureScore other) {
            if (maxCols != other.maxCols) {
                return maxCols > other.maxCols;
            }
            if (totalCells != other.totalCells) {
                return totalCells > other.totalCells;
            }
            if (totalRows != other.totalRows) {
                return totalRows > other.totalRows;
            }
            return tableCount > other.tableCount;
        }
    }

    // ── 텍스트 추출 ──

    private Document extractText(
            PDFTextStripper stripper,
            PDDocument document,
            int pageNumber,
            int totalPages,
            List<Table> tables)
            throws IOException {

        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        String text = stripper.getText(document).trim();

        if (text.isEmpty()) {
            return null;
        }

        if (!tables.isEmpty()) {
            text = removeTableTextFromPageText(text, tables);
            if (text.isEmpty()) {
                return null;
            }
        }

        return createDocument(text, ExtractedContentType.TEXT, pageNumber, totalPages);
    }

    private Document createDocument(
            String content, ExtractedContentType contentType, int pageNumber, int totalPages) {
        return new Document(
                content,
                Map.of(
                        "content_type", contentType.name(),
                        "page_number", String.valueOf(pageNumber),
                        "total_pages", String.valueOf(totalPages)));
    }

    // ── 페이지 텍스트에서 표 데이터 행 제거 (행 단위 지문 매칭) ──

    private String removeTableTextFromPageText(String pageText, List<Table> tables) {
        Set<String> tableRowFingerprints = buildTableRowFingerprints(tables);
        if (tableRowFingerprints.isEmpty()) {
            return pageText;
        }

        List<String> filteredLines = new ArrayList<>();
        for (String line : pageText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String fingerprint = trimmed.replaceAll("\\s+", "");
            if (!tableRowFingerprints.contains(fingerprint)) {
                filteredLines.add(trimmed);
            }
        }

        return String.join("\n", filteredLines).trim();
    }

    private Set<String> buildTableRowFingerprints(List<Table> tables) {
        Set<String> fingerprints = new HashSet<>();

        for (Table table : tables) {
            for (List<RectangularTextContainer> row : table.getRows()) {
                long numericCellCount =
                        row.stream()
                                .map(cell -> cell.getText().trim())
                                .filter(text -> text.matches("-?\\d+|·"))
                                .count();
                if (numericCellCount < 2) {
                    continue;
                }

                StringBuilder sb = new StringBuilder();
                for (RectangularTextContainer cell : row) {
                    sb.append(cell.getText().replace("\r", " "));
                }
                String fingerprint = sb.toString().replaceAll("\\s+", "");
                if (!fingerprint.isEmpty()) {
                    fingerprints.add(fingerprint);
                }
            }
        }

        return fingerprints;
    }
}
