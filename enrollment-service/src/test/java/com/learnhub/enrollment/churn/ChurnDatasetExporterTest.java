package com.learnhub.enrollment.churn;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChurnDatasetExporterTest {

    @Test
    @DisplayName("toJsonLines - JDBC rows - gzipped JSON Lines with ISO dates and explicit nulls")
    void toJsonLines() throws Exception {
        ChurnDatasetExporter exporter = new ChurnDatasetExporter(null, null, new ObjectMapper());
        UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("snapshot_date", Date.valueOf(LocalDate.of(2026, 10, 3)));
        row.put("enrollment_id", id);
        row.put("snapshot_at", Timestamp.valueOf(LocalDateTime.of(2026, 10, 2, 19, 0)));   // UTC wall-clock, as stored
        row.put("days_since_last_login", null);
        row.put("current_course_progress", 42.5);

        byte[] gzipped = exporter.toJsonLines(List.of(row, row));

        String text = new String(new GZIPInputStream(new ByteArrayInputStream(gzipped)).readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(text.split("\n")).hasSize(2).allMatch(line -> line.equals(
                "{\"snapshot_date\":\"2026-10-03\",\"enrollment_id\":\"" + id + "\","
                        + "\"snapshot_at\":\"2026-10-02T19:00:00Z\",\"days_since_last_login\":null,"
                        + "\"current_course_progress\":42.5}"));
    }
}
