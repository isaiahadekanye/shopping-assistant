package com.shoppingassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingassistant.model.Product;
import com.shoppingassistant.repository.ProductRepository;

/** The fixture has 14 records: 12 valid, 1 without a title, 1 duplicate id. */
@SpringBootTest
@ActiveProfiles("test")
class CatalogIngestServiceTest {
    @Autowired
    ProductRepository repository;

    @Test
    void skipsRecordsWithoutTitleAndDuplicateIds() {
        assertThat(repository.count()).isEqualTo(12);
        assertThat(repository.findById("NOTITLE")).isEmpty();
        assertThat(repository.findById("RS1").orElseThrow().getTitle()).startsWith("Men's Road Running Shoes"); // first copy wins
    }

    @Test
    void cleansFields() {
        Product rs1 = repository.findById("RS1").orElseThrow();
        assertThat(rs1.getBrand()).isEqualTo("Stride");
        assertThat(rs1.getCategoryPath()).isEqualTo("Men > Shoes > Athletic > Running"); // root category dropped
        assertThat(rs1.getPrice()).isEqualTo(89.99);
        assertThat(rs1.getImageUrl()).isNotNull();
        assertThat(rs1.getFeatures()).isNotEmpty();
    }

    @Test
    void treatsNanAsMissingAndFallsBackToStoreForBrand() {
        Product socks = repository.findById("SK1").orElseThrow();
        assertThat(socks.getColor()).isNull();
        assertThat(socks.getSize()).isNull();
        assertThat(socks.getBrand()).isEqualTo("Cotton Plus");
    }

    @Test
    void dropsOnlyTheTopCategoryLevel() {
        assertThat(CatalogIngestService.categoryPath(List.of("Store", "Women", "Shoes"))).isEqualTo("Women > Shoes");
        assertThat(CatalogIngestService.categoryPath(List.of("Backpacks"))).isEqualTo("Backpacks"); // only one level: keep it
        assertThat(CatalogIngestService.categoryPath(List.of())).isEmpty();
    }

    @Test
    void rejectsNonPositivePrices() throws Exception {
        var raw = new ObjectMapper().readTree("{\"parent_asin\":\"X\",\"title\":\"Hat\",\"price\":0}");
        assertThat(CatalogIngestService.toProduct(raw).orElseThrow().getPrice()).isNull();
    }
}
