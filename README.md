# scms-be

**학생통합역량시스템** 백엔드 API 서버.

프론트엔드 레포: `scms-fe`
업무 프로세스 흐름도: `docs/process-model.pdf`

---

## 도메인

프로세스 흐름도의 5개 파트를 그대로 패키지로 옮겼습니다.

| Proc.ID | 프로세스 | Owner | 패키지 |
| --- | --- | --- | --- |
| P1100 | 비교과프로그램 등록 | 학생역량센터 | `domain/program` |
| P1200 | 비교과프로그램 운영 | 비교과운영부서 | `domain/program` |
| P2100 | 핵심역량정보 등록 | 학생역량센터 | `domain/competency` |
| P2200 | 진단검사 실시 | 학생역량센터 | `domain/competency` |
| P3100 | 상담준비 / 상담실시 | 학생역량센터 | `domain/counsel` |
| P4100 | 마일리지 항목관리·실적등록 | 학생역량센터 | `domain/mileage` |
| P5100 | 구인/구직 잡매칭 및 이력관리 | 취창업지원과 | `domain/career` |

각 패키지의 `package-info.java`에 해당 프로세스의 흐름도와 구현 체크리스트가 들어 있습니다.
**작업 시작 전에 반드시 읽어보세요.**

---

## 기술 스택

| 항목 | 버전 |
| --- | --- |
| Java | 17 (LTS) |
| Spring Boot | 4.0.7 |
| 영속성 | Spring Data JPA (Hibernate) |
| 동적 쿼리 | QueryDSL 5.1.0 |
| DB | PostgreSQL 18 |
| 인증 | Spring Security + JWT (jjwt 0.12.6) |
| API 문서 | springdoc-openapi (Swagger UI) |
| 엑셀 | Apache POI (취업통계, 운영현황) |
| PDF | OpenPDF (수료증 출력) |
| 빌드 | Gradle 8.14.5 |

---

## 로컬 실행

### 1. 사전 준비

JDK 17 설치 후 PostgreSQL에 DB 생성.

```sql
CREATE DATABASE scmsdb;
CREATE USER scms WITH PASSWORD '원하는비밀번호';
GRANT ALL PRIVILEGES ON DATABASE scmsdb TO scms;
-- PostgreSQL 15+ 는 스키마 권한도 별도로 필요합니다
\c scmsdb
GRANT ALL ON SCHEMA public TO scms;
```

### 2. 설정 파일 복사

두 파일 모두 `.gitignore` 대상입니다.

```bash
cd src/main/resources
cp application-local.yml.example application-local.yml
cp application-secret.yml.example application-secret.yml
```

JWT secret은 32바이트 이상이어야 합니다.

```bash
openssl rand -base64 48
```

### 3. 실행

```bash
./gradlew bootRun
```

- 연결 확인: http://localhost:8080/api/auth/ping
- Swagger UI: http://localhost:8080/swagger-ui.html

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

- 어떤 키/비밀번호도 `application.yml`에 직접 쓰지 않습니다.
- 커밋 전 `git diff --staged`로 비밀값 포함 여부를 확인하세요.
