package com.gnagnoohc.scms.domain.board.repository;

import com.gnagnoohc.scms.global.common.entity.BoardPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoardPostRepository extends JpaRepository<BoardPost, Integer>, BoardPostRepositoryCustom {

    // 파일 다운로드 API가 storedFile → fileGroup → 게시글을 역추적해 접근 권한을 판단할 때 사용.
    Optional<BoardPost> findByFileGroup_FileGroupId(Integer fileGroupId);
}
