package com.learnhub.course;

import com.learnhub.course.datasource.ReadFromReplica;
import com.learnhub.course.datasource.ReadReplicaDataSourceConfig;
import com.learnhub.course.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The "replica" is the same test container, told apart by the replica pool's application_name. */
class ReadReplicaRoutingIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void replicaUrl(DynamicPropertyRegistry registry) {
        registry.add("app.datasource.read.url", postgres::getJdbcUrl);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Probe probe;

    @Autowired
    private CategoryService categoryService;

    @Test
    @DisplayName("The primary DataSource is the lazy routing proxy")
    void routingDataSourceIsPrimary() {
        assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
    }

    @Test
    @DisplayName("@ReadFromReplica read-only queries run on the replica pool")
    void annotatedReadUsesReplica() {
        assertThat(probe.replicaRead()).isEqualTo(ReadReplicaDataSourceConfig.REPLICA_APPLICATION_NAME);
    }

    @Test
    @DisplayName("Other queries run on the primary pool")
    void plainReadUsesPrimary() {
        assertThat(probe.primaryRead()).isNotEqualTo(ReadReplicaDataSourceConfig.REPLICA_APPLICATION_NAME);
    }

    @Test
    @DisplayName("A read-write transaction under @ReadFromReplica still uses the primary")
    void writeTransactionUsesPrimary() {
        assertThat(probe.readWriteUnderReplica()).isNotEqualTo(ReadReplicaDataSourceConfig.REPLICA_APPLICATION_NAME);
    }

    @Test
    @DisplayName("A real catalog query works through the replica")
    void catalogQueryWorks() {
        assertThatCode(() -> categoryService.getAllCategories()).doesNotThrowAnyException();
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        Probe probe(JdbcTemplate jdbcTemplate) {
            return new Probe(jdbcTemplate);
        }
    }

    static class Probe {

        private static final String APPLICATION_NAME = "SELECT current_setting('application_name')";

        private final JdbcTemplate jdbcTemplate;

        Probe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @ReadFromReplica
        @Transactional(readOnly = true)
        public String replicaRead() {
            return jdbcTemplate.queryForObject(APPLICATION_NAME, String.class);
        }

        @Transactional(readOnly = true)
        public String primaryRead() {
            return jdbcTemplate.queryForObject(APPLICATION_NAME, String.class);
        }

        @ReadFromReplica
        @Transactional
        public String readWriteUnderReplica() {
            return jdbcTemplate.queryForObject(APPLICATION_NAME, String.class);
        }
    }
}
