package com.gnagnoohc.scms.global.common.repository;

import com.gnagnoohc.scms.global.common.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {
    List<StoredFile> findByFileGroup_FileGroupId(Integer fileGroupId);
}
