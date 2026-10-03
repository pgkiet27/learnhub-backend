package com.learnhub.enrollment.repository;

import com.learnhub.enrollment.churn.ChurnSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.learnhub.enrollment.churn.ChurnFeatureCalculator.FEATURE_NAMES;

/** churn_feature_snapshots, written with plain JDBC for the upsert and the labelling UPDATE. */
@Repository
@RequiredArgsConstructor
public class ChurnSnapshotRepository {

    // The run that comes horizonDays later may start slightly earlier in the day than this one
    private static final Duration LABEL_TOLERANCE = Duration.ofHours(1);

    private static final List<String> COLUMNS = columns();
    private static final String UPSERT = """
            INSERT INTO churn_feature_snapshots (%s) VALUES (%s)
            ON CONFLICT (snapshot_date, enrollment_id) DO UPDATE SET %s
            """.formatted(
            String.join(", ", COLUMNS),
            COLUMNS.stream().map(c -> ":" + c).collect(Collectors.joining(", ")),
            COLUMNS.stream().skip(2).map(c -> c + " = EXCLUDED." + c).collect(Collectors.joining(", ")));

    // churn: 0 = active or completed within the window, 1 = no activity at all since the snapshot,
    // NULL = the only activity seen is after the window (last_accessed_at was overwritten)
    private static final String LABEL = """
            UPDATE churn_feature_snapshots s
            SET churn = (SELECT CASE
                                    WHEN e.is_completed
                                         AND e.completed_at <= s.snapshot_at + make_interval(days => :horizonDays) THEN 0
                                    WHEN e.last_accessed_at > s.snapshot_at
                                         AND e.last_accessed_at <= s.snapshot_at + make_interval(days => :horizonDays) THEN 0
                                    WHEN e.last_accessed_at IS NULL OR e.last_accessed_at <= s.snapshot_at THEN 1
                                END
                         FROM enrollments e WHERE e.id = s.enrollment_id),
                reminded_in_window = COALESCE(
                        (SELECT e.churn_reminder_delivered_at >= s.snapshot_at
                                AND e.churn_reminder_delivered_at <= s.snapshot_at + make_interval(days => :horizonDays)
                         FROM enrollments e WHERE e.id = s.enrollment_id), false),
                labeled_at = :now
            WHERE s.labeled_at IS NULL AND s.snapshot_at <= :cutoff
            RETURNING s.snapshot_date
            """;

    private static final String FIND_FEATURES = "SELECT " + String.join(", ", COLUMNS)
            + " FROM churn_feature_snapshots WHERE snapshot_date = :date ORDER BY enrollment_id";

    private static final String FIND_LABELS = """
            SELECT snapshot_date, enrollment_id, churn, reminded_in_window, labeled_at
            FROM churn_feature_snapshots
            WHERE snapshot_date = :date AND labeled_at IS NOT NULL
            ORDER BY enrollment_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** A second run on the same date replaces that date's rows. */
    public void saveAll(LocalDate snapshotDate, Instant snapshotAt, List<ChurnSnapshot> snapshots) {
        SqlParameterSource[] params = snapshots.stream()
                .map(s -> {
                    MapSqlParameterSource p = new MapSqlParameterSource()
                            .addValue("snapshot_date", snapshotDate)
                            .addValue("enrollment_id", s.enrollmentId())
                            .addValue("snapshot_at", utc(snapshotAt))
                            .addValue("user_id", s.userId())
                            .addValue("course_id", s.courseId())
                            .addValue("enrolled_at", utc(s.enrolledAt()))
                            .addValue("missing_sources", s.missingSources(), Types.VARCHAR)
                            .addValue("churn_score", s.churnScore(), Types.NUMERIC)
                            .addValue("risk_level", s.riskLevel(), Types.VARCHAR)
                            .addValue("model_used", s.modelUsed(), Types.VARCHAR)
                            .addValue("reminder_queued", s.reminderQueued());
                    FEATURE_NAMES.forEach(f -> p.addValue(f, s.features().get(f), Types.DOUBLE));
                    return p;
                })
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, params);
    }

    /** Labels every snapshot whose window has ended; returns the snapshot date of each row labelled. */
    public List<LocalDate> labelDue(Instant now, int horizonDays) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("horizonDays", horizonDays)
                .addValue("now", utc(now))
                .addValue("cutoff", utc(now.minus(Duration.ofDays(horizonDays)).plus(LABEL_TOLERANCE)));
        return jdbc.queryForList(LABEL, params, LocalDate.class);
    }

    public List<Map<String, Object>> findFeatures(LocalDate snapshotDate) {
        return jdbc.queryForList(FIND_FEATURES, Map.of("date", snapshotDate));
    }

    public List<Map<String, Object>> findLabels(LocalDate snapshotDate) {
        return jdbc.queryForList(FIND_LABELS, Map.of("date", snapshotDate));
    }

    // TIMESTAMP columns hold UTC wall-clock time (like Hibernate writes them); java.sql.Timestamp would use the JVM zone
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static List<String> columns() {
        List<String> columns = new ArrayList<>(List.of(
                "snapshot_date", "enrollment_id", "snapshot_at", "user_id", "course_id", "enrolled_at"));
        columns.addAll(FEATURE_NAMES);
        columns.addAll(List.of("missing_sources", "churn_score", "risk_level", "model_used", "reminder_queued"));
        return List.copyOf(columns);
    }
}
