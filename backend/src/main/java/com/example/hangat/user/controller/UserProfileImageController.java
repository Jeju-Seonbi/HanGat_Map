package com.example.hangat.user.controller;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.user.model.dto.UserDto.UserResponse;
import com.example.hangat.user.service.UserProfileImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 프로필 사진 API - 업로드와 조회 모두 로그인한 본인만 사용할 수 있다.
 * MinIO를 외부에 공개하지 않고 인증된 백엔드 응답으로 이미지를 전달한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/profile-image")
@Tag(name = "User")
public class UserProfileImageController {
    private final UserProfileImageService images;

    /** multipart의 file 한 개만 받고 사용자 ID는 요청값이 아닌 인증 정보에서 읽는다. */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "내 프로필 사진 변경", description = "JPEG/PNG/WebP, 최대 5MiB")
    public BaseResponse<UserResponse> upload(@AuthenticationPrincipal Long userId,
                                             @RequestPart("file") MultipartFile file) {
        return BaseResponse.success(images.upload(userId, file));
    }

    /** 브라우저·공유 캐시에 개인 사진을 남기지 않고 스트림은 응답 완료 시 닫힌다. */
    @GetMapping("/{filename}")
    @Operation(summary = "내 프로필 사진 조회")
    public ResponseEntity<InputStreamResource> read(@AuthenticationPrincipal Long userId,
                                                    @PathVariable String filename) {
        var stream = images.open(userId, filename);
        String extension = filename.substring(filename.lastIndexOf('.') + 1);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(extension.equals("jpg") ? "image/jpeg" : "image/" + extension))
                .body(new InputStreamResource(stream));
    }
}
