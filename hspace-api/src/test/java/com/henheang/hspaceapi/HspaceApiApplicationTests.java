package com.henheang.hspaceapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HspaceApiApplicationTests {

    // Boots against the test schema: Flyway (security-api's V1-V6 plus this
    // module's V100+) and Hibernate `validate` must agree.
    @Test
    void contextLoads() {}
}
