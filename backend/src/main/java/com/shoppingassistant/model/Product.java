package com.shoppingassistant.model;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    private String id;

    @Column(nullable = false, length = 1000)
    private String title;

    private String brand;

    @Column(length = 1000)
    private String categoryPath;

    @Column(length = 1000)
    private String color;

    @Column(length = 1000)
    private String size;

    @Column(length = 3000)
    private String material;

    private Double price;

    private Double rating;

    private Integer ratingCount;

    @Column(length = 1000)
    private String imageUrl;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> features = new ArrayList<>();

    @Column(length = 1000)
    private String description;

    /** Lowercase title + brand + category used for keyword search. */
    @JsonIgnore
    @Column(length = 2000)
    private String searchText;
}
