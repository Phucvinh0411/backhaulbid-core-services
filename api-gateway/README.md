# 🚪 BackHaulBid API Gateway Service

> **Spring Cloud Gateway Service (Java 17, Spring Boot 4.1.0 & Spring Cloud 2025.1.2)**
>
> API Gateway đóng vai trò là điểm đón đầu (Single Entry Point) duy nhất của toàn bộ hệ thống BackHaulBid. Dịch vụ chạy ở cổng `8000`, chịu trách nhiệm tiếp nhận toàn bộ các yêu cầu HTTP/WebSocket từ các ứng dụng Client (Web Portal, Driver App), sau đó định tuyến (Routing) một cách an toàn tới các dịch vụ microservices phía sau.

---

## 🗺️ Bản Đồ Định Tuyến (Routing Table)

API Gateway được cấu hình định tuyến thông qua các mẫu đường dẫn (Path patterns) trỏ tới các service trong mạng nội bộ Docker:

| Đường dẫn yêu cầu (Request Path) | Dịch vụ đích (Target Service) | URL Nội bộ (Internal URL) | Vai trò |
| :--- | :--- | :--- | :--- |
| `/api/v1/auth/**`, `/api/v1/accounts/**`, `/api/v1/users/**` | **Identity Service** | `http://identity-service:8080` | Đăng ký, đăng nhập, hồ sơ người dùng |
| `/api/v1/fleets/**`, `/api/v1/vehicles/**`, `/api/v1/drivers/**` | **Fleet Service** | `http://fleet-service:8080` | Đội xe, tài xế, phương tiện |
| `/api/v1/wallets/**`, `/api/v1/transactions/**` | **Wallet Service** | `http://wallet-service:8080` | Ví tiền, cọc đấu giá, thanh toán |
| `/api/v1/contracts/**` | **Contract Service** | `http://contract-service:8080` | Sinh hợp đồng, ký kết điện tử |
| `/api/v1/bids/**`, `/bidding-socket/**` | **Bidding Service** | `http://bidding-service:3001` | Tạo phiên, đặt bước giá, Socket live |

---

## 🛠️ Công Nghệ Sử Dụng

*   **Java**: 17
*   **Framework**: Spring Boot 4.1.0 & Spring Cloud Gateway Server WebMvc
*   **Service Discovery**: Netflix Eureka Client (Mặc định được disable `eureka.client.enabled=false` khi chạy môi trường Docker cục bộ).

---

## 🚀 Hướng Dẫn Kích Hoạt Cục Bộ

### 📌 Yêu Cầu Trước Khi Chạy
1.  Đã cài đặt **JDK 17** và Maven.
2.  Các microservices đích đã được khởi chạy độc lập (trong docker hoặc chạy local).

### 💻 Chạy Local (Từ thư mục cha `backhaulbid-core-services`)
Mở terminal và chạy lệnh:
```bash
mvn spring-boot:run -pl api-gateway
```
*API Gateway sẽ lắng nghe tại cổng: `http://localhost:8000`*

---

## 🐳 Đóng Gói Với Docker (Containerization)

Dịch vụ sử dụng chung Dockerfile đa tầng ở thư mục gốc của `backhaulbid-core-services`. 

Build Docker Image:
```bash
# Đứng tại thư mục backhaulbid-core-services:
docker build --build-arg MODULE=api-gateway -t backhaulbid-api-gateway .
```

Khởi chạy Container độc lập:
```bash
docker run -d \
  -p 8000:8000 \
  --name api-gateway-container \
  --network backhaulbid-network \
  backhaulbid-api-gateway
```
