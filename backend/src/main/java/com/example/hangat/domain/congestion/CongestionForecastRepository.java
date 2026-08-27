package com.example.hangat.domain.congestion;

import com.example.hangat.domain.congestion.model.CongestionForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CongestionForecastRepository extends JpaRepository<CongestionForecast, Long> {

    List<CongestionForecast> findByBaseDate(LocalDate baseDate);
}
