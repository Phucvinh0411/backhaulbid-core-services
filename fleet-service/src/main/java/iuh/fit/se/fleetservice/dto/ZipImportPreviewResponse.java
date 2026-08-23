package iuh.fit.se.fleetservice.dto;

import java.util.List;
import java.util.Map;

public record ZipImportPreviewResponse(
        int totalRows,
        int validRows,
        int invalidRows,
        List<Row> rows
) {
    public record Row(
            int rowNumber,
            Map<String, String> data,
            boolean valid,
            List<String> errors,
            List<String> warnings,
            List<String> matchedFiles
    ) {
    }
}
