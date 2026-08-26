package com.example.hangat.course;

import com.example.hangat.course.ai.CourseAiInputDto;
import com.example.hangat.course.ai.CourseAiInputDto.GenerationMetadataDto;
import com.example.hangat.course.model.CourseCandidateDto;
import com.example.hangat.course.model.CourseRequestDto;
import com.example.hangat.course.model.GenerationReason;
import com.example.hangat.course.travel.CourseTravelLegDto;
import com.example.hangat.course.travel.CourseTravelService;
import com.example.hangat.course.weather.CourseWeatherDto;
import com.example.hangat.course.weather.CourseWeatherFactsProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CourseAiPreparationService {

    private final CourseAiInputAssembler assembler;
    private final CourseTravelService travelService;
    private final Optional<CourseWeatherFactsProvider> weatherFactsProvider;

    public CourseAiPreparationService(
            CourseAiInputAssembler assembler,
            CourseTravelService travelService,
            Optional<CourseWeatherFactsProvider> weatherFactsProvider
    ) {
        this.assembler = assembler;
        this.travelService = travelService;
        this.weatherFactsProvider = weatherFactsProvider;
    }

    public CourseAiInputDto prepare(
            CourseRequestDto request,
            List<CourseCandidateDto> candidates
    ) {
        List<CourseCandidateDto> safeCandidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
        Map<String, List<CourseWeatherDto>> weatherFacts = weatherFactsProvider
                .map(provider -> provider.load(request, safeCandidates))
                .orElseGet(Collections::emptyMap);

        if (weatherFacts == null) {
            weatherFacts = Collections.emptyMap();
        }

        return assembler.assemble(
                request,
                safeCandidates,
                weatherFacts,
                buildAdjacentTravelFacts(request, safeCandidates),
                new GenerationMetadataDto(GenerationReason.INITIAL, null, null)
        );
    }

    private List<CourseTravelLegDto> buildAdjacentTravelFacts(
            CourseRequestDto request,
            List<CourseCandidateDto> candidates
    ) {
        List<CourseTravelLegDto> travelFacts = new ArrayList<>();

        for (int index = 0; index + 1 < candidates.size(); index++) {
            CourseCandidateDto from = candidates.get(index);
            CourseCandidateDto to = candidates.get(index + 1);

            if (from == null || from.getPlace() == null || to == null || to.getPlace() == null) {
                continue;
            }

            travelService.calculateStraightLineLeg(
                    from.getPlace(), to.getPlace(), request.getTransport()
            ).ifPresent(travelFacts::add);
        }

        return List.copyOf(travelFacts);
    }
}
