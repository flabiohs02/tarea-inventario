package com.store.inventory.infrastructure;

import com.store.inventory.application.port.ProductRepository;
import com.store.inventory.domain.Product;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Product> bySku = new ConcurrentHashMap<>();

    @Override
    public Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(bySku.get(sku));
    }

    @Override
    public void save(Product product) {
        bySku.put(product.sku(), product);
    }
}
