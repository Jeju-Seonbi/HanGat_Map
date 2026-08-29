package com.example.hangat.config;

import com.example.hangat.config.security.jwt.JwtAuthenticationEntryPoint;
import com.example.hangat.config.security.jwt.JwtAuthenticationFilter;
import com.example.hangat.config.security.jwt.JwtProvider;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security 기본 틀 (Nexus 컨벤션)
 * - 현재는 전부 permitAll - 회원(JWT) 담당자가 JwtFilter/LoginFilter를 붙일 자리
 * - JWT 도입 시: http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
 */
@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain configure(HttpSecurity http,
                                         JwtProvider jwtProvider,
                                         JwtAuthenticationEntryPoint entryPoint) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http.csrf(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth -> auth
                // 예외 처리중 /error가 다시 인증에 막히는 것을 방지
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                // 운영 및 API 문서
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**"
                ).permitAll()
                .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                ).permitAll()

                // 비회원 공개 API
                .requestMatchers("/main/**").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/users/check-nickname"
                ).permitAll()

                // 그 외 API는 인증 필요
                .anyRequest().authenticated()
        );
        http.exceptionHandling(exception ->
                exception.authenticationEntryPoint(entryPoint));

        http.addFilterBefore(
                new JwtAuthenticationFilter(jwtProvider, entryPoint),
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 프론트 dev 서버 오리진 (배포 도메인 확정 시 추가)
        configuration.setAllowedOrigins(List.of(
                "http://localhost:4173",
                "http://localhost:5173"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
