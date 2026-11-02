# BÁO CÁO KỸ THUẬT: HOÀN THIỆN DOCKERFILE MULTI-STAGE BUILD

- **Dự án**: QuickBite Microservices Platform
- **Service tối ưu**: `cart-service`
- **Vị trí**: Junior DevOps Engineer
- **Chủ đề**: Tối ưu hóa kích thước Docker Image & Bảo mật Container thông qua Multi-stage Build
- **Tác giả**: NamNT (NamNTHE176286)

---

## 1. BỐI CẢNH & MỤC TIÊU TỐI ƯU

Trong chiến dịch tối ưu hóa hạ tầng của QuickBite, việc giảm thiểu dung lượng Docker Image cho các microservices mang lại 3 lợi ích sống còn:
1. **Tốc độ triển khai (Deployment Velocity)**: Giảm băng thông và thời gian kéo (pull/push) image giữa Container Registry (Docker Hub, AWS ECR) và các Kubernetes Node / Docker Swarm.
2. **Tối ưu tài nguyên lưu trữ**: Tiết kiệm không gian đĩa trên cả máy chủ CI/CD lẫn máy chủ Production.
3. **Bảo mật mã nguồn (Security Hardening & Reduced Attack Surface)**:
   - Loại bỏ hoàn toàn mã nguồn thô (`.java`), file cấu hình dev, git metadata, và các công cụ biên dịch (`javac`, Gradle daemon, header files C/C++) ra khỏi môi trường Production.
   - Tin tặc không thể lợi dụng compiler hoặc source code thô nếu container bị thâm nhập.

---

## 2. PHÂN TÍCH CHI TIẾT CÁC VỊ TRÍ ĐIỀN KHUYẾT `[___]`

Đoạn mã Dockerfile ban đầu của lập trình viên:

