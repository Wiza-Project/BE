# scms-be

**학생통합역량시스템** 백엔드 API 서버.

프론트엔드 레포: `scms-fe`
업무 프로세스 흐름도: `docs/process-model.pdf`

---

## 도메인

프로세스 흐름도의 5개 파트를 그대로 패키지로 옮겼습니다.

| 프로세스 | Owner | 패키지 |
| --- | --- | --- |
| 비교과프로그램 등록 | 학생역량센터 | `domain/program` |
| 비교과프로그램 운영 | 비교과운영부서 | `domain/program` |
| 핵심역량정보 등록 | 학생역량센터 | `domain/competency` |
| 진단검사 실시 | 학생역량센터 | `domain/competency` |
| 상담준비 / 상담실시 | 학생역량센터 | `domain/counsel` |
| 마일리지 항목관리·실적등록 | 학생역량센터 | `domain/mileage` |
| 구인/구직 잡매칭 및 이력관리 | 취창업지원과 | `domain/career` |

각 패키지의 `package-info.java`에 해당 프로세스의 흐름도와 구현 체크리스트가 들어 있습니다.
**작업 시작 전에 반드시 읽어보세요.**

---

## 공통코드

여러 도메인이 공유하는 드롭다운/옵션 값은 `common_code` 테이블 하나로 관리합니다.
`GET /api/common-codes?groupCode={그룹코드}`로 조회하면 활성 코드만 `sortOrder` 순으로 내려줍니다
(로그인만 하면 역할 무관하게 조회 가능). `code`(문자열)만 안정적인 값이고 `codeId`는 환경마다
다를 수 있으니, FK로 넘길 `codeId`는 항상 이 API로 그때그때 조회해서 쓰세요 — 하드코딩 금지.

코드값은 접두어+100단위 형식입니다(나중에 세분류를 중간에 끼워넣을 여유를 두기 위함).

| 그룹코드 (groupCode) | 설명 | 코드 예시 |
| --- | --- | --- |
| `PROGRAM_TYPE` | 비교과프로그램 유형. `ExtracurricularProgram.programTypeCodeId`가 참조 | `PT100` 학습, `PT200` 공모전, `PT300` 진로창업, `PT400` 심리상담, `PT500` 사회봉사, `PT600` 국제화 |
| `DEPARTMENT` | 부서/운영 단위. `AppUser.departmentCode`, `ExtracurricularProgram.operatingUnitCodeId`가 참조 | `D100` 학생역량센터, `D200` 비교과운영부서, `D300` 진로심리상담센터, `D400` 취창업지원과 |
| `ACADEMIC_YEAR` | 학년도. FK는 아니고(각 엔티티는 스칼라 Integer로 저장) 드롭다운 선택지 기준값 | `2024`~`2027` |
| `SEMESTER` | 학기. FK는 아니고(각 엔티티는 스칼라 String으로 저장) 드롭다운 선택지 기준값 | `SPRING` 1학기, `SUMMER` 여름학기, `FALL` 2학기, `WINTER` 겨울학기 |

초기 시드는 `CommonCodeSeeder`(local 프로필, 앱 기동 시 자동)와 `docs/ddl/2026-08-20_common_code_seed.sql`
(운영 반영용, 수동 실행) 양쪽에 있습니다 — 그룹/코드를 추가·변경하면 **두 곳 다** 같이 고치세요
(자동 동기화 아님). "상담유형"/"마일리지 활동유형"은 각자 전용 테이블(`counseling_type`,
`mileage_activity_type`)이 있어 여기 포함하지 않았습니다.

---

## 기술 스택

