package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.repository.FileGroupRepository;
import com.gnagnoohc.scms.global.common.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 첨부파일 묶음(FileGroup) 생성/조회. 실제 파일 저장·검증은 {@link FileStorageService}가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class FileGroupService {

    private final FileGroupRepository fileGroupRepository;
    private final StoredFileRepository storedFileRepository;

    /** 새 첨부파일 묶음을 만든다. 도메인 엔티티는 이 FileGroup을 자신의 file_group_id로 참조한다. */
    @Transactional
    public FileGroup createGroup() {
        return fileGroupRepository.save(FileGroup.create());
    }

    /** 한 FileGroup에 속한 파일 목록을 업로드 순서(등록순)로 조회한다. */
    @Transactional(readOnly = true)
    public List<StoredFile> getFiles(FileGroup fileGroup) {
        return storedFileRepository.findByFileGroup_FileGroupId(fileGroup.getFileGroupId());
    }
}
