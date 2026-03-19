package kr.java.documind.domain.archive.vector.infrastructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;

public class TableRefiner {

    private static final int MIN_NON_EMPTY_CELLS = 2;

    public String refine(Table table) {
        List<List<String>> grid = extractRawGrid(table);
        return refine(grid);
    }

    public String refine(List<List<String>> grid) {
        grid = expandMergedCells(grid);
        grid = normalizeCells(grid);
        grid = removeEmptyRows(grid);
        grid = removeEmptyColumns(grid);
        grid = normalizeColumnCount(grid);
        grid = cleanupSparseTable(grid);
        grid = distributeStackedValues(grid);
        grid = collapseHeaderRows(grid);
        grid = removeStandaloneGroupRows(grid);
        grid = removeEmptyRows(grid);
        grid = removeEmptyColumns(grid);

        if (isFragmentedTextTable(grid)) {
            return joinFragmentedRows(grid);
        }

        if (!shouldPreserveSparseHeader(grid)) {
            fillMergedCells(grid);
        }

        if (!isMeaningful(grid)) {
            return "";
        }

        return toMarkdown(grid, detectHeader(grid));
    }

    // ── 1. Raw Grid 추출 ──

    List<List<String>> extractRawGrid(Table table) {
        List<List<String>> grid = new ArrayList<>();
        for (List<RectangularTextContainer> row : table.getRows()) {
            List<String> gridRow = new ArrayList<>();
            for (RectangularTextContainer cell : row) {
                gridRow.add(cell.getText());
            }
            grid.add(gridRow);
        }
        return grid;
    }

    // ── 2. 병합 셀 분리 (\r 기반, normalizeCells 이전에 실행) ──

    List<List<String>> expandMergedCells(List<List<String>> grid) {
        List<List<String>> result = new ArrayList<>();

        for (List<String> row : grid) {
            boolean allEmpty = row.stream().allMatch(cell -> cell.trim().isEmpty());
            if (allEmpty) {
                continue;
            }

            String[][] splitCells =
                    row.stream().map(cell -> cell.split("\\R")).toArray(String[][]::new);
            int maxLines = 1;
            for (String[] sc : splitCells) {
                maxLines = Math.max(maxLines, sc.length);
            }
            long multiLineCellCount = 0;
            for (String[] sc : splitCells) {
                if (sc.length > 1) {
                    multiLineCellCount++;
                }
            }

            if (maxLines > 1 && multiLineCellCount > 1) {
                // 병합 셀: 여러 셀이 다중행 → 행 분리
                for (int line = 0; line < maxLines; line++) {
                    List<String> newRow = new ArrayList<>();
                    for (String[] splitCell : splitCells) {
                        if (splitCell.length > 1 && splitCell.length == maxLines) {
                            newRow.add(splitCell[line].trim());
                        } else if (splitCell.length > 1 && splitCell.length > maxLines / 2) {
                            // 줄 수 근접: 가능한 값 분배, 초과분은 빈 셀
                            newRow.add(line < splitCell.length ? splitCell[line].trim() : "");
                        } else if (splitCell.length > 1) {
                            newRow.add(line == 0 ? joinLines(splitCell) : "");
                        } else {
                            newRow.add(line == 0 ? splitCell[0].trim() : "");
                        }
                    }
                    result.add(newRow);
                }
            } else if (maxLines > 1) {
                // 텍스트 줄바꿈: 1개 셀만 다중행 → 공백으로 합침
                List<String> joined = new ArrayList<>();
                for (String[] splitCell : splitCells) {
                    joined.add(joinLines(splitCell));
                }
                result.add(joined);
            } else {
                List<String> trimmedRow = new ArrayList<>();
                for (String cell : row) {
                    trimmedRow.add(cell.trim());
                }
                result.add(trimmedRow);
            }
        }

        return result;
    }

    // ── 3. 셀 정규화 (\r → 공백, 공백 압축) ──

