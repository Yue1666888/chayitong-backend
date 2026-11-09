package com.chayitong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("chayitong")
            .withUsername("chayitong")
            .withPassword("test-password")
            .withInitScript("bootstrap/init.sql");

    static {
        MYSQL.start();
        try (var connection = java.sql.DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    connection,
                    new org.springframework.core.io.ClassPathResource("bootstrap/init_data.sql"));
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                    connection,
                    new org.springframework.core.io.ClassPathResource("bootstrap/content_seeds.sql"));
        } catch (java.sql.SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.media.storage", () -> "local");
        registry.add("app.media.local-root", () -> "./target/test-media");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;
}
