package com.example.hangat.course;

import com.example.hangat.course.model.CourseClaimRequest;
import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.enums.CourseStatus;
import com.example.hangat.course.model.enums.Transport;
import com.example.hangat.course.repository.CourseRepository;
import com.example.hangat.user.model.User;
import com.example.hangat.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CourseClaimConcurrencyTest {

    @Autowired CourseClaimService claimService;
    @Autowired CourseClaimTokenService tokenService;
    @Autowired CourseRepository courseRepository;
    @Autowired UserRepository userRepository;

    @Test
    void concurrentClaimsHaveAtMostOneWinner() throws Exception {
        User first = userRepository.save(User.signUpWithSocial(
                "claim-first@hangat.local", "첫 사용자"));
        User second = userRepository.save(User.signUpWithSocial(
                "claim-second@hangat.local", "둘째 사용자"));
        Course course = courseRepository.save(Course.builder()
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 2))
                .transport(Transport.RENTAL_CAR)
                .status(CourseStatus.READY)
                .build());
        String proof = tokenService.issue(course.getId()).token();
        CountDownLatch start = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var attempts = List.of(first, second).stream()
                    .map(user -> executor.submit(() -> {
                        start.await();
                        try {
                            claimService.claim(course.getId(), user.getId(),
                                    new CourseClaimRequest(proof, "동시 저장"));
                            return true;
                        } catch (RuntimeException exception) {
                            return false;
                        }
                    }))
                    .toList();
            start.countDown();
            long successes = 0;
            for (var attempt : attempts) {
                if (attempt.get()) successes++;
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        Course saved = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(CourseStatus.SAVED);
        assertThat(saved.getUser().getId()).isIn(first.getId(), second.getId());
    }
}
