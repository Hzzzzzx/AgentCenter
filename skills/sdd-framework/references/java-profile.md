# Java 语言配置

本文件是 SDD（Specification-Driven Development）框架的 Java 语言适配层，为 Java/Spring Boot 项目提供具体的验证命令、代码规范、测试策略和项目结构指导。

---

## 1. 验证命令

### 编译

```bash
# Maven
mvn compile

# Gradle
gradle compileJava
```

### 测试

```bash
# Maven
mvn test

# Gradle
gradle test

# 运行单个测试类
mvn test -Dtest=UserServiceTest

# 运行单个测试方法
mvn test -Dtest=UserServiceTest#shouldSaveUser_whenValidInput

# Gradle
gradle test --tests "com.example.UserServiceTest.shouldSaveUser_whenValidInput"
```

### 代码检查

```bash
# Maven - Spotless（代码格式化）
mvn spotless:check

# Maven - Checkstyle（代码风格）
mvn checkstyle:check

# Maven - SpotBugs（静态分析）
mvn spotbugs:check

# Maven - 所有检查
mvn verify -Pchecks

# Gradle
gradle check
gradle spotlessCheck
gradle checkstyleMain
gradle spotbugsMain
```

### 打包

```bash
# Maven（跳过测试）
mvn package -DskipTests

# Gradle
gradle bootJar
gradle jar -x test
```

### 完整 CI 流水线

```bash
# Maven
mvn verify

# Gradle
gradle build
```

---

## 2. 禁止模式（Java 代码禁区）

### 类型安全

- **禁止**使用原始类型 `List list`，必须使用泛型 `List<String> list`
- **禁止**无理由的未检查类型转换，必须有 `@SuppressWarnings("unchecked")` 并附注释说明原因
- **禁止**滥用 `@SuppressWarnings("unchecked")`，每个 suppression 必须有业务理由
- **禁止**返回 `null`，优先使用 `Optional<T>`

### 异常处理

- **禁止**空 catch 块 `catch (Exception e) {}`，至少要记录日志
- **禁止**捕获通用 `Exception`，应捕获具体异常类型
- **禁止**吞掉异常而不做任何处理

### 日志输出

- **禁止**使用 `System.out.println()`，必须使用 SLF4J + Logback
- **禁止**在日志中使用字符串拼接，应用占位符 `log.info("userId={}", userId)`

### 并发与时间

- **禁止**在测试中使用 `Thread.sleep()`，使用 `Awaitility` 或 `ConditionTimeoutException`
- **禁止**使用 `new Date()`，必须使用 `java.time` API（`Instant`, `LocalDateTime`, `ZonedDateTime`）
- **禁止**可变静态状态无同步保护

### 字符串处理

- **禁止**在循环内使用字符串拼接 `str +=`，使用 `StringBuilder`
- **禁止**使用 `String.valueOf(null)` 而非显式检查

### 其他

- **禁止**使用 `//noinspection` 抑制检查
- **禁止**空方法体除非是接口默认实现
- **禁止**硬编码魔法值，应使用常量或配置

---

## 3. 测试策略

### 技术栈

| 层级 | 工具 |
|------|------|
| 单元测试 | JUnit 5 + Mockito + AssertJ |
| 集成测试 | Testcontainers（PostgreSQL, Redis, Kafka, MongoDB）|
| Spring 测试 | `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest` |
| 契约测试 | Spring Cloud Contract |
| 性能测试 | JMeter / Gatling |

### Mock 约定

```java
// Spring Bean 使用 @MockBean
@MockBean
private UserRepository userRepository;

// POJO 使用 Mockito.mock
private final UserService userService = Mockito.mock(UserService.class);
```

### 目录结构

```
src/test/java/
└── com/example/
    ├── service/
    │   └── UserServiceTest.java
    ├── controller/
    │   └── UserControllerTest.java
    └── repository/
        └── UserRepositoryTest.java
```

### 命名规范

- 测试类：`UserServiceTest.java` 或 `UserServiceShould.java`
- 测试方法：推荐 BDD 风格

```java
@Test
void shouldSaveUser_whenValidInput() { }

@Test
void shouldThrowException_whenUserNotFound() { }

@Test
void createUser_givenValidInput_returnsCreatedUser() { }
```

### Spring Boot 测试切片

```java
// Web 层单独测试（不含 Service）
@WebMvcTest(UserController.class)

// Service 层测试（不含 Repository）
@ServiceTest
UserServiceTest { }

// JPA 层测试（使用内存数据库或 Testcontainers）
@DataJpaTest
@Testcontainers
UserRepositoryTest { }

// 完整集成测试
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestcontainer
FullIntegrationTest { }
```

### Testcontainers 示例

```java
@Testcontainers
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

---

## 4. Spring Boot 约定

### 分层架构

```
Controller层：接收请求，参数校验，返回响应
    ↓
Service层：业务逻辑，事务边界
    ↓
Repository层：数据访问，数据库操作
```

### 依赖注入

**推荐：构造器注入**

```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 构造器注入（推荐）
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
}
```

**禁止：字段注入**

```java
// 禁止
@Autowired
private UserRepository userRepository;
```

### 配置管理

**推荐：`@ConfigurationProperties`**

```java
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {
    private String merchantId;
    private String apiKey;
    // getters and setters
}
```

**替代：`@Value`（仅用于简单值）**

```java
@Value("${app.feature.flags.new-checkout:false}")
private boolean newCheckoutEnabled;
```

### 异常处理

```java
// 自定义异常
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("User not found with id: " + userId);
    }
}

