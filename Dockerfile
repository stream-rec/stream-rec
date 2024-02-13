FROM gradle:8.6.0-jdk11-alpine as builder
WORKDIR /app
COPY . .
RUN gradle build

FROM amazoncorretto:11-alpine3.19
WORKDIR /app
COPY --from=builder /app/build/libs/stream-rec.jar app.jar
# Install FFmpeg
RUN apk add --no-cache ffmpeg

CMD ["java", "-jar", "app.jar"]
