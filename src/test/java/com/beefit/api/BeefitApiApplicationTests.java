package com.beefit.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.beefit.api.support.PostgreSqlTestSupport;

@SpringBootTest(properties = {
		"spring.main.lazy-initialization=true"
})
@ActiveProfiles("test")
@Testcontainers
class BeefitApiApplicationTests {

	@Container
	static final PostgreSQLContainer POSTGRES = PostgreSqlTestSupport
			.createContainer("beefit_application_test");

	@DynamicPropertySource
	static void registerPostgresProperties(DynamicPropertyRegistry registry) {
		PostgreSqlTestSupport.registerDataSourceProperties(registry, POSTGRES);
	}

	@Test
	void contextLoads() {
	}

}
