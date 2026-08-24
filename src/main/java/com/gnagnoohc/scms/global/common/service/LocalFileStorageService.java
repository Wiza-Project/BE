package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.repository.StoredFileRepository;
import com.gnagnoohc.scms.global.common.util.FileValidator;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code app.file.upload-dir} 하위, 파일 그룹({@link FileGroup#getFileGroupId()})별 하위
 * 디렉터리에 실제 파일을 저장하는 로컬 디스크 구현체.
 *
 * 저장 경로: {@code {upload-dir}/{fileGroupId}/{UUID}.{ext}}
 * - fileGroupId로 하위 디렉터리를 나눠 한 첨부 묶음의 파일들을 한 곳에 모은다.
 * - 실제 저장 파일명은 UUID로 발급해 파일명 충돌/경로 조작을 막고, 원본 파일명은
 *   {@link StoredFile#getOriginalFileName()}에 별도로 보관했다가 다운로드 응답에 사용한다.
 */
@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final StoredFileRepository storedFileRepository;
    private final MalwareScanner malwareScanner;
    private final Path uploadRoot;

    public LocalFileStorageService(
            StoredFileRepository storedFileRepository,
            MalwareScanner malwareScanner,
            @Value("${app.file.upload-dir}") String uploadDir
    ) {
        this.storedFileRepository = storedFileRepository;
        this.malwareScanner = malwareScanner;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public StoredFile store(MultipartFile file, FileGroup fileGroup, Integer uploaderId) {
        // 크기 상한은 spring.servlet.multipart.max-file-size가 이미 요청 단계에서 강제한다
        // (여기 도달했다는 건 이미 통과했다는 뜻). 더 엄격한 제한이 필요한 도메인은
        // FileValidator.validate(file, allowedExtensions, maxFileSize)를 직접 호출한다.
        FileValidator.validate(file, FileValidator.SUPPORTED_EXTENSIONS);
        malwareScanner.scan(file);

        String extension = extractExtension(file.getOriginalFilename());
        String storageKey = fileGroup.getFileGroupId() + "/" + UUID.randomUUID() + "." + extension;
        Path targetPath = resolveWithinRoot(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            log.error("파일 저장 실패 - storageKey={}", storageKey, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        StoredFile storedFile = StoredFile.builder()
                .fileGroup(fileGroup)
                .originalFileName(file.getOriginalFilename())
                .storageKey(storageKey)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .createdBy(uploaderId)
                .build();

        return storedFileRepository.save(storedFile);
    }

    @Override
    @Transactional
    public List<StoredFile> storeAll(List<MultipartFile> files, FileGroup fileGroup, Integer uploaderId) {
        return files.stream()
                .map(file -> store(file, fileGroup, uploaderId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoadedFile load(Integer storedFileId) {
        StoredFile storedFile = storedFileRepository.findById(storedFileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Path path = resolveWithinRoot(storedFile.getStorageKey());
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("저장된 파일을 디스크에서 찾을 수 없음 - storedFileId={}, storageKey={}",
                        storedFileId, storedFile.getStorageKey());
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return new LoadedFile(resource, storedFile.getOriginalFileName(), storedFile.getContentType());
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void delete(StoredFile storedFile) {
        Path path = resolveWithinRoot(storedFile.getStorageKey());
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 디스크 파일 삭제가 실패해도 메타데이터는 지운다 — 고아 파일은 남지만 운영 중 예외로
            // 인해 삭제 자체가 막히는 것보다 낫다. 로그로 남겨 별도 정리할 수 있게 한다.
            log.warn("파일 삭제 실패 - storageKey={}", storedFile.getStorageKey(), e);
        }
        storedFileRepository.delete(storedFile);
    }

    /** storageKey는 서버가 UUID로 직접 생성하므로 이론상 벗어날 수 없지만, 방어적으로 한 번 더 막아둔다. */
    private Path resolveWithinRoot(String storageKey) {
        Path resolved = uploadRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "잘못된 저장 경로입니다.");
        }
        return resolved;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