```dockerfile
# Stage 1: Build mã nguồn
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar

# Stage 2: Runtime 
FROM [___]
WORKDIR /app
[___]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 1️⃣ Vị trí 1: Base Image cho Stage 2 (`FROM [___]`)
- **Giá trị điền**: `eclipse-temurin:17-jre-alpine`
- **Phân tích kỹ thuật**:
  - **Khác biệt cốt lõi giữa JDK và JRE**:
    - **JDK (Java Development Kit)**: Chứa toàn bộ công cụ phát triển (trình biên dịch `javac`, công cụ debug `jdb`, profiler `jstack`, header file native). Những công cụ này chỉ cần thiết trong quá trình build mã nguồn (Stage 1), hoàn toàn **thừa thãi** khi chạy ứng dụng trên môi trường Production.
    - **JRE (Java Runtime Environment)**: Chỉ chứa máy ảo Java Virtual Machine (JVM) và các thư viện lõi chuẩn cần thiết để thực thi bytecode (`.class`, `.jar`).
  - **Lựa chọn Alpine Linux (`alpine`)**:
    - Alpine Linux sử dụng thư viện siêu nhẹ `musl libc` và bộ tiện ích `busybox`, giúp kích thước base image hệ điều hành chỉ vỏn vẹn ~5MB (thay vì hơn 100MB của Debian/Ubuntu tiêu chuẩn).
  - Do đó, việc kết hợp `eclipse-temurin:17` với phiên bản `jre-alpine` là sự lựa chọn tối ưu nhất cho Runtime của Spring Boot.

### 2️⃣ Vị trí 2: Chuyển giao Artifact giữa các Stage (`[___]`)
- **Giá trị điền**: `COPY --from=builder /app/build/libs/*.jar app.jar`
- **Phân tích kỹ thuật**:
  - **Cú pháp Docker Multi-stage**:
    $$\texttt{COPY --from=<Tên\_Stage\_Nguồn> <Đường\_dẫn\_File\_ở\_Stage\_Nguồn> <Đường\_dẫn\_Đích\_ở\_Stage\_Hiện\_Tại>}$$
  - Ở Stage 1, container được gán nhãn đại diện là `AS builder`. Sau khi lệnh `./gradlew bootJar` thực thi thành công, file JAR đóng gói được sinh ra tại `/app/build/libs/cart-service-0.0.1.jar` (hoặc wildcard `*.jar`).
  - Tham số `--from=builder` cho phép Docker trích xuất duy nhất file `.jar` từ Stage 1 sang Stage 2 và đổi tên thành `app.jar` mà không cần mang theo bất kỳ file mã nguồn `.java` hay file dependency trung gian nào.

---

## 3. DOCKERFILE HOÀN CHỈNH ([`Dockerfile`](Dockerfile))

```dockerfile
# =========================================================================
# Dockerfile Multi-stage Build cho QuickBite cart-service
# =========================================================================

# Stage 1: Build mã nguồn (Biên dịch Java và đóng gói file JAR)
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
# Cấp quyền thực thi và đóng gói ứng dụng Spring Boot
RUN chmod +x ./gradlew && ./gradlew bootJar

# Stage 2: Runtime (Môi trường thực thi tinh gọn, chỉ chứa JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Chuyển giao artifact .jar từ Stage 1 sang Stage 2
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 4. ĐÁNH GIÁ THỰC NGHIỆM: SO SÁNH DUNG LƯỢNG SINGLE-STAGE VS MULTI-STAGE

Để kiểm chứng hiệu quả thực tế, cả hai phiên bản Dockerfile đã được tiến hành build trực tiếp trên máy chủ cục bộ:
- Phiên bản 1 (Single-stage): Sử dụng trực tiếp `eclipse-temurin:17-jdk-alpine` và giữ nguyên source code trong image.
- Phiên bản 2 (Multi-stage): Sử dụng 2 giai đoạn (Builder: JDK Alpine $\rightarrow$ Runtime: JRE Alpine).

### Bảng so sánh dung lượng thực tế

| Tiêu chí | Single-stage Build (JDK) | Multi-stage Build (JRE) | Mức độ cải thiện |
| :--- | :---: | :---: | :---: |
| **Base Image** | `eclipse-temurin:17-jdk-alpine` | `eclipse-temurin:17-jre-alpine` | Giảm tải các thư viện dev thừa |
| **Dung lượng Image** | **511 MB** | **256 MB** | **Giảm ~50% (~255 MB)** |
| **Chứa Source Code `.java`?** | Có (Lộ mã nguồn thô) | **Không (Chỉ chứa `app.jar`)** | Tăng cường bảo mật |
| **Chứa công cụ biên dịch (`javac`)?** | Có (Thừa thãi, rủi ro) | **Không (Chỉ có JVM runtime)** | Giảm thiểu diện tích tấn công |
| **Thời gian khởi động** | Tương đương | Tương đương (1.52s) | Không ảnh hưởng hiệu năng |

### Bằng chứng lệnh `docker images` trong Terminal

```bash
PS C:\Devop\15> docker images | findstr cart-service
quickbite/cart-service                single-stage    49f464a8e537   2 minutes ago       511MB
quickbite/cart-service                multi-stage     a4665e89990c   2 minutes ago       256MB
```

$$\implies \textbf{Kết luận: } \text{Kích thước Image giảm chính xác một nửa (từ 511MB xuống còn 256MB), loại bỏ hoàn toàn mã nguồn thô khỏi image production.}$$

---

## 5. BẰNG CHỨNG THỰC NGHIỆM CONTAINER HOẠT ĐỘNG THÀNH CÔNG

Container xây dựng từ Multi-stage build được khởi chạy thử nghiệm để kiểm tra tính toàn vẹn của ứng dụng `cart-service`:

```bash
PS C:\Devop\15> docker run -d --name test-cart-multistage -p 8080:8080 quickbite/cart-service:multi-stage
0967e40391207a5ae1f5d0c1d88056ff40cd92e3c182ade6d3356cb9815cddc5

PS C:\Devop\15> docker logs test-cart-multistage
2026-11-02 12:52:36.380  INFO 1 --- [main] c.q.c.CartServiceApplication         : Starting CartServiceApplication v0.0.1 using Java 17...
2026-11-02 12:52:36.500  INFO 1 --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8082 (http)
2026-11-02 12:52:36.500  INFO 1 --- [main] c.q.c.CartServiceApplication         : Started CartServiceApplication in 1.52 seconds
```

---

## 6. HƯỚNG DẪN NỘP BÀI LÊN PORTAL

1. **File nộp**: Tệp tin [`Dockerfile`](Dockerfile) hoàn chỉnh theo đúng cấu trúc ở Mục 3.
2. **Hình ảnh nộp**: Ảnh chụp màn hình cửa sổ Terminal chạy lệnh:
   ```bash
   docker images | grep cart-service
   ```
   Hiển thị rõ 2 dòng `quickbite/cart-service:single-stage` (511MB) và `quickbite/cart-service:multi-stage` (256MB) để chứng minh mức độ tối ưu hóa dung lượng image.
