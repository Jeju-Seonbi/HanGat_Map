package com.example.hangat.course;

import com.example.hangat.course.model.entity.CoursePreset;
import com.example.hangat.course.repository.CoursePresetRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoursePresetJsonPersistenceTest {

    @Autowired CoursePresetRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void 유효한_JSON_문자열은_그대로_저장하고_조회한다() {
        String filterJson = "{\"regions\":[\"EAST\"],\"styles\":[\"NATURE\"]}";
        CoursePreset saved = repository.saveAndFlush(preset("VALID_JSON", filterJson));
        entityManager.clear();

        CoursePreset loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getFilterJson()).isEqualTo(filterJson);
    }

    @Test
    void 필터_JSON은_null을_허용한다() {
        CoursePreset saved = repository.saveAndFlush(preset("NULL_JSON", null));
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getFilterJson()).isNull();
    }

    @Test
    void 유효하지_않은_JSON은_DB_CHECK가_차단한다() {
        assertThatThrownBy(() -> repository.saveAndFlush(preset("INVALID_JSON", "not-json")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CoursePreset preset(String code, String filterJson) {
        return CoursePreset.builder()
                .code(code)
                .name(code)
                .defaultTitle(code)
                .durationDays((short) 2)
                .filterJson(filterJson)
                .build();
    }
}