| 항목 | 버전                                  |
| --- |-------------------------------------|
| Java | 17 (LTS)                            |
| Spring Boot | 4.0.7                               |
| 영속성 | Spring Data JPA (Hibernate)         |
| 동적 쿼리 | QueryDSL 5.1.0                      |
| DB | PostgreSQL 16 (pgvector)            |
| 컨테이너 | Docker Desktop / Docker Compose (개발 DB 가동) |
| 인증 | Spring Security + JWT (jjwt 0.12.6) |
| API 문서 | springdoc-openapi (Swagger UI)      |
| 엑셀 | Apache POI (취업통계, 운영현황)             |
| PDF | OpenPDF (수료증 출력)                    |
| 빌드 | Gradle 8.14.5                       |

로컬 개발용 DB(PostgreSQL + pgvector)는 프로젝트 루트의 `docker-compose.yml`을 통해 컨테이너 환경으로 실행됩니다.

---

## 로컬 실행

### 1. 사전 준비

- JDK 17 이상 설치
- Docker Desktop 실행(엔진이 켜진 상태인지 확인해주세요)

### 2. 설정 파일 복사 및 환경 세팅

세 파일 모두 `.gitignore` 대상입니다.

```bash
cd src/main/resources
cp application-local.yml.example application-local.yml
cp application-secret.yml.example application-secret.yml

# env파일은 백엔드 최상위 루트 디렉토리에서 실행하세요
cp .env.example .env
```
생성된 .env 파일 내 DB 정보 및 JWT secret 키 값을 본인 환경에 맞춰 적어주세요.
JWT secret은 32바이트 이상이어야 합니다.

```bash
openssl rand -base64 48
```

docker Desktop이 실행 중이라면, 아래 명령으로 로컬 DB 컨테이너를 실행합니다.

```bash
# docker-compose.yml이 있는 루트 디렉토리에서 실행하세요
docker compose up -d
```

Docker Desktop의 Containers 탭에서 컨테이너 실행 되었는지 확인해주세요.

### 3. DB 툴 연결 방법
DB 관리 툴에서 가동된 Docker DB에 접속해주세요.

PostgreSQL
Host: localhost
Port: 본인의 .env에 설정한 DB_PORT (예: 5433, 5334, 5678 등 중복되지 않으면서도 본인이 편한 port)
User: .env에 작성한 DB_USER
Password: .env에 작성한 DB_PASSWORD
Database: .env에 작성한 DB_NAME
Test Connection (연결 테스트) 클릭하여 연결 확인 후 적용

### 4. 실행

```bash
./gradlew bootRun
```

- 연결 확인: http://localhost:${SERVER_PORT}OR9999/api/auth/ping
- Swagger UI: http://localhost:${SERVER_PORT}OR9999/swagger-ui.html

---

## 패키지 구조

```
com.gnagnoohc.scms
├─ ScmsApplication.java
├─ global/                          공통 인프라
│  ├─ config/                       Security, Cors, Jpa(Auditing), Swagger
│  ├─ common/
│  │  ├─ entity/                    BaseTimeEntity, BaseEntity(작성자 기록)
│  │  ├─ dto/                       ApiResponse, PageResponse
│  │  └─ enums/                     ApprovalStatus, UserType, Department
│  ├─ error/                        ErrorCode, BusinessException, GlobalExceptionHandler
│  └─ security/                     JwtTokenProvider, JwtAuthenticationFilter, AuthUser
└─ domain/
   ├─ user/                         계정·인증 (전 액터 공통)
   ├─ program/                      비교과프로그램      P1100, P1200
   ├─ competency/                   핵심역량·진단검사   P2100, P2200
   ├─ counsel/                      상담관리            P3100
   ├─ mileage/                      마일리지            P4100
   └─ career/                       취창업관리          P5100
```

도메인마다 `controller / service / repository / entity / dto` 5개 폴더를 둡니다.

---

## 권한 설계 (중요)

사용자 유형이 **4종**(학생/교직원/상담사/기업체)이고, 같은 교직원이라도
소속 부서에 따라 할 수 있는 일이 다릅니다. 그래서 **2단 구조**로 검사합니다.

**1단계 — URL 패턴 + UserType** (`SecurityConfig`)

