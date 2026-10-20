package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 첨부파일(이미지/PDF) 저장·조회 공용 서비스. 모든 도메인이 재사용한다.
 *
 * 사용 흐름: {@link FileGroupService#createGroup()}으로 FileGroup을 먼저 만들고, 이 서비스로
 * 파일을 저장한 뒤, 자신의 엔티티에 fileGroup을 연결한다 (예: CareerDocument.fileGroup).
 * 구현체를 로컬 디스크({@link LocalFileStorageService}) 외 다른 저장소(S3 등)로 교체하더라도
 * 도메인 코드는 이 인터페이스에만 의존하므로 영향을 받지 않는다.
 */
public interface FileStorageService {

    /** 파일 1개를 검증 후 저장하고, 메타데이터를 담은 StoredFile을 반환한다. */
    StoredFile store(MultipartFile file, FileGroup fileGroup, Integer uploaderId);

    /** 여러 파일을 같은 FileGroup에 저장한다. */
    List<StoredFile> storeAll(List<MultipartFile> files, FileGroup fileGroup, Integer uploaderId);

    /** 다운로드 응답을 만드는 데 필요한 정보를 조회한다. */
    LoadedFile load(Integer storedFileId);

    /** 저장된 파일을 디스크와 DB에서 함께 제거한다. */
    void delete(StoredFile storedFile);

    /** 다운로드 응답을 만들 때 필요한 정보 묶음. */
    record LoadedFile(Resource resource, String originalFileName, String contentType) {
    }
}
