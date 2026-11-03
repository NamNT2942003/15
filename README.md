# BÁO CÁO KỸ THUẬT: TỐI ƯU HÓA HỆ THỐNG GIÁM SÁT PROMETHEUS - GRAFANA & TỰ ĐỘNG HÓA CI/CD

- **Dự án**: QuickBite Microservices Platform
- **Học phần**: Monitoring, Alerting & Observability (Session 15)
- **Tác giả**: NamNT (NamNTHE176286)
- **Repository**: [NamNT2942003/15](https://github.com/NamNT2942003/15)

---

## MỤC LỤC
1. [Bài tập 1: Sửa lỗi mất kết nối Grafana - Prometheus (Docker Internal DNS)](#bài-tập-1-sửa-lỗi-mất-kết-nối-grafana---prometheus)
2. [Bài tập 2: Tối ưu hóa chu kỳ Scrape (Tránh quá tải tài nguyên)](#bài-tập-2-tối-ưu-hóa-chu-kỳ-scrape)
3. [Bài tập 3: Xử lý cảnh báo giả False Alarm (Ngưỡng > 85%, for: 5m, promtool)](#bài-tập-3-xử-lý-cảnh-báo-giả-false-alarm)
4. [Bài tập 4: Tự động hóa Giám sát với Prometheus Service Discovery (`file_sd_configs`)](#bài-tập-4-tự-động-hóa-giám-sát-với-prometheus-service-discovery)
5. [Bài tập 5: Debug App bằng Metrics (Kích hoạt Spring Boot Actuator Prometheus Endpoint)](#bài-tập-5-debug-app-bằng-metrics)
6. [Bài tập 6: Tích hợp Giám sát vào CI/CD (Fail-Fast Gatekeeper với `promtool`)](#bài-tập-6-tích-hợp-giám-sát-vào-cicd)
7. [Tổng kết & Hướng dẫn kiểm thử toàn diện](#tổng-kết--hướng-dẫn-kiểm-thử-toàn-diện)

---

## BÀI TẬP 1: SỬA LỖI MẤT KẾT NỐI GRAFANA - PROMETHEUS

### 1. Hiện tượng & Log lỗi thực tế
Khi container Prometheus bị khởi động lại do lệnh `docker-compose restart`, Docker Daemon giải phóng và cấp phát lại địa chỉ IP ngẫu nhiên trong dải bridge subnet. Do Datasource của Grafana đang hardcode IP tĩnh `http://192.168.1.15:9090`:
- Toàn bộ dashboard trên Grafana rơi vào trạng thái **"No data"** hoặc hiển thị biểu tượng chấm than đỏ lỗi kết nối.
- Trích xuất log lỗi từ container Grafana (`docker logs -f grafana`):

```log
logger=tsdb.prometheus t=2026-11-03T07:15:22.102Z level=error msg="Instant query failed" query="up" err="Post \"http://192.168.1.15:9090/api/v1/query\": dial tcp 192.168.1.15:9090: connect: connection refused"
logger=context userId=1 orgId=1 uname=admin t=2026-11-03T07:15:22.103Z level=error msg="Internal server error" error="Post \"http://192.168.1.15:9090/api/v1/query_range\": dial tcp 192.168.1.15:9090: i/o timeout"
logger=datasources t=2026-11-03T07:15:25.451Z level=error msg="Failed to check datasource health" id=1 name=Prometheus error="HTTP connection error"
```

### 2. Phân tích nguyên nhân & Cơ chế Docker Embedded DNS
- **Bản chất của IP tĩnh**: Container IP là phù du (ephemeral). Không bao giờ được dùng IP nội bộ của container trong cấu hình dài hạn.
- **Docker Embedded DNS**: Khi các service cùng khai báo chung mạng bridge (`monitoring-net`), Docker kích hoạt máy chủ DNS nội bộ tại địa chỉ `127.0.0.11`. DNS này tự động theo dõi và ánh xạ service name `prometheus` sang IP hiện hành của container.

### 3. File cấu hình sửa đổi (`grafana/provisioning/datasources/datasource.yml`)
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

### 4. Khởi động lại Grafana & Kiểm tra kết nối
```bash
# Khởi động lại container Grafana để nạp lại datasource provisioning
docker compose restart grafana
```
- Truy cập Grafana: `http://localhost:3000/datasources`
- Nhấn **Save & test**: Nhận thông báo màu xanh lá `Successfully queried the Prometheus API.`
- Mọi dashboard khôi phục biểu đồ và dữ liệu metrics đầy đủ.

---

## BÀI TẬP 2: TỐI ƯU HÓA CHU KỲ SCRAPE

### 1. Phân tích tác hại khi `scrape_interval: 1s`
- **Quá tải CPU**: Prometheus phải mở socket TCP, gửi HTTP GET `/metrics`, bóc tách text format và nén time-series chunks liên tục mỗi 1 giây cho từng target.
- **Tràn bộ nhớ đệm (RAM Bloat)**: Head block trong RAM phải tiếp nhận lượng samples gấp 15 lần.
- **Áp lực Disk I/O & WAL**: Khối lượng ghi dữ liệu vào Write-Ahead Log tăng vọt, tiêu tốn IOPS đĩa cứng.
- **Tác động ngược lên ứng dụng mục tiêu**: Service backend liên tục bị chiếm tài nguyên thread pool để export metrics.

### 2. Giải pháp tối ưu: Chu kỳ `15s`
Nâng `scrape_interval` và `evaluation_interval` lên `15s`:
- Giảm hơn 93% số lượng HTTP requests và tải nén TSDB.
- Chu kỳ 15 giây là tiêu chuẩn vàng, đảm bảo đồ thị Grafana mượt mà mà không làm nghẽn hệ thống.

### 3. Cấu hình `prometheus/prometheus.yml`
```yaml
global:
  scrape_interval: 15s      # Đã điều chỉnh từ 1s thành 15s
  evaluation_interval: 15s  # Chu kỳ 15s đánh giá alerting rules

scrape_configs:
  - job_name: "prometheus"
    static_configs:
      - targets: ["localhost:9090"]

  - job_name: "backend-app"
    static_configs:
      - targets: ["backend:8080"]
```

### 4. Cơ chế Reload cấu hình Zero-Downtime
Kích hoạt flag `--web.enable-lifecycle` trong `docker-compose.yml`. Khi sửa đổi cấu hình, reload tức thì không cần restart container:
```bash
curl -X POST http://localhost:9090/-/reload
```

---

## BÀI TẬP 3: XỬ LÝ CẢNH BÁO GIẢ (FALSE ALARM)

### 1. Phân tích nguyên nhân "Alert Fatigue"
File ban đầu:
```yaml
expr: 100 - (avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[1m])) * 100) > 20
for: 10s
```
- Ngưỡng `> 20%` quá thấp; các tác vụ thông thường (khởi động JVM, chạy batch job) đều dễ dàng chạm mốc này.
- Thời gian `for: 10s` quá ngắn; các biến động tức thời (transient spikes) kích hoạt cảnh báo liên tục, gây ngập lụt email (Spam / Alert Fatigue).

### 2. Cấu hình khắc phục (`prometheus/alert_rules.yml`)
- Tăng ngưỡng cảnh báo lên `> 85%`.
- Tăng thời gian duy trì `for` lên `5m` (5 phút liên tục).

```yaml
groups:
  - name: CPU_Alerts
    rules:
      - alert: HighCPUUsage
        expr: 100 - (avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[1m])) * 100) > 85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage detected on {{ $labels.instance }}"
          description: "CPU usage has exceeded 85% (current: {{ $value | printf \"%.2f\" }}%) continuously for more than 5 minutes."
```

### 3. Kiểm tra cú pháp bằng `promtool`
Chạy lệnh kiểm tra tính hợp lệ của rule:
```bash
docker run --rm -v "${PWD}/prometheus:/etc/prometheus" \
  prom/prometheus:v2.51.0 promtool check rules /etc/prometheus/alert_rules.yml
```

**Kết quả kiểm tra:**
```text
Checking /etc/prometheus/alert_rules.yml
  SUCCESS: 1 rules found
```

---

## BÀI TẬP 4: TỰ ĐỘNG HÓA GIÁM SÁT VỚI PROMETHEUS SERVICE DISCOVERY

### 1. Hạn chế của `static_configs` và Sức mạnh của `file_sd_configs`
Trong mô hình Microservices, số lượng container liên tục co giãn (autoscaling). Với `static_configs`, mỗi lần thêm/bớt container đều phải sửa file `prometheus.yml` và reload/restart Prometheus, gây rủi ro downtime và sai sót cú pháp.

Với `file_sd_configs`:
- Prometheus tự động lắng nghe sự kiện thay đổi file qua `inotify` và quét định kỳ `refresh_interval`.
- Chỉ cần cập nhật file danh sách JSON/YAML, Prometheus tự động nạp target mới mà **không cần restart hay reload**.

### 2. File danh sách mục tiêu (`prometheus/targets.json`)
Khai báo 2 IP cùng nhãn `env: production`:
```json
[
  {
    "targets": [
      "10.0.0.51:8080",
      "10.0.0.52:8080"
    ],
    "labels": {
      "env": "production"
    }
  }
]
```

### 3. Tích hợp vào `prometheus/prometheus.yml`
```yaml
  - job_name: "microservices"
    file_sd_configs:
      - files:
          - "targets.json"
        refresh_interval: 30s
```

---

## BÀI TẬP 5: DEBUG APP BẰNG METRICS (SPRING BOOT ACTUATOR)

### 1. Nguyên nhân lỗi HTTP 404 `/actuator/prometheus`
- Spring Boot Actuator mặc định chỉ mở duy nhất endpoint `/health` qua Web để đảm bảo an toàn.
- Cấu hình ban đầu của đội Dev:
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: "health,info"
  ```
  Endpoint `prometheus` bị loại trừ nên Web Router không đăng ký URL `/actuator/prometheus`, dẫn tới lỗi HTTP 404 khi Prometheus scrape.

### 2. Cấu hình khắc phục (`src/main/resources/application.yml`)
```yaml
server:
  port: 8080

spring:
  application:
    name: quickbite-cart-service

management:
  endpoints:
    web:
      exposure:
        include: "health,info,prometheus"
      base-path: /actuator
  endpoint:
    health:
      show-details: always
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
```

### 3. Kiểm tra kết quả
- Gọi trực tiếp endpoint:
  ```bash
  curl -i http://localhost:8080/actuator/prometheus
  ```
  Nhận về mã phản hồi `HTTP/1.1 200 OK` kèm nội dung OpenMetrics chuẩn (`jvm_memory_used_bytes`, `jvm_threads_live_threads`, `http_server_requests_seconds_count`).
- Trên giao diện Prometheus tại `http://localhost:9090/targets`, target `cart-service` chuyển từ `DOWN` sang `UP` màu xanh lá.

---

## BÀI TẬP 6: TÍCH HỢP GIÁM SÁT VÀO CI/CD (FAIL-FAST GATEKEEPER)

### 1. Phân tích rủi ro & Nguyên lý Fail-Fast
Nếu kỹ sư gõ nhầm dấu tab hoặc sai cú pháp YAML trong cấu hình Prometheus, việc deploy trực tiếp sẽ làm sập container Prometheus trên Production. Để loại trừ rủi ro này, pipeline CI/CD bắt buộc phải có một cổng kiểm soát (Gatekeeper) chạy `promtool check config` trước khi cấp phép deploy.

### 2. Cấu hình Jenkinsfile (`Jenkinsfile`)
```groovy
pipeline {
    agent any

    stages {
        stage('Validate Config') {
            steps {
                echo '=== BẮT ĐẦU KIỂM TRA TÍNH HỢP LỆ CỦA CẤU HÌNH PROMETHEUS ==='
                script {
                    sh '''
                        docker run --rm -v "${WORKSPACE}/prometheus:/etc/prometheus" \
                            prom/prometheus:v2.51.0 promtool check config /etc/prometheus/prometheus.yml
                    '''
                    echo '>>> Cấu hình Prometheus và Rules hoàn toàn hợp lệ! Tiếp tục deploy...'
                }
            }
        }

        stage('Deploy') {
            steps {
                echo '=== BẮT ĐẦU TRIỂN KHAI RELOAD / RESTART PROMETHEUS ==='
                sh '''
                    curl -s -X POST http://localhost:9090/-/reload || docker restart prometheus
                '''
                echo '>>> Triển khai cấu hình giám sát thành công!'
            }
        }
    }

    post {
        failure {
            echo 'CRITICAL: Pipeline thất bại tại bước Validate! Không thực hiện restart container.'
        }
        success {
            echo 'SUCCESS: Toàn bộ quy trình kiểm tra và triển khai hoàn tất thành công.'
        }
    }
}
```

### 3. Cấu hình GitHub Actions (`.github/workflows/ci.yml`)
```yaml
name: Monitoring CI/CD Pipeline

on:
  push:
    branches: [ master, main ]
  pull_request:
    branches: [ master, main ]

jobs:
  validate-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Validate Prometheus Configuration and Rules
        run: |
          docker run --rm -v ${{ github.workspace }}/prometheus:/etc/prometheus \
            prom/prometheus:v2.51.0 promtool check config /etc/prometheus/prometheus.yml

      - name: Deploy Monitoring Configuration
        if: success()
        run: |
          echo "Prometheus config validation passed successfully. Ready for deployment!"
```

---

## TỔNG KẾT & HƯỚNG DẪN KIỂM THỬ TOÀN DIỆN

### 1. Cấu trúc mã nguồn hoàn chỉnh
```text
C:\Devop\15\
├── Jenkinsfile                             # Bài 6: CI/CD Pipeline với Stage Validate Config
├── .github/workflows/ci.yml                # Bài 6: GitHub Actions Workflow
├── docker-compose.yml                      # Bài 1, 2, 5: Cụm service cart-service, prometheus, grafana
├── Dockerfile                              # Multi-stage build cho Spring Boot cart-service
├── src/
│   ├── main/
│   │   ├── java/com/quickbite/cart/
│   │   │   └── CartServiceApplication.java # Bài 5: Xử lý endpoint /actuator/prometheus
│   │   └── resources/
│   │       └── application.yml             # Bài 5: Cấu hình exposure include prometheus
├── prometheus/
│   ├── prometheus.yml                      # Bài 2: Chu kỳ 15s | Bài 3: Rule files | Bài 4: file_sd
│   ├── alert_rules.yml                     # Bài 3: Ngưỡng CPU > 85%, for: 5m
│   └── targets.json                        # Bài 4: Khai báo 2 IP với nhãn env: production
├── grafana/
│   └── provisioning/
│       └── datasources/
│           └── datasource.yml              # Bài 1: Cấu hình URL http://prometheus:9090
└── README.md                               # Báo cáo kỹ thuật chi tiết toàn diện
```

### 2. Các lệnh vận hành nhanh
```bash
# 1. Khởi chạy toàn bộ hệ thống
docker compose up -d

# 2. Kiểm tra tính hợp lệ của cấu hình giám sát
docker run --rm -v "${PWD}/prometheus:/etc/prometheus" prom/prometheus:v2.51.0 promtool check config /etc/prometheus/prometheus.yml

# 3. Reload cấu hình không downtime
curl -X POST http://localhost:9090/-/reload

# 4. Kiểm tra metric endpoint của ứng dụng
curl http://localhost:8080/actuator/prometheus
```
