package com.learnhub.enrollment.churn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnhub.enrollment.repository.ChurnSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Uploads the churn dataset to S3 as gzipped JSON Lines, one Hive-style partition per snapshot date:
 * {prefix}/features/dt=YYYY-MM-DD/part-0.jsonl.gz and {prefix}/labels/dt=YYYY-MM-DD/part-0.jsonl.gz.
 * Each upload rewrites the whole partition, so re-exporting a date is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChurnDatasetExporter {

    private final ChurnSnapshotRepository snapshotRepository;
    private final ObjectProvider<S3Client> s3Client;
    private final ObjectMapper objectMapper;

    @Value("${churn.dataset.export.enabled:false}")
    private boolean enabled;

    @Value("${churn.dataset.export.bucket:}")
    private String bucket;

    @Value("${churn.dataset.export.prefix:churn}")
    private String prefix;

    public boolean isEnabled() {
        return enabled;
    }

    /** Returns the keys written; upload errors are logged, not thrown, so they never fail the scoring run. */
    public List<String> export(LocalDate featuresDate, Collection<LocalDate> labelDates) {
        if (!enabled) {
            return List.of();
        }
        List<String> written = new ArrayList<>();
        upload("features", featuresDate, snapshotRepository.findFeatures(featuresDate), written);
        for (LocalDate date : labelDates) {
            upload("labels", date, snapshotRepository.findLabels(date), written);
        }
        return written;
    }

    private void upload(String dataset, LocalDate date, List<Map<String, Object>> rows, List<String> written) {
        if (rows.isEmpty()) {
            return;
        }
        String key = "%s/%s/dt=%s/part-0.jsonl.gz".formatted(prefix, dataset, date);
        try {
            s3Client.getObject().putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/gzip").build(),
                    RequestBody.fromBytes(toJsonLines(rows)));
            log.info("Exported {} churn {} rows to s3://{}/{}", rows.size(), dataset, bucket, key);
            written.add(key);
        } catch (Exception ex) {
            log.error("Could not export churn {} for {} to s3://{}/{}: {}", dataset, date, bucket, key, ex.getMessage());
        }
    }

    byte[] toJsonLines(List<Map<String, Object>> rows) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(bytes), StandardCharsets.UTF_8)) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> line = new LinkedHashMap<>();
                row.forEach((column, value) -> line.put(column, jsonValue(value)));
                out.write(objectMapper.writeValueAsString(line));
                out.write('\n');
            }
        }
        return bytes.toByteArray();
    }

    // ISO-8601 strings rather than JDBC types, so Athena/pandas read them the same way.
    // TIMESTAMP columns hold UTC wall-clock time, so the JVM zone must not be applied.
    private static Object jsonValue(Object value) {
        return switch (value) {
            case Timestamp t -> t.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();
            case Date d -> d.toLocalDate().toString();
            case null, default -> value;
        };
    }
}
