FROM gradle:8.6.0-jdk17-alpine as builder
WORKDIR /app
COPY . .
RUN gradle stream-rec:build -x test --no-daemon

FROM amazoncorretto:17-alpine3.19
WORKDIR /app
COPY --from=builder /app/stream-rec/build/libs/stream-rec.jar app.jar

# Install FFmpeg
RUN apk add --no-cache ffmpeg
# Install Rclone
RUN apk add --no-cache rclone

# Install SQLite
RUN apk add --no-cache sqlite

EXPOSE 12555

CMD ["java", "-jar", "app.jar"]
