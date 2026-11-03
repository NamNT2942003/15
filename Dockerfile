# =========================================================================
# Dockerfile Multi-stage Build cho QuickBite cart-service
# =========================================================================

# Stage 1: Build mã nguồn (Biên dịch Java và đóng gói file JAR)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
# Cấp quyền thực thi cho gradlew và thực hiện build JAR
RUN chmod +x ./gradlew && ./gradlew bootJar

# Stage 2: Runtime (Môi trường thực thi tinh gọn, chỉ chứa JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Chuyển giao artifact .jar từ Stage 1 sang Stage 2
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

