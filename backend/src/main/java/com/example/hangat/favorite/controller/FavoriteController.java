package com.example.hangat.favorite.controller;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.favorite.model.FavoriteResponse;
import com.example.hangat.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 내 찜 API.
 * 회원 ID는 URL이나 요청 본문이 아니라 검증된 로그인 정보에서 가져온다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/favorites")
@Validated
@Tag(name = "내 찜 목록", description = "로그인 회원의 찜한 장소")
public class FavoriteController {

    private final FavoriteService service;

    @GetMapping
    @Operation(summary = "내 찜 목록")
    public BaseResponse<PageResponse<FavoriteResponse>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "recent") String sort
    ) {
        return BaseResponse.success(service.list(userId, page, size, sort));
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "장소의 내 찜 여부")
    public BaseResponse<Boolean> contains(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Min(1) Long placeId
    ) {
        return BaseResponse.success(service.contains(userId, placeId));
    }

    @PutMapping("/{placeId}")
    @Operation(summary = "장소 찜", description = "이미 찜한 장소도 성공으로 처리한다.")
    public BaseResponse<Void> add(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Min(1) Long placeId
    ) {
        service.add(userId, placeId);
        return BaseResponse.success(null);
    }

    @DeleteMapping("/{placeId}")
    @Operation(summary = "장소 찜 해제", description = "이미 해제된 장소도 성공으로 처리한다.")
    public BaseResponse<Void> remove(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Min(1) Long placeId
    ) {
        service.remove(userId, placeId);
        return BaseResponse.success(null);
    }
}
