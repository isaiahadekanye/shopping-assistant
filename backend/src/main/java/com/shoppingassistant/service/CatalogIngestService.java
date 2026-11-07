package com.shoppingassistant.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingassistant.config.CatalogProperties;
import com.shoppingassistant.model.Product;
import com.shoppingassistant.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** On startup, loads products.json into the in-memory H2 database */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogIngestService implements ApplicationRunner {
    /** How data exports commonly spell "no value" (e.g. pandas writes "nan"). */
    private static final Set<String> NO_VALUE = Set.of("nan", "null", "none", "n/a");

    private final ProductRepository repository;
    private final ObjectMapper mapper;
    private final CatalogProperties catalog;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Path path = Path.of(catalog.getJsonPath());
        if (!Files.exists(path)) {
            log.warn("{} not found; starting with an empty catalog", path.toAbsolutePath());
            return;
        }
        load(path);
    }

    /** Streams the file and inserts in batches. */
    public int load(Path path) throws IOException {
        Set<String> seen = new HashSet<>();
        List<Product> batch = new ArrayList<>();
        int skipped = 0;
        try (MappingIterator<JsonNode> records = mapper.readerFor(JsonNode.class).readValues(path.toFile())) {
            while (records.hasNext()) {
                Optional<Product> product = toProduct(records.next());
                if (product.isEmpty() || !seen.add(product.get().getId())) {
                    skipped++;
                    continue;
                }
                batch.add(product.get());
                if (batch.size() >= catalog.getBatchSize()) {
                    repository.saveAll(batch);
                    batch.clear();
                }
            }
        }
        repository.saveAll(batch);
        log.info("Loaded {} products from {} ({} skipped: missing title/id or duplicate)", seen.size(), path, skipped);
        return seen.size();
    }

    /** Maps one raw record to a Product. Values like "nan" become null; records without id or title are skipped. */
    public static Optional<Product> toProduct(JsonNode r) {
        String id = text(r.get("parent_asin"));
        String title = text(r.get("title"));
        if (id == null || title == null) {
            return Optional.empty();
        }
        JsonNode details = r.path("details");
        String brand = firstNonNull(text(r.get("brand")), text(details.get("Brand Name")), text(details.get("Brand")), text(r.get("store")));
        String categoryPath = categoryPath(texts(r.get("categories")));
        Double price = r.path("price").isNumber() && r.path("price").asDouble() > 0 ? r.path("price").asDouble() : null;
        Double rating = r.path("average_rating").isNumber() ? r.path("average_rating").asDouble() : null;
        Integer ratingCount = r.path("rating_number").isNumber() ? r.path("rating_number").asInt() : null;
        String image = StreamSupport.stream(r.path("images").spliterator(), false)
                .map(img -> text(img.get("large"))).filter(url -> url != null).findFirst().orElse(null);
        String description = String.join(" ", texts(r.get("description")));

        return Optional.of(Product.builder()
                .id(id).title(title).brand(brand).categoryPath(categoryPath.isEmpty() ? null : categoryPath)
                .color(text(r.get("color"))).size(text(r.get("size"))).material(text(r.get("material")))
                .price(price).rating(rating).ratingCount(ratingCount).imageUrl(image)
                .features(texts(r.get("features")).stream().limit(8).toList())
                .description(description.isEmpty() ? null : description.substring(0, Math.min(description.length(), 1000)))
                .searchText(ProductService.searchable(title + " " + brand + " " + categoryPath))
                .build());
    }

    /**
     * Joins the category levels, skipping the top level when there are more. The top level is the broad
     * department nearly every product shares, so keeping it would make every product match words like "shoes".
     */
    static String categoryPath(List<String> levels) {
        List<String> specific = levels.size() > 1 ? levels.subList(1, levels.size()) : levels;
        return String.join(" > ", specific);
    }

    /** Trimmed text, or null if it's empty or a "no value" placeholder like "nan". */
    private static String text(JsonNode node) {
        if (node == null || !node.isValueNode()) {
            return null;
        }
        String s = node.asText().trim();
        return s.isEmpty() || NO_VALUE.contains(s.toLowerCase()) ? null : s;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> {
                String s = text(n);
                if (s != null) out.add(s);
            });
        }
        return out;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
