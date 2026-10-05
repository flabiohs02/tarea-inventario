package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.support.RecordingAlertListener;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;

/** Peak season: hundreds of customers buying the same product at the same time. */
class ConcurrencyTest {

    private static final int CUSTOMERS = 200;

    @RepeatedTest(5)
    void neverOversellsUnderConcurrentReservations() throws Exception {
        RecordingAlertListener purchasing = new RecordingAlertListener();
        InventoryService service = Inventory.create(Clock.systemUTC(), purchasing);
        service.registerProduct("HOT", ProductCategory.STANDARD);
        service.addStock("HOT", 50);

        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        runConcurrently(CUSTOMERS, i -> {
            try {
                service.reserve("ORDER-" + i, "HOT", 1);
                reserved.incrementAndGet();
            } catch (InsufficientStockException e) {
                rejected.incrementAndGet();
            }
        });

        assertEquals(50, reserved.get());
        assertEquals(CUSTOMERS - 50, rejected.get());
        assertEquals(0, service.available("HOT"));
        assertEquals(1, purchasing.alerts().size(), "the low stock alert must be sent exactly once");
    }

    @RepeatedTest(5)
    void concurrentRetriesOfTheSameOrderReserveOnce() throws Exception {
        InventoryService service = Inventory.create(Clock.systemUTC(), (sku, available) -> { });
        service.registerProduct("HOT", ProductCategory.STANDARD);
        service.addStock("HOT", 100);

        runConcurrently(50, i -> service.reserve("ORDER-1", "HOT", 2));

        assertEquals(98, service.available("HOT"));
    }

    private static void runConcurrently(int tasks, IntTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int id = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    task.run(id);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IntTask {
        void run(int id);
    }
}
