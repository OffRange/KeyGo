FROM rust:1.98.0-alpine AS build
WORKDIR /app
# Bypass rust-toolchain.toml so rustup doesn't fetch the Android targets
ENV RUSTUP_TOOLCHAIN=1.98.0
COPY . .
RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/app/target \
    cargo build --release --locked -p keygo-server \
 && cp target/release/keygo-server /keygo-server

FROM alpine:3.24
RUN addgroup -S -g 10001 keygo && \
    adduser -S -D -H -u 10001 -G keygo keygo && \
    install -d -m 700 -o keygo -g keygo /run/secrets

USER 10001
COPY --from=build /keygo-server /usr/local/bin/keygo-server

EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD ["wget", "-q", "--spider", "http://127.0.0.1:8000/health/live"]
CMD ["keygo-server", "serve"]
