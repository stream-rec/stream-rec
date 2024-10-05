FROM gradle:8.8-jdk21-alpine AS builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test

# Install dependencies
FROM amazoncorretto:21-alpine3.20
WORKDIR /app
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Add libc6-compat for Android room
RUN apk add --no-cache curl libc6-compat ffmpeg sqlite rclone tzdata && \
    apk add --no-cache streamlink --repository=https://dl-cdn.alpinelinux.org/alpine/edge/community && \
     # install streamlink-ttvlol
    INSTALL_DIR="/root/.local/share/streamlink/plugins"; mkdir -p "$INSTALL_DIR"; curl -L -o "$INSTALL_DIR/twitch.py" 'https://github.com/2bc4/streamlink-ttvlol/releases/latest/download/twitch.py'

    # Set timezone
ENV TZ=\${TZ:-Europe/Paris}

EXPOSE 12555

CMD ["java", "-jar", "app.jar"]