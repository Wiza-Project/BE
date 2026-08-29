package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 도메인 독립적인 감사 이력 기록 모듈.
 *
 * <p>호출 도메인은 요청 본문이 아닌 인증된 {@code AuthUser#getId()}를 actorUserId로 전달해야 한다.
 * 기록 시각은 {@link AuditLog}의 {@code created_at}에 JPA Auditing으로 자동 저장된다.</p>
 *
 * <p>예: {@code auditLogService.recordAccess(authUser.getId(), "COUNSELING_PRIVATE_RECORD", sessionId);}
 * 호출은 실제 조회·변경이 성공한 뒤에 둔다. 실패도 이력으로 남겨야 하는 도메인은
 * {@link #recordFailure(Integer, String, Integer, AuditAction)}를 사용한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int RESOURCE_TYPE_MAX_LENGTH = 80;
    private static final int ACCESS_REASON_MAX_LENGTH = 500;
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogWriter auditLogWriter;
    private final AuditRequestMetadataProvider auditRequestMetadataProvider;

    /** 민감정보 또는 일반 리소스 열람 성공 이력을 기록한다. */
    public void recordAccess(Integer actorUserId, String resourceType, Integer resourceId) {
        recordSuccess(actorUserId, resourceType, resourceId, AuditAction.READ);
    }

    /** 증빙파일 등 파일 내려받기 성공 이력을 기록한다. */
    public void recordDownload(Integer actorUserId, String resourceType, Integer resourceId) {
        recordSuccess(actorUserId, resourceType, resourceId, AuditAction.DOWNLOAD);
    }

    /** 로그인 성공 이력을 기록한다. */
    public void recordLogin(Integer actorUserId) {
        recordSuccess(actorUserId, "AUTH", null, AuditAction.LOGIN);
    }

    /** 생성·수정·삭제 등 데이터 변경 성공 이력을 기록한다. */
    public void recordChange(Integer actorUserId, String resourceType, Integer resourceId, AuditAction action) {
        if (action != AuditAction.CREATE && action != AuditAction.UPDATE && action != AuditAction.DELETE
                && action != AuditAction.STATUS_CHANGE && action != AuditAction.APPROVE
                && action != AuditAction.REJECT && action != AuditAction.CANCEL
                && action != AuditAction.LOCK && action != AuditAction.DORMANT) {
            throw new IllegalArgumentException("변경 이력에는 상태를 변경하는 행위 코드만 사용할 수 있습니다.");
        }
        recordSuccess(actorUserId, resourceType, resourceId, action);
    }

    /**
     * 성공 로그를 기록한다. 업무 트랜잭션 안에서 호출되면 커밋 후에만 실제 INSERT를 수행한다.
     * 따라서 업무가 롤백되면 SUCCESS 로그도 남지 않는다.
     */
    public void recordSuccess(Integer actorUserId, String resourceType, Integer resourceId, AuditAction action) {
        AuditLog auditLog = create(actorUserId, resourceType, resourceId, action, AuditResult.SUCCESS, null);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeSafely(auditLog);
                }
            });
            return;
        }
        writeSafely(auditLog);
    }

    /**
     * 실패 로그를 즉시 독립 트랜잭션으로 저장한다. actorUserId는 로그인 ID를 찾지 못한 실패처럼
     * 알 수 없는 경우 null을 허용한다.
     */
    public void recordFailure(Integer actorUserId, String resourceType, Integer resourceId, AuditAction action) {
        writeSafely(create(actorUserId, resourceType, resourceId, action, AuditResult.FAILURE, null));
    }

    /** 접근 사유가 필요한 민감정보 열람에 사용한다. 요청 본문이나 민감 데이터 원문은 전달하지 않는다. */
    public void recordAccess(Integer actorUserId, String resourceType, Integer resourceId, String accessReason) {
        AuditLog auditLog = create(actorUserId, resourceType, resourceId, AuditAction.READ,
                AuditResult.SUCCESS, accessReason);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeSafely(auditLog);
                }
            });
            return;
        }
        writeSafely(auditLog);
    }

    private AuditLog create(Integer actorUserId, String resourceType, Integer resourceId,
                            AuditAction action, AuditResult result, String accessReason) {
        validate(actorUserId, resourceType, action, result);
        AuditRequestMetadata metadata = auditRequestMetadataProvider.current();
        if (metadata == null) {
            metadata = new AuditRequestMetadata(null, null);
        }
        return AuditLog.create(
                actorUserId, resourceType, resourceId, action.name(), result.name(),
                truncate(accessReason, ACCESS_REASON_MAX_LENGTH), metadata.ipAddress(), metadata.userAgent()
        );
    }

    private void writeSafely(AuditLog auditLog) {
        try {
            auditLogWriter.write(auditLog);
        } catch (RuntimeException e) {
            // 감사 로그의 장애가 정상 업무 처리 결과를 바꾸면 안 된다. 민감한 로그 내용은 남기지 않는다.
            log.warn("감사 로그 저장에 실패했습니다. action={}, resourceType={}, resourceId={}",
                    auditLog.getActionCode(), auditLog.getResourceType(), auditLog.getResourceId(), e);
        }
    }

    private void validate(Integer actorUserId, String resourceType, AuditAction action, AuditResult result) {
        if (resourceType == null || resourceType.isBlank() || resourceType.length() > RESOURCE_TYPE_MAX_LENGTH) {
            throw new IllegalArgumentException("감사 로그 리소스 유형은 1~80자여야 합니다.");
        }
        if (action == null || result == null) {
            throw new IllegalArgumentException("감사 로그 행위와 처리 결과는 필수입니다.");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
