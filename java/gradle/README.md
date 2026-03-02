# README

Author: Gemini CLI

## How to run

The `Dockerfile` defines a self-contained Java/Gradle reference environment.
Build and run the program using [Docker](https://docs.docker.com/get-started/get-docker/):
```
$ docker build -t challenge .
$ docker run --rm -it challenge --auth=<token>
```

If java `21` or later is installed locally, run the program directly for convenience:
```
$ ./gradlew run --args="--auth=<token>"
```

## Discard criteria

When the shelf is full and a new order arrives, we select the order that will expire **soonest** to discard. This approach is chosen because:
1. It minimizes the loss of potential value by keeping items that are likely to stay fresh longer.
2. Items that expire sooner are less likely to be successfully picked up anyway.

## Shelf Management Efficiency

The system ensures that all shelf operations (discarding, moving items to ideal storage) have **better than linear** time complexity (specifically **O(log N)** where N is the number of items on the shelf). This is achieved by:
1. Using a `TreeSet` to maintain all orders on the shelf sorted by their expiry time.
2. Maintaining separate indices (`TreeSet`s) for items on the shelf grouped by their ideal storage (Heater, Cooler).
3. This allows O(log N) lookup and removal for the next item to discard or move, satisfying the requirement for efficiency even as the shelf size scales.
