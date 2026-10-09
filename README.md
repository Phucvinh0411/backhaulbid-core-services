# ☕ BackHaulBid Core Services

> **Hệ Thống Dịch Vụ Core & API Gateway (Spring Boot + Java 17 + PostgreSQL + Flyway)**
>
> BackHaulBid Core Services là kho lưu trữ tập hợp các dịch vụ nghiệp vụ nền tảng của hệ thống, được thiết kế theo mô hình Maven Multi-module. Các dịch vụ tương tác với cơ sở dữ liệu quan hệ PostgreSQL và thực hiện đồng bộ dữ liệu trạng thái không đồng bộ thông qua RabbitMQ.

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

*   **Ngôn ngữ & Runtime**: ![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.x-6DB33F?style=flat-square&logo=springboot&logoColor=white)
*   **API Gateway**: Spring Cloud Gateway (WebMvc/Reactive) chạy ở cổng `8080`.
*   **ORM / Database Access**: Spring Data JPA / Hibernate.
*   **Database Migration**: ![Flyway](https://img.shields.io/badge/Flyway-CC292B?style=flat-square&logo=flyway&logoColor=white)
*   **Cơ sở dữ liệu**: ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)
*   **Thư viện hỗ trợ**: Project Lombok, MapStruct.

---

## 📐 Nguyên Tắc Thiết Kế Cơ Sở Dữ Liệu (DB Design Rules)

Khi phát triển các thực thể (Entities) và thiết kế Schema trong Core Services, bắt buộc tuân thủ 3 nguyên tắc sau:

1.  **UUID Primary Key**: 
    Tất cả các khoá chính (Primary Key - PK) trong bảng cơ sở dữ liệu phải sử dụng kiểu dữ liệu `UUID` (kiểu chuỗi định danh ngẫu nhiên) để đảm bảo tính duy nhất trên toàn hệ thống phân tán, tránh trùng lặp khi đồng bộ và tăng tính bảo mật bảo vệ thông tin kinh doanh.
2.  **Role là Enum**:
    Quyền hạn và vai trò tài khoản (ví dụ: `SHIPPER`, `CARRIER`, `ADMIN`, `DRIVER`) phải được định nghĩa bằng kiểu dữ liệu `Enum` (Java Enumerated Types) và lưu xuống DB dưới dạng chuỗi đại diện (String) thay vì lưu ID số nguyên, nhằm duy trì tính tường minh và kiểm soát lỗi compile chặt chẽ.
3.  **Tuyệt đối KHÔNG sử dụng Base Entity (No Base Entity model)**:
    Mỗi thực thể Entity phải được khai báo các thuộc tính tường minh trực tiếp (như `id`, `created_at`, `updated_at`, v.v.). Nghiêm cấm tạo lớp cha `BaseEntity` hay `AbstractEntity` rồi kế thừa (`extends`), nhằm giữ mã nguồn phẳng (flat code), trực quan, dễ bảo trì và hạn chế tối đa các vấn đề liên quan đến cơ chế lazy-loading, proxy của Hibernate.

---

## 🏗️ Cấu Trúc Dự Án (Project Structure)

Dự án được tổ chức theo cấu trúc Maven Multi-module:

```text
backhaulbid-core-services/
├── api-gateway/            # Cổng Gateway (Port 8080) - Điểm vào duy nhất của hệ thống API
├── identity-service/       # Quản lý tài khoản, đăng nhập, phân quyền (PostgreSQL)
├── fleet-service/          # Quản lý đội xe, phương tiện, thông tin tài xế (PostgreSQL)
├── wallet-service/         # Quản lý ví điện tử, số dư, thực hiện phong tỏa/hoàn cọc (PostgreSQL)
├── contract-service/       # Tự động lập hợp đồng cam kết khi phiên đấu giá kết thúc (PostgreSQL)
├── pom.xml                 # Parent POM quản lý versions và dependencies chung
└── README.md
```

Mỗi module microservice (ngoại trừ `api-gateway`) chứa cấu trúc chuẩn Spring Boot:
*   `src/main/resources/db/migration/`: Chứa các script SQL của **Flyway** để versioning và tự động migrate DB (ví dụ: `V1__init_schema.sql`).
*   `src/main/java/`: Chứa mã nguồn phân theo package `controller`, `service`, `repository`, `entity`, `dto`, `exception`, `config`.

---

## 📌 Yêu Cầu Môi Trường (Prerequisites)

