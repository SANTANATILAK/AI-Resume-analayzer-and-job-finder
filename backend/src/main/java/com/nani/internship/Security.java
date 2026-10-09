package com.nani.internship;
import io.jsonwebtoken.*; import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*; import jakarta.servlet.http.*;
import java.io.IOException; import java.util.*; import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.*; import org.springframework.web.filter.OncePerRequestFilter;

@Component class JwtUtil {
  final SecretKey key;
  JwtUtil(@Value("${app.jwt-secret}") String s){ key=Keys.hmacShaKeyFor(s.getBytes()); }
  String make(String email,String role){
    return Jwts.builder().subject(email).claim("role",role).issuedAt(new Date())
      .expiration(new Date(System.currentTimeMillis()+86400000L)).signWith(key).compact(); }
  Claims parse(String t){ return Jwts.parser().verifyWith(key).build().parseSignedClaims(t).getPayload(); }
}
@Component class JwtFilter extends OncePerRequestFilter {
  final JwtUtil jwt; JwtFilter(JwtUtil j){jwt=j;}
  protected void doFilterInternal(HttpServletRequest rq,HttpServletResponse rs,FilterChain c) throws ServletException,IOException {
    String h=rq.getHeader("Authorization");
    if(h!=null&&h.startsWith("Bearer ")){ try{ Claims cl=jwt.parse(h.substring(7));
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(cl.getSubject(),null,
        List.of(new SimpleGrantedAuthority("ROLE_"+cl.get("role",String.class))))); }catch(JwtException e){ /* invalid token: stay anonymous */ } }
    c.doFilter(rq,rs);
  }
}
@Configuration class SecurityConfig {
  @Bean SecurityFilterChain chain(HttpSecurity http,JwtFilter f) throws Exception {
    http.csrf(c->c.disable()).cors(Customizer.withDefaults())
      .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->r.sendError(401)))
      .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
        .requestMatchers("/api/auth/**","/api/health").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
      .addFilterBefore(f,UsernamePasswordAuthenticationFilter.class);
    return http.build(); }
  @Bean PasswordEncoder encoder(){ return new BCryptPasswordEncoder(); }
  @Bean CorsConfigurationSource cors(@Value("${app.frontend-url}") String urls){
    CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(Arrays.asList(urls.split(",")));
    c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("*")); c.setExposedHeaders(List.of("Content-Disposition"));
    UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**",c); return s; }
}
