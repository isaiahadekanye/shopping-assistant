package com.shoppingassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.shoppingassistant.service.ProductService;

/** Boots the whole app: wiring works and the fixture catalog is loaded on startup. */
@SpringBootTest
@ActiveProfiles("test")
class ShoppingAssistantApplicationTest {
    @Autowired
    ProductService productService;

    @Test
    void startsAndLoadsCatalog() {
        assertThat(productService.findByIds(List.of("RS1"))).hasSize(1);
    }
}
