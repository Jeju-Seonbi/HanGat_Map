package com.example.hangat.course.service;

import com.example.hangat.course.model.MainCourseResponse;
import com.example.hangat.course.model.entity.Course;
import com.example.hangat.course.model.entity.CoursePreset;
import com.example.hangat.course.model.enums.CourseStatus;
import com.example.hangat.course.model.enums.CourseType;
import com.example.hangat.course.repository.CourseItemRepository;
import com.example.hangat.course.repository.CoursePresetRepository;
import com.example.hangat.course.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 메인 추천 코스 카드 조회 (MAIN_002, 담당: 정동현).
 *
 * <p>프리셋별 <b>최신 READY</b>를 집는다 - 오늘 배치가 실패했으면 자연히 직전 성공분이 나와
 * 카드가 비지 않는다(실패 내성). 단 <b>출발일이 이미 지난 코스는 걸러낸다</b> - 배치가 며칠
 * 죽어 있었다면 낡은 코스를 "오늘의 추천"으로 파는 대신 빈 목록을 준다(정직성).
 * 빈 목록이면 프론트가 목업으로 폴백한다(연동 시 빈 배열도 폴백 조건에 포함할 것).
 */
@Service
@Transactional(readOnly = true)
public class MainCourseService {

    /** 카드 수 - 노출 후보(유효 출발일의 프리셋별 최신 READY)가 4장이어도 평균 집중률 낮은 3장만. */
    private static final int CARD_COUNT = 3;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CoursePresetRepository presetRepository;
    private final CourseRepository courseRepository;
    private final CourseItemRepository itemRepository;

    public MainCourseService(CoursePresetRepository presetRepository,
                             CourseRepository courseRepository,
                             CourseItemRepository itemRepository) {
        this.presetRepository = presetRepository;
        this.courseRepository = courseRepository;
        this.itemRepository = itemRepository;
    }

    public List<MainCourseResponse> mainCourses() {
        LocalDate today = LocalDate.now(KST);
        List<MainCourseResponse> cards = new ArrayList<>();
        for (CoursePreset preset : presetRepository.findActivePresets()) {
            courseRepository.findFirstByPresetIdAndCourseTypeAndStatusOrderByIdDesc(
                            preset.getId(), CourseType.SAMPLE, CourseStatus.READY)
                    .filter(course -> !course.getStartDate().isBefore(today))   // 출발일 지난 낡은 코스 컷
                    .map(this::toCard)
                    .ifPresent(cards::add);
        }
        return cards.stream()
                .sorted(Comparator.comparing(MainCourseResponse::averageCongestionRate,
                        Comparator.nullsLast(BigDecimal::compareTo)))
                .limit(CARD_COUNT)
                .toList();
    }

    private MainCourseResponse toCard(Course course) {
        return MainCourseResponse.of(course, itemRepository.findItemsWithPlace(course.getId()));
    }
}
