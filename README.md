# Camunda Workflow Application

Ứng dụng quản lý workflow với **Camunda BPM 7.21.0**, **Spring Boot 3.3.6**, và **Java 21**.

## 🚀 Tính năng

- ✅ **Camunda BPM Engine** - Process engine mạnh mẽ
- ✅ **Web UIs** - Cockpit, Tasklist, Admin
- ✅ **REST API** - Quản lý process và tasks
- ✅ **2 BPMN Workflows mẫu** - Simple process và Loan approval
- ✅ **Database** - H2 (dev) và PostgreSQL (production)
- ✅ **Docker Ready** - Triển khai nhanh với Docker Compose

## 📋 Yêu cầu

- Java 21
- Maven 3.6+
- Docker & Docker Compose (optional)
- Node.js (Frontend): >=20.19.0 (khuyến nghị 22.12.0 LTS)

## 🏃 Chạy ứng dụng

### Cách 1: Chạy với Maven (H2 Database)

```powershell
# Build project
mvn clean package

# Chạy application
mvn spring-boot:run
```

Ứng dụng sẽ chạy tại: http://localhost:8080

### Cách 2: Chạy với Docker (PostgreSQL Database)

```powershell
docker-compose up --build
```

## 🧩 Frontend (React)

Trong thư mục `frontend/`:

```powershell
cd frontend
npm install
npm run dev
```

### Frontend env vars

- `VITE_API_BASE_URL` (mặc định `http://localhost:8080`)
- `VITE_TENANT_ID` (optional) → gửi header `X-Tenant-Id`
- `VITE_API_KEY` (optional) → gửi header `X-API-Key` (chỉ áp dụng cho `/api/camel-routes` nếu backend bật API key)

## 🌐 Truy cập Web UIs

Sau khi ứng dụng chạy, truy cập:

| Service | URL | Credentials |
|---------|-----|-------------|
| **Camunda Cockpit** | http://localhost:8080/camunda/app/cockpit | admin / admin |
| **Camunda Tasklist** | http://localhost:8080/camunda/app/tasklist | admin / admin |
| **Camunda Admin** | http://localhost:8080/camunda/app/admin | admin / admin |
| **H2 Console** | http://localhost:8080/h2-console | sa / sa |

### Camunda Cockpit
- Giám sát process instances
- Xem process definitions
- Quản lý incidents

### Camunda Tasklist
- Xem và hoàn thành user tasks
- Quản lý task assignments

### Camunda Admin
- Quản lý users và groups
- Cấu hình authorizations

## 📡 REST API

### 1. Start Process Instance

```powershell
# Simple process
curl -X POST http://localhost:8080/api/workflow/start/simple-process `
  -H "Content-Type: application/json" `
  -d '{\"variables\": {\"customerName\": \"John Doe\"}}'

# Loan approval process
curl -X POST http://localhost:8080/api/workflow/start/loan-approval `
  -H "Content-Type: application/json" `
  -d '{\"variables\": {\"amount\": 50000, \"customer\": \"Jane Smith\"}}'
```

**Response:**
```json
{
  "processInstanceId": "abc-123-def",
  "processKey": "simple-process",
  "status": "started"
}
```

### 2. Get Tasks

```powershell
# Get tasks for admin user
curl http://localhost:8080/api/workflow/tasks?assignee=admin

# Get all unassigned tasks
curl http://localhost:8080/api/workflow/tasks
```

**Response:**
```json
[
  {
    "id": "task-123",
    "name": "Review Request",
    "assignee": "admin",
    "processInstanceId": "abc-123-def",
    "taskDefinitionKey": "Task_Review",
    "createTime": "2024-12-24T10:00:00Z"
  }
]
```

### 3. Complete Task

```powershell
curl -X POST http://localhost:8080/api/workflow/tasks/{taskId}/complete `
  -H "Content-Type: application/json" `
  -d '{\"variables\": {\"approved\": true, \"comment\": \"Approved\"}}'
```

### 4. Get Process Status

```powershell
curl http://localhost:8080/api/workflow/process/{processInstanceId}/status
```

**Response:**
```json
{
  "processInstanceId": "abc-123-def",
  "isActive": true,
  "status": "running"
}
```

### 5. Claim Task

```powershell
curl -X POST http://localhost:8080/api/workflow/tasks/{taskId}/claim `
  -H "Content-Type: application/json" `
  -d '{\"assignee\": \"admin\"}'
```

## 📊 BPMN Workflows

### 1. Simple Process (`simple-process`)

Workflow đơn giản để test:
- **Start** → **Review Request (User Task)** → **End**

### 2. Loan Approval (`loan-approval`)

Quy trình duyệt khoản vay:
1. **Start** - Bắt đầu đơn vay
2. **Submit Loan Application** - Nộp hồ sơ
3. **Review Application** - Xem xét hồ sơ
4. **Gateway Decision** - Quyết định duyệt/từ chối
   - Nếu `approved == true` → **Process Approved Loan**
   - Nếu không → **Send Rejection Notice**
5. **End** - Kết thúc

## 🗄️ Database

### H2 (Development)
- In-memory database
- H2 Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:./camunda-db`
- Username: `sa`
- Password: `sa`

### PostgreSQL (Production)
- Configured in `docker-compose.yml`
- Database: `camunda`
- Username: `camunda`
- Password: `camunda`

## 📁 Cấu trúc Project

```
cammuda/
├── src/main/
│   ├── java/com/workflow/camunda/
│   │   ├── CamundaWorkflowApplication.java
│   │   ├── controller/
│   │   │   └── WorkflowController.java
│   │   ├── service/
│   │   │   └── WorkflowService.java
│   │   └── dto/
│   │       ├── TaskDto.java
│   │       ├── ProcessStartRequest.java
│   │       └── TaskCompleteRequest.java
│   └── resources/
│       ├── application.yml
│       └── processes/
│           ├── simple-process.bpmn
│           └── loan-approval.bpmn
├── pom.xml
├── Dockerfile
└── docker-compose.yml
```

## 🔧 Configuration

Xem file `application.yml` để cấu hình:
- Database connection
- Camunda admin user
- Web apps settings
- Logging levels

## 🛠️ Development

### Tạo BPMN mới

1. Download [Camunda Modeler](https://camunda.com/download/modeler/)
2. Tạo BPMN diagram mới
3. Lưu vào `src/main/resources/processes/`
4. Set `isExecutable="true"` và process ID
5. Restart application

### Hot Reload

Application tự động deploy BPMN files khi khởi động.

## 📝 Logs

View logs trong console:
```powershell
# Maven
mvn spring-boot:run

# Docker
docker-compose logs -f camunda-app
```

## 🐛 Troubleshooting

### Port 8080 đã được sử dụng
```powershell
# Đổi port trong application.yml
server:
  port: 8081
```

### Database connection error
- Check PostgreSQL đang chạy
- Verify connection string trong `docker-compose.yml`

## 📚 Tài liệu

- [Camunda BPM Documentation](https://docs.camunda.org/manual/7.21/)
- [Spring Boot Integration](https://docs.camunda.org/manual/7.21/user-guide/spring-boot-integration/)
- [BPMN 2.0 Tutorial](https://camunda.com/bpmn/)

## 📄 License

MIT License
