transaction-processing-system/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .gitignore
├── .dockerignore
├── README.md
├── docker/
│   ├── prometheus/
│   │   └── prometheus.yml
│   ├── grafana/
│   │   ├── provisioning/
│   │   │   ├── datasources/datasource.yml
│   │   │   └── dashboards/dashboard.yml
│   │   └── dashboards/
│   │       └── transaction-processing-dashboard.json      [Part 6 ✓]
│   └── elk/
│       └── logstash.conf                                  [Part 6 ✓]
├── docs/
│   └── NEW_RELIC.md                                        [Part 6 ✓]
└── src/
    ├── main/
    │   ├── java/com/example/transactionprocessing/
    │   │   ├── TransactionProcessingSystemApplication.java [Part 2 ✓]
    │   │   ├── config/
    │   │   │   ├── KafkaTopicConfig.java                    [Part 4 ✓]
    │   │   │   ├── KafkaProducerConfig.java                 [Part 4 ✓]
    │   │   │   ├── KafkaConsumerConfig.java                 [Part 4 ✓]
    │   │   │   ├── RetryProperties.java                     [Part 4 ✓ — not in original
    │   │   │   │   plan; @ConfigurationProperties binding for app.retry.* so
    │   │   │   │   TransactionProcessingService/RetryService get typed access instead of
    │   │   │   │   scattered @Value injections]
    │   │   │   ├── RedisConfig.java                         [Part 2 ✓]
    │   │   │   ├── OpenApiConfig.java                       [Part 5 ✓]
    │   │   │   ├── WebConfig.java                           [Part 5 ✓]
    │   │   │   └── DevDataSeeder.java                       [Part 3 ✓]
    │   │   ├── security/                                     [Part 3 ✓, +1 in Part 5]
    │   │   │   ├── JwtProperties.java
    │   │   │   ├── JwtTokenProvider.java
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtAuthenticationEntryPoint.java
    │   │   │   ├── JwtAccessDeniedHandler.java                [Part 5 ✓ — not in original
    │   │   │   │   plan; needed because AuthenticationEntryPoint alone only covers 401s.
    │   │   │   │   URL-matcher-level 403s (hasRole("ADMIN") on /admin/**) happen in the
    │   │   │   │   filter chain before MVC dispatch, so GlobalExceptionHandler can't catch
    │   │   │   │   them — this handler + GlobalExceptionHandler's AccessDeniedException
    │   │   │   │   case together cover both the filter-chain and controller-dispatch layers.]
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── SecurityUtils.java
    │   │   │   ├── CustomUserDetails.java
    │   │   │   └── CustomUserDetailsService.java
    │   │   ├── auth/                                         [Part 3 ✓]
    │   │   │   ├── controller/AuthController.java
    │   │   │   ├── service/AuthService.java
    │   │   │   └── dto/ (RegisterRequest, LoginRequest, AuthResponse)
    │   │   ├── common/entity/BaseEntity.java                 [Part 2 ✓ — not in original
    │   │   │   plan's entity list; @MappedSuperclass providing UUID id + audit timestamps,
    │   │   │   shared by User/Account/Transaction]
    │   │   ├── user/
    │   │   │   ├── entity/User.java                          [Part 2 ✓]
    │   │   │   ├── entity/Role.java                          [Part 2 ✓]
    │   │   │   └── repository/UserRepository.java            [Part 2 ✓]
    │   │   ├── account/
    │   │   │   ├── entity/Account.java                       [Part 2 ✓]
    │   │   │   ├── repository/AccountRepository.java         [Part 2 ✓]
    │   │   │   ├── service/AccountService.java                [Part 4 ✓]
    │   │   │   ├── controller/AccountController.java          [Part 5 ✓]
    │   │   │   ├── mapper/AccountMapper.java                  [Part 5 ✓ — not in original
    │   │   │   │   plan; added for symmetry with transaction/mapper/TransactionMapper, both
    │   │   │   │   MapStruct interfaces (mapstruct already a Part 1 pom.xml dependency)]
    │   │   │   └── dto/ (CreateAccountRequest, AccountResponse) [Part 5 ✓]
    │   │   ├── transaction/
    │   │   │   ├── entity/Transaction.java                   [Part 2 ✓]
    │   │   │   ├── entity/TransactionStatus.java              [Part 2 ✓]
    │   │   │   ├── repository/TransactionRepository.java      [Part 2 ✓]
    │   │   │   ├── controller/TransactionController.java      [Part 5 ✓]
    │   │   │   ├── controller/AdminTransactionController.java [Part 5 ✓]
    │   │   │   ├── service/TransactionService.java            [Part 4 ✓]
    │   │   │   ├── service/TransactionProcessingService.java  [Part 4 ✓]
    │   │   │   ├── service/IdempotencyService.java            [Part 4 ✓]
    │   │   │   ├── service/RetryService.java                  [Part 4 ✓]
    │   │   │   ├── service/CreateTransactionCommand.java       [Part 4 ✓, internal command
    │   │   │   │   object — not a REST DTO; decouples TransactionService from Part 5's DTOs]
    │   │   │   ├── event/TransactionCreatedEvent.java          [Part 4 ✓]
    │   │   │   ├── event/TransactionProcessedEvent.java        [Part 4 ✓]
    │   │   │   ├── event/TransactionFailedEvent.java           [Part 4 ✓]
    │   │   │   ├── producer/TransactionEventProducer.java      [Part 4 ✓]
    │   │   │   ├── consumer/TransactionEventConsumer.java      [Part 4 ✓]
    │   │   │   ├── dto/request/CreateTransactionRequest.java   [Part 5 ✓]
    │   │   │   ├── dto/response/TransactionResponse.java       [Part 5 ✓]
    │   │   │   └── mapper/TransactionMapper.java               [Part 5 ✓]
    │   │   ├── audit/
    │   │   │   ├── entity/AuditLog.java                       [Part 2 ✓]
    │   │   │   ├── repository/AuditLogRepository.java          [Part 2 ✓]
    │   │   │   └── service/AuditService.java                   [Part 4 ✓]
    │   │   ├── metrics/
    │   │   │   └── TransactionMetrics.java                     [Part 6 ✓]
    │   │   └── common/                                       [Part 3-6 ✓ all complete]
    │   │       ├── logging/CorrelationIdFilter.java            [Part 6 ✓ — not in original
    │   │       │   plan; tags every log line of a request with a shared correlationId in MDC,
    │   │       │   which logback-spring.xml's JSON encoder picks up automatically. Pairs with
    │   │       │   docker/elk/logstash.conf for "find every log line of this one request" in
    │   │       │   Kibana via a single field filter instead of timestamp-proximity grepping.]
    │   │       ├── exception/
    │   │       │   ├── DuplicateResourceException, InvalidCredentialsException,
    │   │       │   │   ResourceNotFoundException                [Part 3 ✓]
    │   │       │   ├── InsufficientBalanceException, InvalidTransactionException,
    │   │       │   │   InvalidTransactionStateException, UnauthorizedAccountAccessException,
    │   │       │   │   IdempotencyInProgressException            [Part 4 ✓]
    │   │       │   └── GlobalExceptionHandler.java               [Part 5 ✓]
    │   │       └── response/ApiResponse.java                    [Part 3 ✓]
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-test.yml
    │       ├── logback-spring.xml                              [Part 6 ✓]
    │       └── db/migration/
    │           ├── V1__create_users.sql                         [Part 2 ✓]
    │           ├── V2__create_accounts.sql                      [Part 2 ✓]
    │           ├── V3__create_transactions.sql                  [Part 2 ✓]
    │           ├── V4__create_audit_logs.sql                    [Part 2 ✓]
    │           └── V5__create_indexes.sql                       [Part 2 ✓]
    └── test/
        ├── java/com/example/transactionprocessing/             [Part 7 ✓]
        │   ├── auth/AuthServiceTest.java                       (4 tests: register happy path,
        │   │   duplicate email, login happy path, bad credentials)
        │   ├── transaction/TransactionServiceTest.java          (8 tests: no-key happy path,
        │   │   same-src-dst, missing account, ownership, currency mismatch,
        │   │   idempotency fast path, lost-race-winner-committed, lost-race-in-flight)
        │   ├── transaction/IdempotencyServiceTest.java          (5 tests: get miss/hit,
        │   │   tryClaim win/lose, key-prefix+ttl verified)
        │   ├── transaction/TransactionConsumerTest.java         (3 tests: happy path,
        │   │   exception still acks, never propagates)
        │   ├── account/AccountLockingTest.java                  (5 tests: debit reduces,
        │   │   overdraft rejected, drain to zero, credit increases, lock-order enforced)
        │   ├── controller/TransactionControllerIntegrationTest  (7 tests: @WebMvcTest +
        │   │   @Import(GlobalExceptionHandler) + manual SecurityContext; create 201,
        │   │   amount=0 400, currency 400, empty body 400, get-own 200,
        │   │   get-not-own 403, ref-missing 404)
        │   └── repository/TransactionRepositoryTest.java        (8 tests: @DataJpaTest +
        │       Testcontainers PostgreSQL; findByRef, findByKey, key-miss, ref-unique,
        │       null-keys-dont-conflict, countByStatus, pageable, findByUserId,
        │       decimal-precision)
        └── resources/
            └── application-test.yml                            [Part 7 ✓ — in src/test/
                resources so it's found by all test slices without being on the main
                classpath; disables Redis auto-config to prevent context failure in
                @WebMvcTest slices that don't have a Redis server]

Legend: [Part N] = will be generated in that part of this build. Files without
a tag already exist (created in Part 1).
