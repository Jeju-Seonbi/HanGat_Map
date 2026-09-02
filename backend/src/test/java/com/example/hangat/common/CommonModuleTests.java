package com.example.hangat.common;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.exception.GlobalExceptionHandler;
import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.BaseResponseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CommonModuleTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 성공_응답은_2000_코드를_가진다() {
        BaseResponse<String> response = BaseResponse.success("ok");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(2000);
        assertThat(response.getResult()).isEqualTo("ok");
    }

    @Test
    void 클라이언트_오류는_400으로_매핑된다() {
        ResponseEntity<BaseResponse<Object>> response =
                handler.handleBaseException(new BaseException(BaseResponseStatus.PLACE_NOT_FOUND));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo(3201);
        assertThat(response.getBody().getSuccess()).isFalse();
    }

    @Test
    void 서버_오류는_500으로_매핑되고_추가정보를_result에_담는다() {
        ResponseEntity<BaseResponse<Object>> response =
                handler.handleBaseException(new BaseException(BaseResponseStatus.EXTERNAL_API_ERROR, "TourAPI timeout"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo(5002);
        assertThat(response.getBody().getResult()).isEqualTo("TourAPI timeout");
    }
}
