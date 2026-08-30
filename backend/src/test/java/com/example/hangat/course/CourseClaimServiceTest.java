package com.example.hangat.course;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.course.model.CourseClaimRequest;
import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.enums.CourseStatus;
import com.example.hangat.course.model.enums.Transport;
import com.example.hangat.course.repository.CourseRepository;
import com.example.hangat.user.model.User;
import com.example.hangat.user.repository.UserRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseClaimServiceTest {

    @Test
    void validProofClaimsTheReadyCourseExactlyOnce() {
        UserRepository users = mock(UserRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        CourseClaimTokenService tokens = mock(CourseClaimTokenService.class);
        CourseClaimService service = new CourseClaimService(users, courses, tokens);
        User user = user(7L);
        Course course = readyCourse(11L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(courses.findByIdForClaim(11L)).thenReturn(Optional.of(course));

        var response = service.claim(11L, 7L, new CourseClaimRequest("proof", " 제주 여행 "));

        verify(tokens).validate("proof", 11L);
        assertThat(response.status()).isEqualTo(CourseStatus.SAVED);
        assertThat(course.getUser()).isSameAs(user);
        assertThat(course.getTitle()).isEqualTo("제주 여행");

        assertThatThrownBy(() -> service.claim(
                11L, 7L, new CourseClaimRequest("proof", "재시도")))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.COURSE_NOT_CLAIMABLE));

        User anotherUser = user(8L);
        when(users.findById(8L)).thenReturn(Optional.of(anotherUser));
        assertThatThrownBy(() -> service.claim(
                11L, 8L, new CourseClaimRequest("proof", "다른 사용자 재시도")))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.COURSE_NOT_CLAIMABLE));
    }

    @Test
    void stateIsCheckedBeforeProofAndMissingUserOrCourseDoesNotMutateAnything() {
        UserRepository users = mock(UserRepository.class);
        CourseRepository courses = mock(CourseRepository.class);
        CourseClaimTokenService tokens = mock(CourseClaimTokenService.class);
        CourseClaimService service = new CourseClaimService(users, courses, tokens);
        User user = user(7L);
        Course saved = readyCourse(11L);
        saved.markSaved(user, "기존 저장");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(courses.findByIdForClaim(11L)).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.claim(
                11L, 7L, new CourseClaimRequest("proof", "가로채기")))
                .isInstanceOf(BaseException.class);
        verify(tokens, never()).validate("proof", 11L);

        when(users.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.claim(
                11L, 99L, new CourseClaimRequest("proof", "제목")))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.USER_NOT_FOUND));

        when(users.findById(8L)).thenReturn(Optional.of(user(8L)));
        when(courses.findByIdForClaim(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.claim(
                404L, 8L, new CourseClaimRequest("proof", "제목")))
                .isInstanceOfSatisfying(BaseException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(BaseResponseStatus.COURSE_NOT_FOUND));
    }

    @Test
    void requestRequiresClaimProofAndTitle() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new CourseClaimRequest(null, "제목")))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("claimToken");
            assertThat(validator.validate(new CourseClaimRequest("proof", " ")))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("title");
        }
    }

    private User user(Long id) {
        return User.builder().id(id).email("user" + id + "@hangat.local")
                .nickname("사용자" + id).build();
    }

    private Course readyCourse(Long id) {
        return Course.builder().id(id)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 2))
                .transport(Transport.RENTAL_CAR)
                .status(CourseStatus.READY)
                .build();
    }
}
