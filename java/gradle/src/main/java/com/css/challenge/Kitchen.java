package com.css.challenge;

import com.css.challenge.client.Action;
import com.css.challenge.client.Order;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kitchen {
  private static final Logger LOGGER = LoggerFactory.getLogger(Kitchen.class);

  private final Storage heater = new Storage(Action.HEATER, 6);
  private final Storage cooler = new Storage(Action.COOLER, 6);
  private final Storage shelf = new Storage(Action.SHELF, 12);
  
  private final Map<String, StoredOrder> activeOrders = new ConcurrentHashMap<>();
  private final List<Action> actions = Collections.synchronizedList(new ArrayList<>());
  private final AtomicInteger ordersInProgress = new AtomicInteger(0);
  
  private Instant lastTimestamp = Instant.EPOCH;

  private final ExecutorService workerPool = Executors.newFixedThreadPool(
      Runtime.getRuntime().availableProcessors()
  );

  private final Duration minPickup;
  private final Duration maxPickup;

  public Kitchen(Duration minPickup, Duration maxPickup) {
    this.minPickup = minPickup;
    this.maxPickup = maxPickup;
  }

  private synchronized void recordAction(String id, String name, String type, String target) {
    Instant now = Instant.now();
    if (!now.isAfter(lastTimestamp)) {
      now = lastTimestamp.plusNanos(1000); // Monotonic microsecond precision
    }
    lastTimestamp = now;
    actions.add(new Action(now, id, name, type, target));
  }

  /**
   * Receives a new order using Full Async CompletableFuture chains.
   */
  public CompletableFuture<Void> receiveOrder(Order order) {
    ordersInProgress.incrementAndGet();
    
    return CompletableFuture.runAsync(() -> {
      Instant now = Instant.now();
      String ideal = getIdealStorage(order.getTemp());

      boolean placed = tryPlace(order, ideal, now);
      if (!placed) {
        reorganizeAndPlace(order, ideal, now);
      }
    }, workerPool).thenRunAsync(() -> {
      // Schedule the pickup in the future
      long delayMillis = ThreadLocalRandom.current().nextLong(minPickup.toMillis(), maxPickup.toMillis() + 1);
      
      CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS, workerPool)
          .execute(() -> pickupOrder(order.getId()));
    }, workerPool);
  }

  private boolean tryPlace(Order order, String ideal, Instant now) {
    Storage target = getStorage(ideal);
    target.lock.lock();
    try {
      if (target.hasSpace()) {
        place(order, ideal, now);
        return true;
      }
    } finally {
      target.lock.unlock();
    }

    if (!ideal.equals(Action.SHELF)) {
      shelf.lock.lock();
      try {
        if (shelf.hasSpace()) {
          place(order, Action.SHELF, now);
          return true;
        }
      } finally {
        shelf.lock.unlock();
      }
    }
    return false;
  }

  private void reorganizeAndPlace(Order order, String ideal, Instant now) {
    shelf.lock.lock();
    try {
      if (shelf.hasSpace()) {
        place(order, Action.SHELF, now);
        return;
      }

      StoredOrder moved = tryMoveFromShelf(now);
      if (moved != null) {
        place(order, Action.SHELF, now);
      } else {
        discardFromShelf(now);
        place(order, Action.SHELF, now);
      }
    } finally {
      shelf.lock.unlock();
    }
  }

  private void place(Order order, String storageName, Instant now) {
    StoredOrder so = new StoredOrder(order, now, storageName);
    getStorage(storageName).add(so, now);
    activeOrders.put(order.getId(), so);
    recordAction(order.getId(), order.getName(), Action.PLACE, storageName);
    LOGGER.info("Action: PLACE {} ({}) in {}", order.getId(), order.getName(), storageName);
  }

  private void pickupOrder(String orderId) {
    Instant now = Instant.now();
    StoredOrder so = activeOrders.remove(orderId);
    if (so == null) return;

    Storage storage = getStorage(so.currentStorage);
    storage.lock.lock();
    try {
      storage.remove(so);
      String actionType = so.isExpired(now) ? Action.DISCARD : Action.PICKUP;
      recordAction(orderId, so.order.getName(), actionType, so.currentStorage);
      LOGGER.info("Action: {} {} ({}) from {}", actionType.toUpperCase(), orderId, so.order.getName(), so.currentStorage);
    } finally {
      storage.lock.unlock();
    }
    ordersInProgress.decrementAndGet();
  }

  private StoredOrder tryMoveFromShelf(Instant now) {
    for (String ideal : List.of(Action.HEATER, Action.COOLER)) {
      Storage idealStorage = getStorage(ideal);
      idealStorage.lock.lock();
      try {
        if (idealStorage.hasSpace()) {
          StoredOrder so = shelf.getSoonestToExpireForIdeal(ideal);
          if (so != null) {
            shelf.remove(so);
            so.moveTo(ideal, now);
            idealStorage.add(so, now);
            recordAction(so.order.getId(), so.order.getName(), Action.MOVE, ideal);
            LOGGER.info("Action: MOVE {} ({}) from shelf to {}", so.order.getId(), so.order.getName(), ideal);
            return so;
          }
        }
      } finally {
        idealStorage.lock.unlock();
      }
    }
    return null;
  }

  private void discardFromShelf(Instant now) {
    StoredOrder worst = shelf.getSoonestToExpire();
    if (worst != null) {
      shelf.remove(worst);
      activeOrders.remove(worst.order.getId());
      recordAction(worst.order.getId(), worst.order.getName(), Action.DISCARD, Action.SHELF);
      LOGGER.info("Action: DISCARD {} ({}) from shelf (FULL)", worst.order.getId(), worst.order.getName());
      ordersInProgress.decrementAndGet();
    }
  }

  private static String getIdealStorage(String temp) {
    return switch (temp) {
      case "hot" -> Action.HEATER;
      case "cold" -> Action.COOLER;
      default -> Action.SHELF;
    };
  }

  private Storage getStorage(String name) {
    return switch (name) {
      case Action.HEATER -> heater;
      case Action.COOLER -> cooler;
      default -> shelf;
    };
  }

  public void waitUntilDone() {
    while (ordersInProgress.get() > 0) {
      try {
        Thread.sleep(10); // Polling more frequently (10ms)
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    workerPool.shutdown();
  }

  public List<Action> getActions() {
    return new ArrayList<>(actions);
  }

  private static class Storage {
    final String name;
    final int capacity;
    final ReentrantLock lock = new ReentrantLock();
    final TreeSet<StoredOrder> orders = new TreeSet<>(StoredOrder.EXPIRY_COMPARATOR);
    final Map<String, TreeSet<StoredOrder>> byIdealStorage = new HashMap<>();

    Storage(String name, int capacity) {
      this.name = name;
      this.capacity = capacity;
      if (Action.SHELF.equals(name)) {
        byIdealStorage.put(Action.HEATER, new TreeSet<>(StoredOrder.EXPIRY_COMPARATOR));
        byIdealStorage.put(Action.COOLER, new TreeSet<>(StoredOrder.EXPIRY_COMPARATOR));
        byIdealStorage.put(Action.SHELF, new TreeSet<>(StoredOrder.EXPIRY_COMPARATOR));
      }
    }

    boolean hasSpace() {
      return orders.size() < capacity;
    }

    void add(StoredOrder order, Instant now) {
      order.updateExpiry(now);
      orders.add(order);
      if (Action.SHELF.equals(name)) {
        String ideal = getIdealStorage(order.order.getTemp());
        byIdealStorage.get(ideal).add(order);
      }
    }

    void remove(StoredOrder order) {
      orders.remove(order);
      if (Action.SHELF.equals(name)) {
        String ideal = getIdealStorage(order.order.getTemp());
        byIdealStorage.get(ideal).remove(order);
      }
    }

    StoredOrder getSoonestToExpire() {
      return orders.isEmpty() ? null : orders.first();
    }

    StoredOrder getSoonestToExpireForIdeal(String ideal) {
      if (!Action.SHELF.equals(name)) return null;
      TreeSet<StoredOrder> set = byIdealStorage.get(ideal);
      return (set == null || set.isEmpty()) ? null : set.first();
    }
  }

  private static class StoredOrder {
    final Order order;
    final Instant placedAt;
    Instant lastStateChangedAt;
    double accumulatedDegradation;
    String currentStorage;
    Instant expiryInstant;

    static final Comparator<StoredOrder> EXPIRY_COMPARATOR =
        (a, b) -> {
          int res = a.expiryInstant.compareTo(b.expiryInstant);
          if (res == 0) return a.order.getId().compareTo(b.order.getId());
          return res;
        };

    StoredOrder(Order order, Instant placedAt, String storage) {
      this.order = order;
      this.placedAt = placedAt;
      this.lastStateChangedAt = placedAt;
      this.accumulatedDegradation = 0;
      this.currentStorage = storage;
      updateExpiry(placedAt);
    }

    void moveTo(String newStorage, Instant now) {
      updateDegradation(now);
      this.currentStorage = newStorage;
      this.lastStateChangedAt = now;
      updateExpiry(now);
    }

    void updateDegradation(Instant now) {
      long deltaMillis = Duration.between(lastStateChangedAt, now).toMillis();
      double deltaSeconds = deltaMillis / 1000.0;
      double multiplier = isAtIdealStorage() ? 1.0 : 2.0;
      accumulatedDegradation += deltaSeconds * multiplier;
      lastStateChangedAt = now;
    }

    void updateExpiry(Instant now) {
      updateDegradation(now);
      double multiplier = isAtIdealStorage() ? 1.0 : 2.0;
      double remainingSeconds = (order.getFreshness() - accumulatedDegradation) / multiplier;
      this.expiryInstant = now.plusMillis((long) (remainingSeconds * 1000));
    }

    boolean isAtIdealStorage() {
      return getIdealStorage(order.getTemp()).equals(currentStorage);
    }

    boolean isExpired(Instant now) {
      updateDegradation(now);
      return accumulatedDegradation >= order.getFreshness();
    }
  }
}