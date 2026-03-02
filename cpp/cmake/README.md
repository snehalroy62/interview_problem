# README

Author: `<your name here>`

## How to build

The `Dockerfile` defines a self-contained C++/CMake reference environment.
Build and run the program using [Docker](https://docs.docker.com/get-started/get-docker/):
```
$ docker build -t challenge .
$ docker run --rm -it challenge --auth <token>
```
Feel free to modify the `Dockerfile` as you see fit.

If cmake `3.25` or later is locally installed, build and run the program directly for convenience:
```
cmake -S . -B build
cmake --build build
./build/src/challenge --auth <token>
```

## Discard criteria

`<your chosen discard criteria and rationale here>`
