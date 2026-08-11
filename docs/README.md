# docs

| 폴더/파일 | 용도 |
| --- | --- |
| `ddl/` | 엔티티 변경 시 생성된 DDL을 날짜별로 보관 (`2026-08-10_program_category.sql`) |
| `process-model.pdf` | 업무 프로세스 흐름도 원본 (팀에서 직접 추가) |
| `erd.md` 또는 이미지 | 확정된 ERD |

## 왜 DDL을 따로 남기나

로컬은 `ddl-auto: update` 로 편하게 개발하지만, 운영 DB는 `validate` 입니다.
운영에 반영할 SQL이 어딘가에는 정리되어 있어야 하고, 팀원 간 스키마 동기화에도 필요합니다.

Hibernate가 만든 DDL을 파일로 뽑으려면 로컬에서 아래 설정을 임시로 켜세요.

```yaml
spring:
  jpa:
    properties:
      jakarta:
        persistence:
          schema-generation:
            scripts:
              action: create
              create-target: build/schema.sql
```
