package com.gnagnoohc.scms.global.common.helper;

import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * 첨부파일(이미지/PDF) 업로드 검증 헬퍼.
 *
 * 클라이언트가 보낸 파일명 확장자·Content-Type은 쉽게 위조할 수 있으므로, 파일 앞부분
 * 바이트(매직넘버)를 직접 읽어 실제 형식과 일치하는지 확인한다. 실행 파일 등을 이미지/PDF로
 * 위장해 업로드하는 것을 막기 위한 최소한의 방어이며, 알려진 악성코드 시그니처를 탐지하는
 * 백신 스캔은 아니다
 *
 * 그 전역 상한보다 더 엄격한 제한이 필요한
 * 화면만 크기를 받는 {@link #validate(MultipartFile, Set, long)}를 쓴다.
 */
@Component
public class FileUploadValidator {

    /** 매직바이트 검사를 지원하는, 즉 이 헬퍼가 실제로 검증 가능한 전체 확장자 목록. */
    public static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf");

    /** 기본 허용 확장자({@link #SUPPORTED_EXTENSIONS}) 전체를 기준으로, 크기 제한 없이 검증한다. */
    public void validate(MultipartFile file) {
        validate(file, SUPPORTED_EXTENSIONS);
    }

    /** 확장자를 좁혀서(예: 이미지만) 검증하되 크기는 별도로 제한하지 않는다. */
    public void validate(MultipartFile file, Set<String> allowedExtensions) {
        validateInternal(file, allowedExtensions, null);
    }

    /**
     * 전역 업로드 상한({@code spring.servlet.multipart.max-file-size})보다 더 엄격한 크기
     * 제한이 필요할 때만 사용한다.
     *
     * @param file              검증할 업로드 파일
     * @param allowedExtensions 허용할 확장자 (소문자, 점 없이). {@link #SUPPORTED_EXTENSIONS}의 부분집합이어야
     *                          매직바이트 검사도 함께 적용된다 — 예를 들어 사진만 받는 화면이면
     *                          {@code Set.of("jpg", "jpeg", "png")}처럼 더 좁혀서 넘기면 된다.
     * @param maxFileSize       허용 최대 크기(byte)
     */
    public void validate(MultipartFile file, Set<String> allowedExtensions, long maxFileSize) {
        validateInternal(file, allowedExtensions, maxFileSize);
    }

    private void validateInternal(MultipartFile file, Set<String> allowedExtensions, Long maxFileSize) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 비어 있습니다.");
        }

        String originalFileName = validateFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);

        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "허용되지 않은 파일 형식입니다: ." + extension);
        }

        if (maxFileSize != null && file.getSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        validateMagicBytes(file, extension);
    }

    private String validateFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_NAME, "파일명이 비어 있습니다.");
        }
        // 경로 조작(디렉터리 트래버설) 문자 차단. 실제 저장 파일명은 UUID를 쓰지만, 원본
        // 파일명은 DB에 남아 다운로드 응답 파일명으로도 노출되므로 여기서도 걸러둔다.
        if (originalFileName.contains("..") || originalFileName.contains("/") || originalFileName.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_NAME, "파일명에 허용되지 않은 문자가 포함되어 있습니다.");
        }
        return originalFileName;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "확장자가 없는 파일입니다.");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 파일 앞부분 바이트를 읽어 실제 형식이 (위조되지 않았다고 주장하는) 확장자와 일치하는지 확인한다.
     */
    private void validateMagicBytes(MultipartFile file, String extension) {
        byte[] header;
        try (var in = file.getInputStream()) {
            header = in.readNBytes(12);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "파일 내용을 읽는 중 오류가 발생했습니다.");
        }

        boolean matches = switch (extension) {
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38); // GIF87a / GIF89a
            case "webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46) // "RIFF"
                    && header.length >= 12
                    && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50; // "WEBP"
            case "pdf" -> startsWith(header, 0x25, 0x50, 0x44, 0x46); // "%PDF"
            default -> false; // SUPPORTED_EXTENSIONS 밖의 확장자는 매직바이트 검사 대상이 아님 — 항상 거부
        };

        if (!matches) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "파일 내용이 확장자(." + extension + ")와 일치하지 않습니다.");
        }
    }

    private boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
