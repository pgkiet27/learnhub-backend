package com.learnhub.course.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Two connection pools (primary and read replica) behind one routing DataSource. Without
 * app.datasource.read.url this class stays inactive and Spring Boot builds its usual single DataSource.
 */
@Configuration
@ConditionalOnExpression("!'${app.datasource.read.url:}'.isEmpty()")
public class ReadReplicaDataSourceConfig {

    public static final String REPLICA_APPLICATION_NAME = "course-service-replica";

    @Bean
    @FlywayDataSource
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource primaryDataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setPoolName("primary");
        return dataSource;
    }

    // Same database user and pool settings: a replica carries the primary's roles and passwords
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource replicaDataSource(DataSourceProperties properties,
                                              @Value("${app.datasource.read.url}") String url) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).url(url).build();
        dataSource.setPoolName("replica");
        dataSource.setReadOnly(true);
        // Tells replica sessions apart in pg_stat_activity
        dataSource.addDataSourceProperty("ApplicationName", REPLICA_APPLICATION_NAME);
        return dataSource;
    }

    // The lazy proxy defers picking a pool until the first statement, after @Transactional has set read-only
    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("primaryDataSource") DataSource primary,
                                 @Qualifier("replicaDataSource") DataSource replica) {
        ReplicaRoutingDataSource routing = new ReplicaRoutingDataSource();
        routing.setTargetDataSources(Map.of(
                ReplicaRoutingDataSource.Target.PRIMARY, primary,
                ReplicaRoutingDataSource.Target.REPLICA, replica));
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }
}
