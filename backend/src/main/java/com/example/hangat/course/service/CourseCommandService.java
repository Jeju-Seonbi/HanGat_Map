package com.example.hangat.course.service;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.course.model.CourseSummaryResponse;
import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.enums.CourseStatus;
import com.example.hangat.course.repository.CourseItemRepository;
import com.example.hangat.course.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 코스 관리 (MY_001) - 이름 변경·삭제.
 *
 * <p>둘 다 <b>소유자 본인만</b> 할 수 있다. 삭제는 물리 삭제가 아니라 논리 삭제라
 * 되돌릴 여지를 남기고, 지운 코스를 또 지워도 오류가 아니다(멱등).
 */
@Service
@Transactional
public class CourseCommandService {

    private final CourseRepository courseRepository;
    private final CourseItemRepository itemRepository;

    public CourseCommandService(CourseRepository courseRepository,
                                CourseItemRepository itemRepository) {
        this.courseRepository = courseRepository;
        this.itemRepository = itemRepository;
    }

    public CourseSummaryResponse rename(Long courseId, String title, Long authUserId) {
        Course course = ownedByMe(courseId, authUserId);
        course.rename(title.trim());
        return CourseSummaryResponse.of(course, itemRepository.findItemsWithPlace(courseId));
    }

    /** 이미 지운 코스를 또 지워도 성공으로 답한다 - 화면이 두 번 눌러도 오류를 보지 않게. */
    public void delete(Long courseId, Long authUserId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.COURSE_NOT_FOUND, courseId));
        if (course.getStatus() == CourseStatus.DELETED) {
            return;
        }
        requireOwner(course, authUserId);
        course.softDelete();
    }

    /**
     * 삭제된 코스는 "없는 코스"로 답한다 - 존재 여부까지 숨긴다.
     * 소유자 없는 임시 코스는 이름 변경·삭제 대상이 아니다(저장하지 않은 코스라 지울 것도 없다).
     */
    private Course ownedByMe(Long courseId, Long authUserId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.COURSE_NOT_FOUND, courseId));
        if (course.getStatus() == CourseStatus.DELETED) {
            throw new BaseException(BaseResponseStatus.COURSE_NOT_FOUND, courseId);
        }
        requireOwner(course, authUserId);
        return course;
    }

    private void requireOwner(Course course, Long authUserId) {
        if (course.getUser() == null
                || authUserId == null
                || !course.getUser().getId().equals(authUserId)) {
            throw new BaseException(BaseResponseStatus.COURSE_FORBIDDEN, course.getId());
        }
    }
}
