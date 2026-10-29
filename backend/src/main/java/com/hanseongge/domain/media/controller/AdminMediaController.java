package com.hanseongge.domain.media.controller;

import com.hanseongge.domain.media.ImageKind;
import com.hanseongge.domain.media.MediaStorage;
import com.hanseongge.domain.media.MediaStoreException;
import com.hanseongge.domain.media.dto.UploadedMediaResponse;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 관리자 이미지 업로드.
 *
 * <h2>왜 관리자 전용인가</h2>
 * 회원이 올릴 수 있는 이미지는 지금 없다(리뷰·문의 첨부는 범위 밖). 공개 업로드는
 * 용량·개수 제한과 바이러스 검사가 따라붙는 별개의 일이라, 필요해질 때 따로 만든다.
 *
 * <h2>올린 파일을 믿지 않는다</h2>
 * 파일명·확장자·Content-Type 은 전부 클라이언트가 보내는 값이다. 셋 다 쓰지 않고
 * {@link ImageKind#sniff} 로 실제 바이트를 읽어 판정하며, 저장 파일명은 서버가 새로 만든다.
 */
@Slf4j
@Tag(name = "관리자 이미지", description = "상품·이벤트 이미지 업로드")
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class AdminMediaController {

    /**
     * 키 앞머리로 쓸 수 있는 분류.
     *
     * <p>⚠️ <b>클라이언트가 보낸 값을 그대로 경로에 쓰지 않는다.</b> 목록에 없으면 거절한다 —
     * {@code ../../etc} 같은 값이 들어오면 저장 위치가 디스크 아무 곳이 된다.
     * {@code LocalFileMediaStorage} 도 같은 검사를 하지만, 거기서 걸리면 500 이 나고
     * 여기서 걸리면 이유가 담긴 400 이 나간다.
     */
    private static final Set<String> ALLOWED_FOLDERS = Set.of("products", "events", "notices");

    private final MediaStorage storage;
    private final AppProperties properties;

    @Operation(summary = "이미지 업로드",
            description = "성공하면 키와 공개 URL 을 돌려준다. 키를 상품·이벤트에 연결한다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadedMediaResponse>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(name = "folder", defaultValue = "products") String folder) {

        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "files 파라미터가 비어 있다");
        }
        if (!ALLOWED_FOLDERS.contains(folder)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 folder=" + folder);
        }
        if (files.size() > properties.media().maxCount()) {
            throw new BusinessException(ErrorCode.MEDIA_TOO_MANY,
                    "업로드 장수 초과 count=" + files.size());
        }

        // 검사를 먼저 다 돌린다. 한 장씩 저장하면서 검사하면 다섯 번째가 거절될 때
        // 앞 네 장이 이미 디스크에 남아, 상품에 연결되지 않은 파일이 쌓인다.
        List<Stored> ready = files.stream().map(file -> new Stored(read(file), kindOf(file))).toList();

        try {
            List<UploadedMediaResponse> uploaded = ready.stream()
                    .map(it -> UploadedMediaResponse.from(storage.store(folder, it.content(), it.kind())))
                    .toList();
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
        } catch (MediaStoreException e) {
            // 저장 실패는 디스크·권한 문제다. 올린 사람 잘못이 아니므로 사유를 노출하지 않는다.
            log.error("이미지 저장 실패 folder={}", folder, e);
            throw new BusinessException(ErrorCode.MEDIA_STORE_FAILED, e.getMessage());
        }
    }

    /** 검사를 통과한 한 장. */
    private record Stored(byte[] content, ImageKind kind) {}

    /**
     * 내용을 읽는다.
     *
     * <p>크기는 톰캣의 multipart 설정이 먼저 막지만(그쪽은 요청 전체 기준),
     * 장당 상한은 여기서 본다. 그렇지 않으면 "20MB 한 장"과 "2MB 열 장"이 같은 취급을 받는다.
     */
    private byte[] read(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "빈 파일");
        }
        if (file.getSize() > properties.media().maxBytesPerFile()) {
            throw new BusinessException(ErrorCode.MEDIA_TOO_LARGE, "용량 초과 bytes=" + file.getSize());
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            // 파일명은 클라이언트가 보낸 값이다 — 로그에만 남기고 응답에는 넣지 않는다.
            log.warn("업로드 내용을 읽지 못했다 name={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.MEDIA_STORE_FAILED, "multipart 읽기 실패");
        }
    }

    private ImageKind kindOf(MultipartFile file) {
        ImageKind kind = ImageKind.sniff(peek(file));
        if (kind == null) {
            // 클라이언트가 보낸 Content-Type 은 로그에만 남긴다. 응답 문구에 섞으면
            // 올린 사람이 보낸 문자열이 관리자 화면에 그대로 출력되는 경로가 된다.
            throw new BusinessException(ErrorCode.MEDIA_UNSUPPORTED_TYPE,
                    "이미지가 아닌 업로드 declaredType=" + file.getContentType());
        }
        return kind;
    }

    /** 판정에는 앞 12바이트면 충분하다. */
    private byte[] peek(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORE_FAILED, "multipart 앞머리 읽기 실패");
        }
    }
}
