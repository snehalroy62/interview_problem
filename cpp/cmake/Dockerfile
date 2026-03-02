# syntax=docker/dockerfile:1
FROM gcc:15.2@sha256:0cc1665930da5d3052d4af019579f1f35dbdf02598ddadfba3b15365b0507de5
RUN apt-get update && apt-get install -y cmake
WORKDIR /app

COPY CMakeLists.txt .

RUN cmake -S . -B build
RUN cmake -S . -B build # If omitted, libcurl is built again when src/ is copied
RUN cmake --build build --parallel 8
COPY src/ src/
RUN cmake -S . -B build
RUN cmake --build build --parallel 8

ENTRYPOINT ["/app/build/src/challenge"]
