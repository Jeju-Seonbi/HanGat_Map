package com.example.hangat.course.controller;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.security.CurrentUser;
import com.example.hangat.course.model.CourseRenameRequest;
import com.example.hangat.course.model.CourseSummaryResponse;
import com.example.hangat.course.service.CourseCommandService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 저장 코스 관리 (MY_001) - 이름 변경·삭제. 둘 다 로그인 + 본인 코스만. */
@RestController
public class CourseCommandController {

    private final CourseCommandService courseCommandService;

    public CourseCommandController(CourseCommandService courseCommandService) {
        this.courseCommandService = courseCommandService;
    }

    @PatchMapping("/courses/{courseId}")
    @Operation(summary = "저장 코스 이름 변경",
            description = "본인이 저장한 코스의 이름만 바꾼다. 일정은 스왑 API로 바꾼다.")
    public BaseResponse<CourseSummaryResponse> rename(
            @PathVariable Long courseId,
            @Valid @RequestBody CourseRenameRequest request) {
        return BaseResponse.success(
                courseCommandService.rename(courseId, request.title(), requireLogin()));
    }

    @DeleteMapping("/courses/{courseId}")
    @Operation(summary = "저장 코스 삭제", description = """
            논리 삭제라 되돌릴 여지를 남긴다. 이미 지운 코스를 또 지워도 성공으로 답한다(멱등).""")
    public BaseResponse<Void> delete(@PathVariable Long courseId) {
        courseCommandService.delete(courseId, requireLogin());
        return BaseResponse.success(null);
    }

    private Long requireLogin() {
        Long userId = CurrentUser.idOrNull();
        if (userId == null) {
            throw new BaseException(BaseResponseStatus.LOGIN_REQUIRED);
        }
        return userId;
    }
}
