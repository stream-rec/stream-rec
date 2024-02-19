FROM gradle:8.6.0-jdk11-alpine as builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build

FROM amazoncorretto:11-alpine3.19
WORKDIR /app
COPY --from=builder /app/build/stream-rec/libs/stream-rec.jar app.jar

# Install FFmpeg
RUN apk add --no-cache ffmpeg
# Install Rclone
RUN apk add --no-cache rclone

# Install SQLite
RUN apk add --no-cache sqlite

CMD ["java", "-jar", "app.jar"]