```
/api/students/**    → ROLE_STUDENT
/api/counselors/**  → ROLE_COUNSELOR
/api/companies/**   → ROLE_COMPANY
/api/admin/**       → ROLE_STAFF
```

**2단계 — 부서 판정** (서비스 계층)

```java
if (!authUser.getDepartment().canApproveProgramCategory()) {
    throw new BusinessException(ErrorCode.DEPARTMENT_FORBIDDEN);
}
```

URL 패턴에 부서까지 넣으려 하지 마세요. 규칙이 폭발합니다.
부서 판정 로직은 `Department` enum의 `canXxx()` 메서드에 모아두었습니다.

---

## API 응답 규약

```jsonc
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "code": "P004", "message": "모집 정원이 초과되었습니다." }
```

에러 코드 체계는 `global/error/ErrorCode.java` 참조.
접두사가 도메인을 나타냅니다 (`P`=프로그램, `Q`=역량진단, `S`=상담, `M`=마일리지, `J`=취창업).

**새 에러 코드를 추가하면 프론트 담당자에게 반드시 공유하세요.**

---

## 개발 규칙

### 브랜치

```
main                              배포 가능 상태
develop                           통합 브랜치
feature/P1200-program-approval    작업 브랜치 (프로세스 ID 사용)
```

### 커밋 메시지

```
feat: 비교과프로그램 참여승인 API 구현
fix: 상담 예약 동시성 문제 수정
refactor: 마일리지 적립 로직 이벤트 방식으로 분리
chore: 의존성 추가
```

### JPA 주의사항

- `ddl-auto`는 로컬만 `update`, 그 외는 `validate`. **운영에 `create`/`update` 절대 금지.**
- `open-in-view: false`이므로 컨트롤러에서 엔티티를 직접 반환하지 말고 **DTO로 변환**하세요.
- 엔티티에 `@Setter`를 붙이지 않습니다. 의미 있는 메서드로만 상태를 변경하세요.
- 목록 조회 N+1은 `fetch join` 또는 QueryDSL로 명시 해결하세요.
- 엔티티 변경 시 DDL을 `docs/ddl/` 에 날짜별로 남깁니다.

### 도메인 간 결합 (이 프로젝트의 핵심 주의사항)

프로세스 흐름도상 도메인이 서로 얽혀 있습니다.

```
비교과 이수 확정 ──▶ 마일리지 자동 적립
진단검사 결과   ──▶ 추천 비교과프로그램 조회
```

`ProgramService`에서 `MileageService`를 직접 호출하면 순환 의존이 생기고,
나중에 어느 한쪽만 수정하기가 매우 어려워집니다.
**Spring의 `ApplicationEventPublisher`로 이벤트를 발행하고, 마일리지 쪽에서 구독하세요.**

```java
// program 도메인
eventPublisher.publishEvent(new ProgramCompletedEvent(studentId, programId, mileagePoint));

// mileage 도메인
@TransactionalEventListener
public void handle(ProgramCompletedEvent event) { ... }
```

### 개인정보 취급

이 시스템은 **민감정보**를 다룹니다.

| 데이터 | 민감도 | 접근 가능 대상 |
| --- | --- | --- |
| 상담결과 (P3100) | 매우 높음 | 담당 상담사, 학생 본인 |
| 진단검사 결과 (P2200) | 높음 | 학생 본인, 학생역량센터 |
| 이력서·자기소개서 (P5100) | 높음 | 학생 본인, 취창업지원과 |

- 교직원(STAFF)이라는 이유만으로 상담결과를 볼 수 있으면 안 됩니다.
- 조회 이력 로깅과 보관 기간·파기 정책을 학교 규정에 맞춰 정하세요.

### 보안

- 어떤 키/비밀번호도 `application.yml, application-secret.yml`에 직접 쓰지 않습니다. `.env`파일에 적어주세요. 
- 커밋 전 `git diff --staged`로 비밀값 포함 여부를 확인하세요.
