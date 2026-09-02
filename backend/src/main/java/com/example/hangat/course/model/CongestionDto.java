package com.example.hangat.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CongestionDto {

    private String baseYmd;
    private String areaCd;
    private String areaNm;
    private String signguCd;
    private String signguNm;

    @JsonProperty("tAtsNm")
    private String tAtsNm;

    private String cnctrRate;
}
