package com.example.hangat.course;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.course.model.CourseClaimRequest;
import com.example.hangat.course.model.CourseClaimResponse;
import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.enums.CourseStatus;
import com.example.hangat.course.repository.CourseRepository;
import com.example.hangat.user.model.User;
import com.example.hangat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseClaimService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseClaimTokenService tokenService;

    @Transactional
    public CourseClaimResponse claim(Long courseId, Long userId, CourseClaimRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.USER_NOT_FOUND));
        Course course = courseRepository.findByIdForClaim(courseId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.COURSE_NOT_FOUND));

        if (course.getUser() != null || course.getStatus() != CourseStatus.READY) {
            throw new BaseException(BaseResponseStatus.COURSE_NOT_CLAIMABLE);
        }

        tokenService.validate(request.claimToken(), courseId);
        course.markSaved(user, request.title().trim());

        return new CourseClaimResponse(
                course.getId(), course.getStatus(), course.getTitle(), course.getSavedAt());
    }
}
