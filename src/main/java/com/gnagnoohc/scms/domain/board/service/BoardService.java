package com.gnagnoohc.scms.domain.board.service;

import com.gnagnoohc.scms.domain.board.BoardConstants;
import com.gnagnoohc.scms.domain.board.dto.request.BoardPostCreateRequest;
import com.gnagnoohc.scms.domain.board.dto.request.BoardPostUpdateRequest;
import com.gnagnoohc.scms.domain.board.dto.response.BoardAttachmentResponse;
import com.gnagnoohc.scms.domain.board.dto.response.BoardCategoryResponse;
import com.gnagnoohc.scms.domain.board.dto.response.BoardPostDetailResponse;
import com.gnagnoohc.scms.domain.board.dto.response.BoardPostListItemResponse;
import com.gnagnoohc.scms.domain.board.repository.BoardPostRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.entity.BoardPost;
import com.gnagnoohc.scms.global.common.entity.BoardType;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.PostStatus;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.helper.FileUploadValidator;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.common.repository.StoredFileRepository;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 공지사항/FAQ 게시판 서비스. 새 테이블 없이 기존 board_post + common_code(FAQ_CATEGORY 그룹)만 사용한다.
 *   - 게시판 구분: board_post.board_type (NOTICE/FAQ)
 *   - 공지 범위: board_post.module_code (기본값 GLOBAL)
 *   - FAQ 카테고리: board_post.category_code → common_code(code_group='FAQ_CATEGORY').code (FK 없는 값 참조)
 *
 * 첨부파일은 게시글마다 전용 FileGroup을 새로 만들어 붙인다(다른 게시글과 공유하지 않음) -
 * 클라이언트가 fileGroupId를 직접 지정하는 경로는 없고, 등록/수정 요청에 실린 multipart
 * "files" part로만 추가하고 removeFileIds로만 제거한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    // 첨부파일은 확장자당 매직바이트 검증이 되는 범위(이미지+PDF)만 허용한다 - FileUploadValidator 참고.
    private static final int MAX_ATTACHMENT_COUNT = 5;
    // 일반 사용자에게 공개하는 상태값. 관리자는 DRAFT/HIDDEN도 함께 본다.
    private static final List<String> PUBLIC_STATUSES = List.of(PostStatus.PUBLISHED.name());
    private static final List<String> ALL_STATUSES =
            List.of(PostStatus.DRAFT.name(), PostStatus.PUBLISHED.name(), PostStatus.HIDDEN.name());

    private final BoardPostRepository boardPostRepository;
    private final AppUserRepository appUserRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final FileGroupService fileGroupService;
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final FileUploadValidator fileUploadValidator;

    // ── 읽기 ────────────────────────────────────────────────────────────

    public PageResponse<BoardPostListItemResponse> list(String boardTypeRaw, String moduleCode, String categoryCode,
                                                             String keyword, boolean staffView, Pageable pageable) {
        BoardType boardType = parseBoardType(boardTypeRaw);
        // FAQ에서 categoryCode로 필터링을 요청했다면, 존재하지도 않거나 비활성인 카테고리로 조용히
        // 빈 목록을 주지 않고 명확히 예외로 알린다.
        if (boardType == BoardType.FAQ && categoryCode != null) {
            requireActiveCategory(categoryCode);
        }
        List<String> allowedStatuses = staffView ? ALL_STATUSES : PUBLIC_STATUSES;
        Page<BoardPost> page = boardPostRepository.search(boardType, moduleCode, categoryCode, keyword, allowedStatuses, pageable);

        Map<String, String> categoryNames = boardType == BoardType.FAQ ? faqCategoryNameMap() : Map.of();
        return PageResponse.from(page.map(post -> BoardPostListItemResponse.from(post, categoryNames)));
    }

    public BoardPostDetailResponse getDetail(String boardTypeRaw, Integer postId, boolean staffView) {
        BoardType boardType = parseBoardType(boardTypeRaw);
        BoardPost post = getVisiblePost(boardType, postId, staffView);
        List<BoardAttachmentResponse> attachments = loadAttachments(post);
        String categoryName = post.getCategoryCode() == null ? null : lookupCategoryName(post.getCategoryCode());
        return BoardPostDetailResponse.from(post, attachments, categoryName);
    }

    /** FAQ만 실질적인 카테고리를 가진다 - NOTICE로 호출하면 빈 목록을 반환한다. */
    public List<BoardCategoryResponse> listCategories(String boardTypeRaw) {
        BoardType boardType = parseBoardType(boardTypeRaw);
        if (boardType != BoardType.FAQ) {
            return List.of();
        }
        return commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc(BoardConstants.FAQ_CATEGORY_CODE_GROUP)
                .stream()
                .map(BoardCategoryResponse::from)
                .toList();
    }

    // ── 게시글 관리(STAFF) ──────────────────────────────────────────────

    @Transactional
    public BoardPostDetailResponse createPost(String boardTypeRaw, BoardPostCreateRequest request,
                                                  List<MultipartFile> files, Integer authorUserId) {
        BoardType boardType = parseBoardType(boardTypeRaw);
        String categoryCode = resolveCategoryCodeForWrite(boardType, request.categoryCode());
        String moduleCode = resolveModuleCode(boardType, request.moduleCode());
        boolean pinned = validatePinRequest(boardType, request.pinned());
        PostStatus postStatus = parsePostStatus(request.postStatus(), PostStatus.PUBLISHED);

        AppUser author = appUserRepository.findById(authorUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        FileGroup fileGroup = hasFiles(files) ? createAndStoreFiles(files, authorUserId) : null;

        BoardPost post = BoardPost.create(boardType, moduleCode, categoryCode, author, request.title(),
                request.content(), fileGroup, pinned, postStatus);
        BoardPost saved = boardPostRepository.save(post);

        String categoryName = categoryCode == null ? null : lookupCategoryName(categoryCode);
        return BoardPostDetailResponse.from(saved, loadAttachments(saved), categoryName);
    }

    @Transactional
    public BoardPostDetailResponse updatePost(String boardTypeRaw, Integer postId, BoardPostUpdateRequest request,
                                                  List<MultipartFile> files, Integer actorUserId) {
        BoardType boardType = parseBoardType(boardTypeRaw);
        BoardPost post = getPostForWrite(boardType, postId);

        String categoryCode = Boolean.TRUE.equals(request.clearCategoryCode())
                ? null
                : request.categoryCode() != null
                        ? resolveCategoryCodeForWrite(boardType, request.categoryCode())
                        : post.getCategoryCode();
        String moduleCode = request.moduleCode() != null ? resolveModuleCode(boardType, request.moduleCode()) : null;

        Boolean pinned = request.pinned() == null ? null : validatePinRequest(boardType, request.pinned());
        PostStatus postStatus = request.postStatus() == null ? null : parsePostStatus(request.postStatus(), null);

        removeAttachments(post, request.removeFileIds());
        if (hasFiles(files)) {
            appendFiles(post, files, actorUserId);
        }
        // 첨부를 모두 제거했고 새로 추가한 파일도 없다면 빈 FileGroup을 계속 물고 있지 않도록 연결을 끊는다.
        if (post.getFileGroup() != null && fileGroupService.getFiles(post.getFileGroup()).isEmpty()) {
            post.attachFileGroup(null);
        }

        post.update(request.title(), request.content(), categoryCode, moduleCode, pinned, postStatus);

        String categoryName = categoryCode == null ? null : lookupCategoryName(categoryCode);
        return BoardPostDetailResponse.from(post, loadAttachments(post), categoryName);
    }

    @Transactional
    public void deletePost(String boardTypeRaw, Integer postId) {
        BoardType boardType = parseBoardType(boardTypeRaw);
        BoardPost post = getPostForWrite(boardType, postId);
        post.softDelete();
    }

    // ── 첨부파일 다운로드 ───────────────────────────────────────────────

    public FileStorageService.LoadedFile downloadFile(Integer storedFileId, boolean staffView) {
        // FileGroup은 여러 도메인이 공유하므로, storedFileId가 실제로 "게시판 글의" 첨부파일인지부터 확인한다.
        // 그렇지 않으면 이 엔드포인트로 다른 도메인(이력서/포트폴리오 등)의 파일까지 내려받을 수 있게 된다.
        // 권한 확인 전에는 디스크 접근(fileStorageService.load)을 하지 않는다.
        StoredFile storedFile = requireStoredFile(storedFileId);
        BoardPost post = boardPostRepository.findByFileGroup_FileGroupId(storedFile.getFileGroup().getFileGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!isVisible(post, staffView)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return fileStorageService.load(storedFileId);
    }

    // ── 내부 헬퍼 ───────────────────────────────────────────────────────

    private BoardType parseBoardType(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "boardType은 NOTICE 또는 FAQ여야 합니다.");
        }
        try {
            return BoardType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "boardType은 NOTICE 또는 FAQ여야 합니다.");
        }
    }

    private String resolveCategoryCodeForWrite(BoardType boardType, String categoryCode) {
        if (categoryCode == null) {
            return null;
        }
        if (boardType != BoardType.FAQ) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "카테고리는 FAQ 게시판에서만 사용할 수 있습니다.");
        }
        requireActiveCategory(categoryCode);
        return categoryCode;
    }

    private void requireActiveCategory(String categoryCode) {
        CommonCode category = commonCodeRepository
                .findByCodeGroupAndCode(BoardConstants.FAQ_CATEGORY_CODE_GROUP, categoryCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_CATEGORY_NOT_FOUND));
        if (!category.isActive()) {
            throw new BusinessException(ErrorCode.BOARD_CATEGORY_INACTIVE);
        }
    }

    private String lookupCategoryName(String categoryCode) {
        return commonCodeRepository.findByCodeGroupAndCode(BoardConstants.FAQ_CATEGORY_CODE_GROUP, categoryCode)
                .map(CommonCode::getCodeName)
                .orElse(null);
    }

    private Map<String, String> faqCategoryNameMap() {
        return commonCodeRepository
                .findByCodeGroupAndActiveTrueOrderBySortOrderAsc(BoardConstants.FAQ_CATEGORY_CODE_GROUP).stream()
                .collect(Collectors.toMap(CommonCode::getCode, CommonCode::getCodeName));
    }

    /** NOTICE는 모듈 범위를 클라이언트가 정할 수 있고(미지정 시 GLOBAL), FAQ는 module_code에 의미가 없어 항상 GLOBAL로 고정한다. */
    private String resolveModuleCode(BoardType boardType, String moduleCode) {
        if (boardType != BoardType.NOTICE) {
            return BoardConstants.DEFAULT_MODULE_CODE;
        }
        return moduleCode != null ? moduleCode : BoardConstants.DEFAULT_MODULE_CODE;
    }

    private boolean validatePinRequest(BoardType boardType, boolean pinned) {
        // 공지 상단 고정은 NOTICE 게시판에서만 허용한다.
        if (pinned && boardType != BoardType.NOTICE) {
            throw new BusinessException(ErrorCode.BOARD_PIN_NOT_SUPPORTED);
        }
        return pinned;
    }

    private PostStatus parsePostStatus(String raw, PostStatus fallbackWhenNull) {
        if (raw == null) {
            return fallbackWhenNull;
        }
        try {
            return PostStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "postStatus는 DRAFT/PUBLISHED/HIDDEN 중 하나여야 합니다.");
        }
    }

    private boolean hasFiles(List<MultipartFile> files) {
        return files != null && !files.isEmpty();
    }

    /** 게시글 등록 시 전용 FileGroup을 새로 만들어 파일을 저장한다. */
    private FileGroup createAndStoreFiles(List<MultipartFile> files, Integer uploaderId) {
        validateAttachmentCount(files.size());
        files.forEach(file -> fileUploadValidator.validate(file, FileUploadValidator.SUPPORTED_EXTENSIONS));

        FileGroup fileGroup = fileGroupService.createGroup();
        fileStorageService.storeAll(files, fileGroup, uploaderId);
        return fileGroup;
    }

    /** 게시글 수정 시 파일을 추가한다. 기존 FileGroup이 없으면 새로 만들어 연결한다. */
    private void appendFiles(BoardPost post, List<MultipartFile> files, Integer actorUserId) {
        FileGroup fileGroup = post.getFileGroup();
        int existingCount = fileGroup == null ? 0 : fileGroupService.getFiles(fileGroup).size();
        validateAttachmentCount(existingCount + files.size());
        files.forEach(file -> fileUploadValidator.validate(file, FileUploadValidator.SUPPORTED_EXTENSIONS));

        if (fileGroup == null) {
            fileGroup = fileGroupService.createGroup();
            post.attachFileGroup(fileGroup);
        }
        fileStorageService.storeAll(files, fileGroup, actorUserId);
    }

    private void validateAttachmentCount(int totalCount) {
        if (totalCount > MAX_ATTACHMENT_COUNT) {
            throw new BusinessException(ErrorCode.BOARD_ATTACHMENT_COUNT_EXCEEDED,
                    "첨부파일은 최대 " + MAX_ATTACHMENT_COUNT + "개까지 첨부할 수 있습니다.");
        }
    }

    /** removeFileIds로 지정된 첨부만 골라 지운다. 이 게시글의 첨부가 아닌 id가 섞여 있으면 예외를 던진다. */
    private void removeAttachments(BoardPost post, List<Integer> removeFileIds) {
        if (removeFileIds == null || removeFileIds.isEmpty()) {
            return;
        }
        if (post.getFileGroup() == null) {
            throw new BusinessException(ErrorCode.BOARD_ATTACHMENT_NOT_FOUND);
        }
        List<StoredFile> owned = fileGroupService.getFiles(post.getFileGroup());
        for (Integer storedFileId : removeFileIds) {
            StoredFile target = owned.stream()
                    .filter(f -> f.getStoredFileId().equals(storedFileId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_ATTACHMENT_NOT_FOUND));
            fileStorageService.delete(target);
        }
    }

    private BoardPost getPostForWrite(BoardType boardType, Integer postId) {
        BoardPost post = boardPostRepository.findDetail(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
        if (post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND);
        }
        if (post.getBoardTypeEnum() != boardType) {
            throw new BusinessException(ErrorCode.BOARD_POST_BOARD_MISMATCH);
        }
        return post;
    }

    private BoardPost getVisiblePost(BoardType boardType, Integer postId, boolean staffView) {
        BoardPost post = getPostForWrite(boardType, postId);
        if (!isVisible(post, staffView)) {
            throw new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND);
        }
        return post;
    }

    private boolean isVisible(BoardPost post, boolean staffView) {
        if (post.getDeletedAt() != null) {
            return false;
        }
        return staffView || post.getPostStatusEnum() == PostStatus.PUBLISHED;
    }

    private StoredFile requireStoredFile(Integer storedFileId) {
        return storedFileRepository.findById(storedFileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<BoardAttachmentResponse> loadAttachments(BoardPost post) {
        if (post.getFileGroup() == null) {
            return List.of();
        }
        return fileGroupService.getFiles(post.getFileGroup()).stream()
                .map(BoardAttachmentResponse::from)
                .toList();
    }
}
