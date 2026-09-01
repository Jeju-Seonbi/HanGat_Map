package com.example.hangat.course.controller;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.security.CurrentUser;
import com.example.hangat.course.model.CourseSwapRequest;
import com.example.hangat.course.model.CourseSwapResponse;
import com.example.hangat.course.service.CourseSwapService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 코스 일정 스왑 - #과밀지역우회.
 *
 * <p>비로그인도 호출할 수 있다(코스 생성이 비로그인 허용이라 생성 직후 스왑도 열려 있어야 함).
 * 소유자가 있는 저장 코스는 서비스가 본인 여부를 검증한다.
 */
@RestController
public class CourseSwapController {

    private final CourseSwapService courseSwapService;

    public CourseSwapController(CourseSwapService courseSwapService) {
        this.courseSwapService = courseSwapService;
    }

    @PostMapping("/courses/{courseId}/items/{itemId}/swap")
    @Operation(summary = "코스 일정 스왑", description = """
            코스의 한 일정을 대안 장소로 제자리 교체한다. 순서(일차·번호)는 그대로 두고
            장소·근거·예보 스냅숏을 바꾸며, 교체된 일정과 그 다음 일정의 이동 거리·시간,
            코스 평균 집중률을 다시 계산해 돌려준다.
            후보는 GET /places/{placeId}/alternatives 에서 고른다.
            혼잡 예보는 날짜 단위로만 제공된다 (시간대 예측 없음).""")
    public BaseResponse<CourseSwapResponse> swap(
            @PathVariable Long courseId,
            @PathVariable Long itemId,
            @Valid @RequestBody CourseSwapRequest request) {
        return BaseResponse.success(
                courseSwapService.swap(courseId, itemId, request.placeId(), CurrentUser.idOrNull()));
    }

}
