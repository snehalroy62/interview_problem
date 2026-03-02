package com.css.challenge;

import com.css.challenge.client.Action;
import com.css.challenge.client.Order;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kitchen manages the storage and fulfillment of food orders.
 * It handles placement, movement, and pickup logic, ensuring orders are stored
 * correctly and discarded if they expire or if space is needed.
 */
public class Kitchen {
  private static final Logger LOGGER = LoggerFactory.getLogger(Kitchen.class);

  private final Storage heater = new Storage(Action.HEATER, 6);
  private final Storage cooler = new Storage(Action.COOLER, 6);
  private final Storage shelf = new Storage(Action.SHELF, 12);

  private final Map<String, StoredOrder> activeOrders = new ConcurrentHashMap<>();
  private final List<Action> actions = Collections.synchronizedList(new ArrayList<>());
  private final AtomicInteger ordersInProgress = new AtomicInteger(0);
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

  private final Duration minPickup;
  private final Duration maxPickup;

  public Kitchen(Duration minPickup, Duration maxPickup) {
    this.minPickup = minPickup;
    this.maxPickup = maxPickup;
  }

  /**
   * Receives a new order and places it in the appropriate storage.
   * Schedules a random pickup time for the order.
   */
  public synchronized void receiveOrder(Order order) {
    ordersInProgress.incrementAndGet();
    Instant now = Instant.now();
    String ideal = getIdealStorage(order.getTemp());

    String target = ideal;
    Storage targetStorage = getStorage(ideal);

    if (!targetStorage.hasSpace()) {
      if (!ideal.equals(Action.SHELF) && shelf.hasSpace()) {
        target = Action.SHELF;
      } else {
        // Shelf is full or ideal was shelf and it's full.
        // Try to move something from shelf to its ideal storage to make room.
        StoredOrder moved = tryMoveFromShelf(now);
        if (moved != null) {
          target = Action.SHELF;
        } else {
          // No space to move, must discard an item from the shelf.
          discardFromShelf(now);
          target = Action.SHELF;
        }
      }
    }

    place(order, target, now);

    // Schedule random pickup
    long delayMillis =
        ThreadLocalRandom.current().nextLong(minPickup.toMillis(), maxPickup.toMillis() + 1);
    scheduler.schedule(() -> pickupOrder(order.getId()), delayMillis, TimeUnit.MILLISECONDS);
  }

  private void place(Order order, String storageName, Instant now) {
    StoredOrder so = new StoredOrder(order, now, storageName);
    getStorage(storageName).add(so, now);
    activeOrders.put(order.getId(), so);
    actions.add(new Action(now, order.getId(), Action.PLACE, storageName));
    LOGGER.info("Action: PLACE {} in {}", order.getId(), storageName);
  }

  private synchronized void pickupOrder(String orderId) {
    Instant now = Instant.now();
    StoredOrder so = activeOrders.remove(orderId);
    if (so == null) return; // Already discarded

    getStorage(so.currentStorage).remove(so);

    String actionType;
    if (so.isExpired(now)) {
      actionType = Action.DISCARD;
      LOGGER.info("Action: DISCARD {} from {} (EXPIRED)", orderId, so.currentStorage);
    } else {
      actionType = Action.PICKUP;
      LOGGER.info("Action: PICKUP {} from {}", orderId, so.currentStorage);
    }

    actions.add(new Action(now, orderId, actionType, so.currentStorage));

    // Proactively try to fill the vacated space from the shelf
    if (!so.currentStorage.equals(Action.SHELF) && !shelf.hasSpace()) {
      tryMoveToIdeal(so.currentStorage, now);
    }
    ordersInProgress.decrementAndGet();
  }

  private void tryMoveToIdeal(String storageName, Instant now) {
    Storage targetStorage = getStorage(storageName);
    while (targetStorage.hasSpace()) {
      StoredOrder so = shelf.getSoonestToExpireForIdeal(storageName);
      if (so == null) break;
      shelf.remove(so);
      so.moveTo(storageName, now);
      targetStorage.add(so, now);
      actions.add(new Action(now, so.order.getId(), Action.MOVE, storageName));
      LOGGER.info("Action: PROACTIVE MOVE {} from shelf to {}", so.order.getId(), storageName);
    }
  }

  private StoredOrder tryMoveFromShelf(Instant now) {
    // Try to move anything that can move off the shelf.
    // Check heater first, then cooler.
    for (String ideal : List.of(Action.HEATER, Action.COOLER)) {
      if (getStorage(ideal).hasSpace()) {
        StoredOrder so = shelf.getSoonestToExpireForIdeal(ideal);
        if (so != null) {
          shelf.remove(so);
          so.moveTo(ideal, now);
          getStorage(ideal).add(so, now);
          actions.add(new Action(now, so.order.getId(), Action.MOVE, ideal));
          LOGGER.info("Action: MOVE {} from shelf to {}", so.order.getId(), ideal);
          return so;
        }
      }
    }
    return null;
  }

  private void discardFromShelf(Instant now) {
    // Discard the item that expires soonest.
    // Storage.getOldest() returns the order with the earliest expiryInstant.
    StoredOrder worst = shelf.getSoonestToExpire();
    if (worst != null) {
      shelf.remove(worst);
      activeOrders.remove(worst.order.getId());
      actions.add(new Action(now, worst.order.getId(), Action.DISCARD, Action.SHELF));
      LOGGER.info("Action: DISCARD {} from shelf (SHELF FULL, soonest to expire)", worst.order.getId());
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
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    scheduler.shutdown();
  }

  public List<Action> getActions() {
    return new ArrayList<>(actions);
  }

  private static class Storage {
    final String name;
    final int capacity;
    // Using a TreeSet to keep orders sorted by expiry time for O(log N) discard logic.
    final TreeSet<StoredOrder> orders = new TreeSet<>(StoredOrder.EXPIRY_COMPARATOR);

    // Indices for faster lookup of items by their ideal storage.
    // Only used for the shelf storage to fulfill the "better than linear" requirement for moves too.
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
