package com.example.hangat.course.controller;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.model.PageResponse;
import com.example.hangat.common.security.CurrentUser;
import com.example.hangat.course.model.CourseDetailResponse;
import com.example.hangat.course.model.CourseSummaryResponse;
import com.example.hangat.course.service.CourseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 코스 조회 - 메인 추천 카드·저장 코스 목록에서 코스를 여는 진입점.
 * 상세는 비로그인도 열 수 있다(소유자 없는 임시·샘플 코스에 한해 - 서비스가 검증).
 */
@RestController
public class CourseQueryController {

    private final CourseQueryService courseQueryService;

    public CourseQueryController(CourseQueryService courseQueryService) {
        this.courseQueryService = courseQueryService;
    }

    @GetMapping("/courses")
    @Operation(summary = "저장 코스 목록", description = """
            내가 저장한 코스만 최근 저장 순으로 페이징해 반환한다(MY_001). 로그인 필수.
            삭제한 코스는 목록에 나오지 않는다.
            카드 모양은 메인 추천 코스(GET /main/courses)와 같은 계약이다.""")
    public BaseResponse<PageResponse<CourseSummaryResponse>> savedCourses(
            @PageableDefault(size = 10, sort = "savedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Long userId = CurrentUser.idOrNull();
        if (userId == null) {
            throw new BaseException(BaseResponseStatus.LOGIN_REQUIRED);
        }
        return BaseResponse.success(courseQueryService.savedCourses(userId, pageable));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "코스 상세", description = """
            일차별 방문 일정과 코스 요약을 반환한다.
            혼잡은 두 값을 함께 준다 - congestion_rate(지금 예보), planned_congestion_rate(저장 시점 스냅숏).
            예보가 갱신되면 두 값이 달라지며 화면이 그 변화를 알려줄 수 있다.
            editable=true면 이 사용자가 스왑·이름수정·삭제를 할 수 있다.
            소유자가 있는 저장 코스는 본인만 조회할 수 있고(3307), 비회원 임시·샘플 코스는 공개다.
            혼잡 예보는 날짜 단위로만 제공된다 (시간대 예측 없음).""")
    public BaseResponse<CourseDetailResponse> detail(@PathVariable Long courseId) {
        return BaseResponse.success(courseQueryService.detail(courseId, CurrentUser.idOrNull()));
    }
}
