package chaoxing.autosign.config;

import chaoxing.autosign.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // 放行登录接口 + 封面图代理（img 标签无法带 Authorization 头）
        if (path.startsWith("/api/auth/") || path.startsWith("/api/course/cover")) {
            chain.doFilter(request, response);
            return;
        }

        // 仅拦截 /api/ 路径
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                httpRequest.setAttribute("userId", userId);
                chain.doFilter(request, response);
                return;
            }
        }

        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.setStatus(401);
        httpResponse.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\"}");
    }
}
