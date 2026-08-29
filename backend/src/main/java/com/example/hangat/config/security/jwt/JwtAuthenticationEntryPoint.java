package com.example.hangat.config.security.jwt;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.common.model.BaseResponseStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 *  인증 실패 응답 - 토큰이 아예 없을 때 Security가 여기로 보낸다.
 *  기본값은 본문 없는 403이라 프론트가 코드를 못 읽기에 BaseResponse 형식으로 맞춤
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {

        write(response, BaseResponseStatus.JWT_INVALID);
    }

    // 필터도 같은 형식으로 내보내야 해서 열어둔다.
    public void write(HttpServletResponse response, BaseResponseStatus status)
            throws IOException{

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), BaseResponse.fail(status));
    }
}