// 全局异常处理器
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }
}
```

### API 响应约定

```java
// 统一响应格式
public class ApiResponse<T> {
    private T data;
    private String error;
    private boolean success;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.data = data;
        response.success = true;
        return response;
    }
}

// Controller 返回
@RestController
public class UserController {

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable Long id) {
        UserDto user = userService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
```

### 参数校验

```java
// DTO 层使用 jakarta.validation
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotNull(message = "年龄不能为空")
    @Min(value = 18, message = "年龄必须大于等于18")
    private Integer age;
}

// Controller 启用校验
@PostMapping("/users")
public ResponseEntity<ApiResponse<UserDto>> createUser(
        @Valid @RequestBody CreateUserRequest request) {
    // ...
}
```

---

## 5. 构建与依赖

### Maven pom.xml 结构

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>my-app</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok（可选，但推荐减少样板代码）-->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>6.21.0</version>
            </plugin>
        </plugins>
    </build>

    <!-- Maven Profiles -->
    <profiles>
        <profile>
            <id>dev</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <spring.profiles.active>dev</spring.profiles.active>
            </properties>
        </profile>
        <profile>
            <id>prod</id>
            <properties>
                <spring.profiles.active>prod</spring.profiles.active>
            </properties>
        </profile>
    </profiles>
</project>
```

### Gradle build.gradle 结构

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.example'
version = '1.0.0-SNAPSHOT'

java {
    sourceCompatibility = '17'
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
}

test {
    useJUnitPlatform()
}

tasks.register('checkAll') {
    group = 'verification'
    dependsOn 'checkstyleMain', 'spotlessCheck', 'test'
}
```

### 依赖版本管理

使用 Spring Boot BOM 统一管理版本：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 6. 项目结构

### 标准 Maven 布局

```
my-app/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/
│   │   │       ├── MyAppApplication.java
│   │   │       ├── config/
│   │   │       │   ├── AppConfig.java
│   │   │       │   └── SecurityConfig.java
│   │   │       ├── controller/
│   │   │       │   └── UserController.java
│   │   │       ├── service/
│   │   │       │   ├── UserService.java
│   │   │       │   └── impl/
│   │   │       │       └── UserServiceImpl.java
│   │   │       ├── repository/
│   │   │       │   └── UserRepository.java
│   │   │       ├── model/
│   │   │       │   ├── entity/
│   │   │       │   │   └── User.java
│   │   │       │   ├── dto/
│   │   │       │   │   ├── CreateUserRequest.java
│   │   │       │   │   └── UserResponse.java
│   │   │       │   └── enums/
│   │   │       │       └── UserStatus.java
│   │   │       ├── exception/
│   │   │       │   ├── UserNotFoundException.java
│   │   │       │   └── GlobalExceptionHandler.java
│   │   │       └── mapper/
│   │   │           └── UserMapper.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/
│   │           └── migration/
│   │               └── V1__init.sql
│   └── test/
│       ├── java/
│       │   └── com/example/
│       │       ├── service/
│       │       │   └── UserServiceTest.java
│       │       └── controller/
│       │           └── UserControllerTest.java
│       └── resources/
│           └── application-test.yml
└── README.md
```

### 包组织方式

**方式一：按层分包（推荐用于小型项目）**

```
com.example/
├── controller/
├── service/
├── repository/
├── model/
└── config/
```

**方式二：按功能分包（推荐用于中大型项目）**

```
com.example/
├── user/
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   └── User.java
├── order/
│   ├── OrderController.java
│   ├── OrderService.java
│   └── Order.java
└── shared/
    ├── config/
    └── exception/
```

### 配置文件

**推荐：`application.yml` 而非 `application.properties`**

```yaml
# application.yml
spring:
  application:
    name: my-app
  profiles:
    active: dev
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true

server:
  port: 8080

logging:
  level:
    com.example: DEBUG
    org.springframework.web: INFO
```

---

## 附录：常用命令速查

| 操作 | Maven | Gradle |
|------|-------|--------|
| 编译 | `mvn compile` | `gradle compileJava` |
| 测试 | `mvn test` | `gradle test` |
| 单测 | `mvn test -Dtest=ClassName` | `gradle test --tests "ClassName"` |
| 打包 | `mvn package -DskipTests` | `gradle bootJar` |
| 格式化检查 | `mvn spotless:check` | `gradle spotlessCheck` |
| 格式化代码 | `mvn spotless:apply` | `gradle spotlessApply` |
| 完整验证 | `mvn verify` | `gradle build` |
| 运行 | `mvn spring-boot:run` | `gradle bootRun` |
| 清理 | `mvn clean` | `gradle clean` |

---

## 附录：Lombok 最佳实践

```java
// 推荐使用的 Lombok 注解
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    // 不要在 entity 上使用 @Data，它包含 equals/hashCode 可能导致问题
}
```

**禁止在 JPA Entity 上使用的注解**：
- `@Data`（包含 `equals`/`hashCode`，与 JPA 生命周期冲突）
- `@Setter`（应只在必要时手动控制 setter）

---

*本文件为 SDD 框架的 Java 语言配置，版本 1.0*
