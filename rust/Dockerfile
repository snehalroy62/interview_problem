FROM rust:1.93@sha256:80302520b7199f0504975bca59a914015e9fee088f759875dbbc238ca9509ee1
WORKDIR /app

COPY Cargo.toml Cargo.lock ./

RUN mkdir src && \
    echo "fn main() {println!(\"hello world!\")}" > src/main.rs

RUN cargo build --release && \
    rm src/*.rs target/release/deps/challenge*

COPY src src
RUN cargo build --release

ENTRYPOINT [ "/app/target/release/challenge" ]
