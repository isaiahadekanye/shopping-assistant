package com.shoppingassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.shoppingassistant.model.Product;
import com.shoppingassistant.model.SearchFilters;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceTest {
    @Autowired
    ProductService products;

    private List<String> ids(String query, String color, Double maxPrice, String sort) {
        return products.search(new SearchFilters(query, null, color, null, maxPrice, sort)).stream().map(Product::getId).toList();
    }

    @Test
    void requiresEveryKeywordInTitleBrandOrCategory() {
        // WS1 is a water shoe that only mentions running in its features, so it doesn't match.
        assertThat(ids("running shoes", null, null, null)).containsExactlyInAnyOrder("RS1", "RS2", "RS3");
    }

    @Test
    void keywordsMatchAtWordStartsSoMenDoesNotMatchWomen() {
        assertThat(ids("men running shoes", null, null, null)).containsExactlyInAnyOrder("RS1", "RS3");
    }

    @Test
    void ignoresPunctuationAndCase() {
        assertThat(ids("Running-SHOES!", null, null, null)).containsExactlyInAnyOrder("RS1", "RS2", "RS3");
    }

    @Test
    void categoryMustAppearInTheCategoryPath() {
        // WS1 (water shoe) is under Shoes > Outdoor, not Running.
        List<Product> result = products.search(new SearchFilters("shoes", "running", null, null, null, "price_low_to_high"));
        assertThat(result).extracting(Product::getId).containsExactly("RS3", "RS1", "RS2");
    }

    @Test
    void filtersByColorInColorOrTitle() {
        assertThat(ids("running shoes", "blue", null, null)).containsExactlyInAnyOrder("RS2", "RS3"); // "Navy Blue" counts
    }

    @Test
    void filtersAndSortsByPrice() {
        assertThat(ids("running shoes", null, 100.0, "price_low_to_high")).containsExactly("RS3", "RS1");
        assertThat(ids("running shoes", null, null, "price_high_to_low")).first().isEqualTo("RS2");
    }

    @Test
    void defaultOrderIsMostReviewedFirst() {
        assertThat(ids("running shoes", null, null, null)).containsExactly("RS1", "RS2", "RS3");
    }

    @Test
    void returnsOneListingPerProductTitle() {
        // SH4 is another colour of SH3 (same title, fewer reviews), so only SH3 is returned.
        assertThat(ids("women shorts", null, null, null)).containsExactlyInAnyOrder("SH1", "SH3");
        // When the variant is the only match, it's still found.
        assertThat(ids("women shorts", "red", null, null)).containsExactly("SH4");
    }

    @Test
    void tightFiltersGiveNoMatchRatherThanLooseResults() {
        assertThat(ids("running shoes", null, 20.0, null)).isEmpty();
    }

    @Test
    void findByIdsKeepsRequestedOrderAndSkipsUnknown() {
        assertThat(products.findByIds(List.of("SH2", "NOPE", "RS1"))).extracting(Product::getId).containsExactly("SH2", "RS1");
    }
}