    List<List<String>> normalizeCells(List<List<String>> grid) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> row : grid) {
            List<String> normalizedRow = new ArrayList<>();
            for (String cell : row) {
                String normalized = cell.replace("\r", " ").replaceAll("\\s+", " ").trim();
                normalizedRow.add(normalized);
            }
            result.add(normalizedRow);
        }
        return result;
    }

    // ── 4. 빈 행 제거 ──

    List<List<String>> removeEmptyRows(List<List<String>> grid) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> row : grid) {
            boolean allEmpty = row.stream().allMatch(String::isEmpty);
            if (!allEmpty) {
                result.add(row);
            }
        }
        return result;
    }

    // ── 5. 빈 열 제거 ──

    List<List<String>> removeEmptyColumns(List<List<String>> grid) {
        if (grid.isEmpty()) {
            return grid;
        }

        int maxCols = grid.stream().mapToInt(List::size).max().orElse(0);
        if (maxCols == 0) {
            return grid;
        }

        List<Integer> nonEmptyColIndices = new ArrayList<>();
        for (int col = 0; col < maxCols; col++) {
            boolean allEmpty = true;
            for (List<String> row : grid) {
                if (col < row.size() && !row.get(col).isEmpty()) {
                    allEmpty = false;
                    break;
                }
            }
            if (!allEmpty) {
                nonEmptyColIndices.add(col);
            }
        }

        List<List<String>> result = new ArrayList<>();
        for (List<String> row : grid) {
            List<String> filteredRow = new ArrayList<>();
            for (int colIdx : nonEmptyColIndices) {
                filteredRow.add(colIdx < row.size() ? row.get(colIdx) : "");
            }
            result.add(filteredRow);
        }
        return result;
    }

    // ── 6. 열 수 정규화 (최빈값 기준) ──

    List<List<String>> normalizeColumnCount(List<List<String>> grid) {
        if (grid.isEmpty()) {
            return grid;
        }

        int modeColCount = findModeColumnCount(grid);
        if (modeColCount == 0) {
            return grid;
        }

        List<List<String>> result = new ArrayList<>();
        for (List<String> row : grid) {
            List<String> normalizedRow = new ArrayList<>(row);
            while (normalizedRow.size() < modeColCount) {
                normalizedRow.add("");
            }
            if (normalizedRow.size() > modeColCount) {
                normalizedRow = new ArrayList<>(normalizedRow.subList(0, modeColCount));
            }
            result.add(normalizedRow);
        }
        return result;
    }

    // ── 7. 스파스 테이블 정리 (페이지 푸터 제거 + 빈 열 압축) ──

    private List<List<String>> cleanupSparseTable(List<List<String>> grid) {
        if (grid.size() < 3) {
            return grid;
        }

        List<List<String>> withoutFooter = new ArrayList<>();
        for (List<String> row : grid) {
            if (!isPageFooterRow(row)) {
                withoutFooter.add(new ArrayList<>(row));
            }
        }

        int dataStart = findDataStartRow(withoutFooter);
        if (dataStart <= 0 || dataStart >= withoutFooter.size()) {
            return withoutFooter;
        }

        List<Integer> keepColumns = findColumnsWithData(withoutFooter, dataStart);
        if (keepColumns.isEmpty()) {
            return withoutFooter;
        }

        int originalColumnCount = withoutFooter.stream().mapToInt(List::size).max().orElse(0);
        if (keepColumns.size() == originalColumnCount) {
            return withoutFooter;
        }

        return compactSparseColumns(withoutFooter, keepColumns, dataStart);
    }

    // ── 8. 스택된 값 분배 (공백으로 뭉친 숫자를 아래 빈 행에 분배) ──

    List<List<String>> distributeStackedValues(List<List<String>> grid) {
        int rowIdx = 0;
        while (rowIdx < grid.size()) {
            List<String> currentRow = grid.get(rowIdx);
            if (currentRow.size() < 2) {
                rowIdx++;
                continue;
            }

            int emptyBelow = countEmptyDataRowsBelow(grid, rowIdx);
            if (emptyBelow == 0) {
                rowIdx++;
                continue;
            }

            // 공백으로 구분된 숫자/기호 값이 있는 열 수집
            Map<Integer, String[]> stackedCols = new LinkedHashMap<>();
            for (int col = 1; col < currentRow.size(); col++) {
                String cell = currentRow.get(col).trim();
                if (cell.isEmpty()) {
                    continue;
                }
                String[] parts = cell.split("\\s+");
                if (parts.length > 1 && isAllNumericOrSymbol(parts)) {
                    stackedCols.put(col, parts);
                }
            }

            if (stackedCols.isEmpty()) {
                rowIdx++;
                continue;
            }

            // 모든 스택 열이 동일한 분배 모드에 합의하는지 확인
            Integer matchCount = null;
            boolean includeCurrentRow = false;
            boolean consistent = true;

            for (String[] parts : stackedCols.values()) {
                if (parts.length == emptyBelow + 1) {
                    if (matchCount != null && matchCount != emptyBelow + 1) {
                        consistent = false;
                        break;
                    }
                    matchCount = emptyBelow + 1;
                    includeCurrentRow = true;
                } else if (parts.length == emptyBelow) {
                    if (matchCount != null && matchCount != emptyBelow) {
                        consistent = false;
                        break;
                    }
                    matchCount = emptyBelow;
                    includeCurrentRow = false;
                } else {
                    consistent = false;
                    break;
                }
            }

            if (!consistent || matchCount == null) {
                rowIdx++;
                continue;
            }

            // 값 분배
            for (Map.Entry<Integer, String[]> entry : stackedCols.entrySet()) {
                int col = entry.getKey();
                String[] values = entry.getValue();

                if (includeCurrentRow) {
                    currentRow.set(col, values[0]);
                    for (int i = 1; i < values.length; i++) {
                        grid.get(rowIdx + i).set(col, values[i]);
                    }
                } else {
                    currentRow.set(col, "");
                    for (int i = 0; i < values.length; i++) {
                        grid.get(rowIdx + i + 1).set(col, values[i]);
                    }
                }
            }

            rowIdx += emptyBelow + 1;
        }
        return grid;
    }

    // ── 9. 다중행 헤더 축소 (≥3행인 경우 1행으로 축소) ──

    List<List<String>> collapseHeaderRows(List<List<String>> grid) {
        int dataStart = findDataStartRow(grid);
        if (dataStart < 3) {
            return grid;
        }

        int colCount = grid.stream().mapToInt(List::size).max().orElse(0);
        String[] headerValues = new String[colCount];
        String[] qualifiers = new String[colCount];
        for (int i = 0; i < colCount; i++) {
            headerValues[i] = "";
            qualifiers[i] = "";
        }

        // Bottom-up: 데이터에 가장 가까운 값이 실제 열 이름일 가능성이 높음
        for (int row = dataStart - 1; row >= 0; row--) {
            List<String> headerRow = grid.get(row);
            for (int col = 0; col < Math.min(colCount, headerRow.size()); col++) {
                String cell = headerRow.get(col).trim();
                if (cell.isEmpty()) {
                    continue;
                }
                if (cell.startsWith("(") && cell.endsWith(")")) {
                    if (qualifiers[col].isEmpty()) {
                        qualifiers[col] = cell;
                    }
                } else {
                    if (headerValues[col].isEmpty()) {
                        headerValues[col] = cell;
                    }
                }
            }
        }

        List<String> collapsedHeader = new ArrayList<>();
        for (int col = 0; col < colCount; col++) {
            String value = headerValues[col];
            if (!qualifiers[col].isEmpty()) {
                value = value.isEmpty() ? qualifiers[col] : value + " " + qualifiers[col];
            }
            collapsedHeader.add(value);
        }

        List<List<String>> result = new ArrayList<>();
        result.add(collapsedHeader);
        for (int row = dataStart; row < grid.size(); row++) {
            result.add(grid.get(row));
        }

        return result;
    }

    // ── 10. 단독 그룹 라벨 행 제거 ──

    private List<List<String>> removeStandaloneGroupRows(List<List<String>> grid) {
        if (grid.size() < 2) {
            return grid;
        }

        int dataStart = findDataStartRow(grid);
        List<List<String>> result = new ArrayList<>();

        for (int i = 0; i < dataStart; i++) {
            result.add(grid.get(i));
        }

        for (int i = dataStart; i < grid.size(); i++) {
            if (!isStandaloneGroupRow(grid.get(i))) {
                result.add(grid.get(i));
            }
        }

        return result;
    }

    void fillMergedCells(List<List<String>> grid) {
        if (grid.isEmpty()) {
            return;
        }

        fillHeaderRight(grid);
        fillFirstColumn(grid);
    }

    private void fillHeaderRight(List<List<String>> grid) {
        // shouldPreserveSparseHeader인 경우 이 메서드는 호출 전에 이미 건너뜀
        // 2행 헤더(두 번째 행에도 텍스트가 있는 경우)는 fill-right 하지 않음
        if (grid.size() >= 2) {
            List<String> secondRow = grid.get(1);
            boolean secondRowHasText =
                    secondRow.stream()
                            .filter(cell -> !cell.isEmpty())
                            .anyMatch(cell -> !cell.matches("-?[\\d,.]+|·|-"));
            if (secondRowHasText) {
                return;
            }
        }

        List<String> headerRow = grid.get(0);
        for (int col = 1; col < headerRow.size(); col++) {
            if (headerRow.get(col).isEmpty()) {
                headerRow.set(col, headerRow.get(col - 1));
            }
        }
    }

    // ── 10. 유효성 검증 ──

    boolean isMeaningful(List<List<String>> grid) {
        if (grid.isEmpty()) {
            return false;
        }
        long nonEmptyCells =
                grid.stream().flatMap(List::stream).filter(cell -> !cell.isEmpty()).count();
        return nonEmptyCells >= MIN_NON_EMPTY_CELLS;
    }

    // ── 11. 헤더 감지 + 마크다운 변환 ──

    boolean detectHeader(List<List<String>> grid) {
        if (grid.isEmpty()) {
            return false;
        }
        List<String> firstRow = grid.get(0);
        boolean allNonEmpty = firstRow.stream().noneMatch(String::isEmpty);
        if (!allNonEmpty) {
            return false;
        }
        boolean allNumeric = firstRow.stream().allMatch(cell -> cell.matches("-?\\d+(\\.\\d+)?"));
        return !allNumeric;
    }

    String toMarkdown(List<List<String>> grid, boolean hasHeader) {
        if (grid.isEmpty()) {
            return "";
        }

        int colCount = grid.get(0).size();
        StringBuilder sb = new StringBuilder();

        for (int rowIdx = 0; rowIdx < grid.size(); rowIdx++) {
            List<String> row = grid.get(rowIdx);
            sb.append("| ");
            sb.append(row.stream().collect(Collectors.joining(" | ")));
            sb.append(" |\n");

            if (rowIdx == 0) {
                sb.append("| ");
                for (int c = 0; c < colCount; c++) {
                    sb.append("---");
                    if (c < colCount - 1) {
                        sb.append(" | ");
                    }
                }
                sb.append(" |\n");
            }
        }

        return sb.toString().trim();
    }

    // ── 유틸리티 ──

    private void fillFirstColumn(List<List<String>> grid) {
        int dataStart = findDataStartRow(grid);

        // 1단계: 값이 있는 위치 수집
        List<int[]> groups = new ArrayList<>();
        for (int row = dataStart; row < grid.size(); row++) {
            if (!grid.get(row).get(0).isEmpty()) {
                groups.add(new int[] {row, row});
            }
        }

        if (groups.isEmpty()) {
            return;
        }

        // 2단계: 각 그룹의 범위 결정 (현재 값 ~ 다음 값 직전)
        for (int i = 0; i < groups.size(); i++) {
            int groupStart;
            int groupEnd;

            if (i == 0) {
                groupStart = dataStart;
            } else {
                groupStart = groups.get(i - 1)[0] + 1;
            }

            if (i < groups.size() - 1) {
                groupEnd = groups.get(i + 1)[0] - 1;
            } else {
                groupEnd = grid.size() - 1;
            }

            String value = grid.get(groups.get(i)[0]).get(0);

            // fill-up: 값 위치 위쪽으로 채움
            for (int row = groupStart; row < groups.get(i)[0]; row++) {
                if (grid.get(row).get(0).isEmpty()) {
                    grid.get(row).set(0, value);
                }
            }

            // fill-down: 값 위치 아래쪽으로 채움
            for (int row = groups.get(i)[0] + 1; row <= groupEnd; row++) {
                if (grid.get(row).get(0).isEmpty()) {
                    grid.get(row).set(0, value);
                }
            }
        }
    }

    private int findDataStartRow(List<List<String>> grid) {
        for (int i = 0; i < grid.size(); i++) {
            List<String> row = grid.get(i);
            long numericCount =
                    row.stream()
                            .filter(
                                    cell ->
                                            cell.matches("-?[\\d,.]+")
                                                    || "·".equals(cell)
                                                    || "-".equals(cell))
                            .count();
            if (numericCount >= 2) {
                return i;
            }
        }
        return 0;
    }

    private String joinLines(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(trimmed);
            }
        }
        return sb.toString();
    }

    private int findModeColumnCount(List<List<String>> grid) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (List<String> row : grid) {
            countMap.merge(row.size(), 1, Integer::sum);
        }
        return countMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    private boolean shouldPreserveSparseHeader(List<List<String>> grid) {
        if (grid.size() < 2) {
            return false;
        }

        List<String> firstRow = grid.get(0);
        List<String> secondRow = grid.get(1);

        boolean firstRowHasBlank = firstRow.stream().anyMatch(String::isEmpty);
        boolean secondRowHasBlank = secondRow.stream().anyMatch(String::isEmpty);
        boolean secondRowHasText =
                secondRow.stream()
                        .filter(cell -> !cell.isEmpty())
                        .anyMatch(cell -> !cell.matches("-?[\\d,.]+|·|-"));

        return firstRowHasBlank && secondRowHasBlank && secondRowHasText;
    }

    private int countEmptyDataRowsBelow(List<List<String>> grid, int startRow) {
        int count = 0;
        for (int r = startRow + 1; r < grid.size(); r++) {
            List<String> row = grid.get(r);
            if (row.get(0).isEmpty()) {
                break;
            }
            boolean allDataEmpty = true;
            for (int c = 1; c < row.size(); c++) {
                if (!row.get(c).isEmpty()) {
                    allDataEmpty = false;
                    break;
                }
            }
            if (allDataEmpty) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private boolean isAllNumericOrSymbol(String[] parts) {
        for (String part : parts) {
            if (!part.matches("-?[\\d,.]+|·|-")) {
                return false;
            }
        }
        return true;
    }

    // ── 파편화된 텍스트 표 감지/병합 ──

    private boolean isFragmentedTextTable(List<List<String>> grid) {
        if (grid.isEmpty() || grid.get(0).size() < 6) {
            return false;
        }

        // 헤더+데이터 구조가 감지되면 파편화 아님 (병합 셀로 빈 셀이 많아도 테이블)
        if (findDataStartRow(grid) > 0) {
            return false;
        }

        // 데이터 행이 하나라도 있으면 데이터 표 → 파편화 아님
        for (List<String> row : grid) {
            long numericCount = row.stream().filter(cell -> cell.matches("-?[\\d,.]+|·|-")).count();
            if (numericCount >= 2) {
                return false;
            }
        }

        int totalCells = 0;
        int emptyCells = 0;

        for (List<String> row : grid) {
            for (String cell : row) {
                totalCells++;
                if (cell.isEmpty()) {
                    emptyCells++;
                }
            }
        }

        if (totalCells == 0) {
            return false;
        }

        double sparsity = (double) emptyCells / totalCells;
        return sparsity >= 0.5;
    }

    private String joinFragmentedRows(List<List<String>> grid) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : grid) {
            String line =
                    row.stream()
                            .filter(cell -> !cell.isEmpty())
                            .collect(Collectors.joining(" "))
                            .trim();
            if (!line.isEmpty()) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ── 스파스 테이블 압축 헬퍼 ──

    private boolean isPageFooterRow(List<String> row) {
        long nonEmptyCount = row.stream().filter(cell -> !cell.isEmpty()).count();
        if (nonEmptyCount != 1) {
            return false;
        }

        String onlyValue = row.stream().filter(cell -> !cell.isEmpty()).findFirst().orElse("");
        return onlyValue.matches("-\\s*\\d+\\s*-");
    }

    private List<Integer> findColumnsWithData(List<List<String>> grid, int dataStart) {
        int maxCols = grid.stream().mapToInt(List::size).max().orElse(0);
        List<Integer> keepColumns = new ArrayList<>();

        int totalDataRows = 0;
        for (int row = dataStart; row < grid.size(); row++) {
            if (!isStandaloneGroupRow(grid.get(row))) {
                totalDataRows++;
            }
        }

        for (int col = 0; col < maxCols; col++) {
            // 첫 번째 열은 항상 유지 (구조적으로 의미 있는 열)
            if (col == 0) {
                keepColumns.add(col);
                continue;
            }

            int filledCount = 0;
            for (int row = dataStart; row < grid.size(); row++) {
                List<String> current = grid.get(row);
                if (col >= current.size()) {
                    continue;
                }
                if (!current.get(col).isEmpty() && !isStandaloneGroupRow(current)) {
                    filledCount++;
                }
            }

            // 2개 이상 채워진 열은 fill rate과 무관하게 유지 (병합 셀 보호)
            if (filledCount >= 2) {
                keepColumns.add(col);
                continue;
            }

            double fillRate = totalDataRows > 0 ? (double) filledCount / totalDataRows : 0;
            if (filledCount > 0 && fillRate >= 0.1) {
                keepColumns.add(col);
            }
        }

        return keepColumns;
    }

    private boolean isStandaloneGroupRow(List<String> row) {
        long nonEmptyCount = row.stream().filter(cell -> !cell.isEmpty()).count();
        if (nonEmptyCount != 1) {
            return false;
        }

        String onlyValue = row.stream().filter(cell -> !cell.isEmpty()).findFirst().orElse("");
        return !onlyValue.matches("-?[\\d,.]+|·|-");
    }

    private List<List<String>> compactSparseColumns(
            List<List<String>> grid, List<Integer> keepColumns, int dataStart) {
        Map<Integer, Integer> columnMapping = buildColumnMapping(grid, keepColumns);
        List<List<String>> compacted = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < grid.size(); rowIndex++) {
            List<String> row = grid.get(rowIndex);
            if (rowIndex < dataStart) {
                compacted.add(compactHeaderRow(row, keepColumns.size(), columnMapping));
            } else {
                compacted.add(compactDataRow(row, keepColumns, keepColumns.size()));
            }
        }

        return compacted;
    }

    private Map<Integer, Integer> buildColumnMapping(
            List<List<String>> grid, List<Integer> keepColumns) {
        int maxCols = grid.stream().mapToInt(List::size).max().orElse(0);
        Map<Integer, Integer> mapping = new HashMap<>();

        for (int i = 0; i < keepColumns.size(); i++) {
            mapping.put(keepColumns.get(i), i);
        }

        for (int col = 0; col < maxCols; col++) {
            if (mapping.containsKey(col)) {
                continue;
            }

            int target = findNearestKeepColumnIndex(keepColumns, col);
            if (target >= 0) {
                mapping.put(col, target);
            }
        }

        return mapping;
    }

    private int findNearestKeepColumnIndex(List<Integer> keepColumns, int col) {
        for (int i = 0; i < keepColumns.size(); i++) {
            if (keepColumns.get(i) >= col) {
                return i;
            }
        }
        return keepColumns.isEmpty() ? -1 : keepColumns.size() - 1;
    }

    private List<String> compactHeaderRow(
            List<String> row, int compactedColumnCount, Map<Integer, Integer> columnMapping) {
        List<String> compacted = new ArrayList<>();
        for (int i = 0; i < compactedColumnCount; i++) {
            compacted.add("");
        }

        for (int col = 0; col < row.size(); col++) {
            String cell = row.get(col);
            if (cell.isEmpty()) {
                continue;
            }

            Integer target = columnMapping.get(col);
            if (target == null) {
                continue;
            }

            String existing = compacted.get(target);
            if (existing.isEmpty()) {
                compacted.set(target, cell);
            } else if (!existing.contains(cell)
                    && !cell.matches("-?[\\d,.]+|·|-")
                    && !existing.matches("-?[\\d,.]+|·|-")) {
                compacted.set(target, existing + " " + cell);
            }
        }

        return compacted;
    }

    private List<String> compactDataRow(
            List<String> row, List<Integer> keepColumns, int compactedColumnCount) {
        List<String> compacted = new ArrayList<>();
        for (int i = 0; i < compactedColumnCount; i++) {
            compacted.add("");
        }

        for (int i = 0; i < keepColumns.size(); i++) {
            int originalColumn = keepColumns.get(i);
            if (originalColumn < row.size()) {
                compacted.set(i, row.get(originalColumn));
            }
        }

        return compacted;
    }
}
