package com.sparta.ourmarbleserver.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class JwtSecurityConfig {
    @Bean
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // 쿠키 인증 없이 Bearer 헤더를 쓰므로 CSRF 보호 끄기
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 요청마다 JWT를 검증하므로 인증용 세션 사용 안 함
                .requestCache(AbstractHttpConfigurer::disable) // 로그인 후 이동할 페이지가 없어 요청 저장 끄기
                .formLogin(AbstractHttpConfigurer::disable) // JSON 로그인 API를 쓰므로 기본 로그인 폼 끄기
                .httpBasic(AbstractHttpConfigurer::disable) // JWT로 요청을 인증하므로 HTTP Basic 인증 끄기
                .logout(AbstractHttpConfigurer::disable) // JWT 인증을 사용하므로 세션 기반 로그아웃 끄기
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/docs.html", "/v3/api-docs", "/v3/api-docs/**").permitAll() // Redoc API 문서 화면
                        .requestMatchers(HttpMethod.GET, "/posts").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), AuthorizationFilter.class);
        return http.build();
    }
}
