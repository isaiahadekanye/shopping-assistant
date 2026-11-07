package com.shoppingassistant.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.shoppingassistant.config.CatalogProperties;
import com.shoppingassistant.model.Product;
import com.shoppingassistant.model.SearchFilters;
import com.shoppingassistant.repository.ProductRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** Catalog reads and search. */
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final CatalogProperties catalog;

    public List<Product> findByIds(List<String> ids) {
        Map<String, Product> byId = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    /**
     * Every query keyword must start a word in the title, brand or category. Optional filters: category words
     * (all must be in the category path, e.g. "running shoe" excludes shoelaces), colour and price.
     */
    public List<Product> search(SearchFilters filters) {
        String sortBy = filters.sort() == null ? "relevance" : filters.sort();
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            for (String word : keywords(filters.query())) {
                where.add(cb.like(root.get("searchText"), "% " + word + "%"));
            }
            for (String word : keywords(filters.category())) {
                where.add(cb.like(cb.lower(root.get("categoryPath")), "%" + word + "%"));
            }
            if (filters.color() != null) {
                String color = "%" + filters.color().toLowerCase() + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("color")), color), cb.like(cb.lower(root.get("title")), color)));
            }
            if (filters.minPrice() != null) {
                where.add(cb.ge(root.get("price"), filters.minPrice()));
            }
            if (filters.maxPrice() != null) {
                where.add(cb.le(root.get("price"), filters.maxPrice()));
            }
            if (sortBy.startsWith("price")) {
                where.add(cb.isNotNull(root.get("price")));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Sort sort = switch (sortBy) {
            case "price_low_to_high" -> Sort.by("price").ascending();
            case "price_high_to_low" -> Sort.by("price").descending();
            case "rating" -> Sort.by("rating", "ratingCount").descending();
            default -> Sort.by("ratingCount").descending(); // most reviewed first
        };
        // Each size/colour of a product is a separate listing with the same title, and variants share a
        // review count, so they bunch together. Fetch extra candidates and keep the first listing per title.
        Set<String> titles = new HashSet<>();
        return productRepository.findAll(spec, PageRequest.of(0, catalog.getSearchCandidates(), sort)).getContent().stream()
                .filter(p -> titles.add(p.getTitle().toLowerCase()))
                .limit(catalog.getSearchLimit())
                .toList();
    }

    /**
     * Lowercase words separated by single spaces, with a leading space: "Men's Running-Shoes!" becomes
     * " men s running shoes". Punctuation can't hide a word, and the search pattern "% shoe%" only matches
     * at the start of a word (so "men" doesn't match "women").
     */
    public static String searchable(String text) {
        return " " + (text == null ? "" : text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", " ").trim());
    }

    /** Query words, with a trailing plural "s" dropped so "shoes" also matches "shoe". */
    static List<String> keywords(String query) {
        return Arrays.stream(searchable(query).trim().split(" "))
                .filter(w -> !w.isEmpty())
                .map(w -> w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") ? w.substring(0, w.length() - 1) : w)
                .toList();
    }
}
