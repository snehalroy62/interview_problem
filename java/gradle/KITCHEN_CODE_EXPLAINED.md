# 🍳 How Our Smart Kitchen Code Works!

Imagine the `Kitchen.java` file is a big book of rules for a robot chef. Let's look at each page!

---

## 1. The Setup (The "Tools" we use)
```java
private final Storage heater = new Storage(Action.HEATER, 6);
private final Storage cooler = new Storage(Action.COOLER, 6);
private final Storage shelf = new Storage(Action.SHELF, 12);
```
**Child Explanation:** 
These are our three special tables. 
- The **Red Table (Heater)** can hold 6 hot pizzas. 
- The **Blue Table (Cooler)** can hold 6 cold ice creams. 
- The **Yellow Table (Shelf)** is a bigger table for 12 of anything!

---

## 2. Taking an Order (`receiveOrder`)
```java
public synchronized void receiveOrder(Order order) { ... }
```
**Child Explanation:** 
This is what the robot does when a new order comes in. 
- It looks at the food and says, "Are you hot or cold?"
- It tries to put it on the right table.
- If the right table is full, it tries to put it on the big Yellow Table.
- If *everything* is full, it's smart! It moves something else or says, "Oops, this old food has to go!" to make room for the new food.

---

## 3. Picking Up Food (`pickupOrder`)
```java
private synchronized void pickupOrder(String orderId) { ... }
```
**Child Explanation:** 
This is when a hungry customer comes to get their food!
- The robot checks: "Is this food still yummy or did it go bad?"
- If it's yummy, the customer takes it!
- If it's yucky (expired), the robot throws it in the bin. 
- **Bonus:** After the customer leaves, the robot immediately looks at the Yellow Table to see if it can move a waiting food to the now-empty Red or Blue table! (That's the smart part I added!)

---

## 4. Moving Food Around (`tryMoveToIdeal` & `tryMoveFromShelf`)
```java
private void tryMoveToIdeal(String storageName, Instant now) { ... }
```
**Child Explanation:** 
This is our robot being a helpful cleaner. If a spot opens up on a "favorite" table, the robot quickly runs to the big Yellow Table, grabs the right food, and moves it to the better table so it stays yummy longer!

---

## 5. Cleaning Up (`discardFromShelf`)
```java
private void discardFromShelf(Instant now) { ... }
```
**Child Explanation:** 
When the big Yellow Table is totally full and a new order arrives, the robot has to make a choice. It looks at all the food on the table and finds the one that is going to get "yucky" the soonest, and says "Bye-bye!" to it.

---

## 6. The Helper Robot Rules (`StoredOrder` class)
```java
private static class StoredOrder { ... }
```
**Child Explanation:** 
Every piece of food has a little "timer" attached to it. 
- This part of the code keeps track of how yummy the food is.
- If the food is on the "wrong" table, the timer ticks **twice as fast**! 
- It tells the robot exactly when the food will become "yucky."

---

## 7. The Storage Boxes (`Storage` class)
```java
private static class Storage { ... }
```
**Child Explanation:** 
This is just a fancy box that knows how many things are inside it and if there is any room left for more!

---

### Summary
The whole file is just a set of rules to make sure food gets to the customer while it's still yummy, and if the kitchen gets too crowded, the robot knows exactly what to do! 🤖🍕