*   [Java Development Kit (JDK) 17](https://adoptium.net/temurin/releases/?version=17) trở lên.
*   [Apache Maven 3.8+](https://maven.apache.org/) hoặc sử dụng wrapper (`mvnw`).
*   Cơ sở dữ liệu **PostgreSQL** đang chạy (đã tạo sẵn các database cho từng service tương ứng).

---

## 🚀 Kích Hoạt Dự Án (Getting Started)

1.  **Clone repository và di chuyển vào thư mục:**
    ```bash
    git clone https://github.com/backhaulbid/backhaulbid-core-services.git
    cd backhaulbid-core-services
    ```

2.  **Biên dịch dự án và tải dependencies:**
    ```bash
    mvn clean install -DskipTests
    ```

3.  **Cấu hình kết nối Database:**
    Đảm bảo các database `backhaulbid_identity`, `backhaulbid_fleet`, `backhaulbid_wallet`, và `backhaulbid_contract` đã tồn tại trong PostgreSQL. Cập nhật file `application.yml` trong từng module hoặc cấu hình biến môi trường:
    ```env
    SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/backhaulbid_xxxx
    SPRING_DATASOURCE_USERNAME=postgres
    SPRING_DATASOURCE_PASSWORD=yourpassword
    ```

4.  **Khởi chạy các Microservices (Chạy lần lượt trên các tab Terminal):**
    ```bash
    # 1. Chạy API Gateway (Port 8080)
    mvn spring-boot:run -pl api-gateway

    # 2. Chạy Identity Service
    mvn spring-boot:run -pl identity-service

    # 3. Chạy Fleet Service
    mvn spring-boot:run -pl fleet-service

    # 4. Chạy Wallet Service
    mvn spring-boot:run -pl wallet-service

    # 5. Chạy Contract Service
    mvn spring-boot:run -pl contract-service
```
*Lưu ý: API Gateway sẽ đóng vai trò là điểm đón tiếp khách truy cập từ bên ngoài qua cổng **`8080`**.*

---

## 🐳 Triển Khai Với Docker

Mã nguồn chứa Dockerfile đa tầng (Multi-stage) để đóng gói từng module:

Build ảnh Docker cho từng service:
```bash
docker build --build-arg MODULE=api-gateway -t backhaulbid-api-gateway .
docker build --build-arg MODULE=identity-service -t backhaulbid-identity-service .
docker build --build-arg MODULE=fleet-service -t backhaulbid-fleet-service .
docker build --build-arg MODULE=wallet-service -t backhaulbid-wallet-service .
docker build --build-arg MODULE=contract-service -t backhaulbid-contract-service .
```
*(Hãy thay đổi cấu hình cổng trong application.yml hoặc biến môi trường khi chạy các container để kết nối liên thông trong Docker Network).*

## NFC eKYC — chế độ phát triển local

Identity-service cung cấp phiên QR dưới `/api/v1/representative-verifications/sessions`. `NFC_EKYC_ENABLED` mặc định tắt. Chủ hàng/Chủ xe ghép phiên với app native bằng cùng tài khoản và xác nhận trên web trước khi app nhận quyền gửi dữ liệu NFC.

Server xác thực chữ ký/hash của dữ liệu chip, kiểm tra định dạng và ngày MRZ, giới hạn ảnh, rồi mã hóa tóm tắt danh tính cùng ảnh bằng khóa cấu hình. Bằng chứng có thời hạn lưu; API owner chỉ trả các trường danh tính cần thiết. Raw DG/SOD/MRZ/APDU không được lưu.

`NFC_LOCAL_AUTOMATIC` mặc định là `DISABLED`. Khi giữ mặc định, hồ sơ không được tự động chấp nhận và cần xử lý theo luồng duyệt identity đã cấu hình. `COMPLETED_ONLY` hoặc `CAPTURE_COMPLETE` chỉ được bật cùng Spring profile `local`; startup bị chặn nếu dùng profile `prod`, `production` hoặc `staging`. Trong chế độ này, server có thể ghi `VERIFIED` với phương thức `LOCAL_NFC_DEVELOPMENT` khi các bước thu thập đạt chính sách đã chọn. Đây là chấp nhận nghiệp vụ dành riêng cho phát triển local; nó **không** chứng minh nguồn cấp chip, đối chiếu khuôn mặt hoặc liveness đã được xác minh độc lập.

Các cờ kết quả do client gửi không thay thế quyết định server. Khi rollback, tắt `NFC_EKYC_ENABLED` và khởi động lại identity-service; giữ các migration eKYC đã áp dụng trong rollback thông thường.
