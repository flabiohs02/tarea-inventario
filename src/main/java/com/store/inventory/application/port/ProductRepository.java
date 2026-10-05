package com.store.inventory.application.port;

import com.store.inventory.domain.Product;
import java.util.Optional;

/** Storage of products. Today in memory; tomorrow a database. */
public interface ProductRepository {

    Optional<Product> findBySku(String sku);

    void save(Product product);
}
