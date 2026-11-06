package vn.cineprime.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.cineprime.domain.Store;
import static vn.cineprime.domain.Domain.*;
import vn.cineprime.domain.Domain.User;

@Configuration
public class SecurityConfig {
    public record SessionUser(Long id,String email,String password,String role,int version) implements UserDetails, Serializable {
        public Collection<? extends GrantedAuthority> getAuthorities() { return List.of((GrantedAuthority)()->"ROLE_"+role); }
        public String getUsername() { return email; }
        public String getPassword() { return password; }
    }
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(Store db) {
        return email -> { User u=db.first(User.class,"where e.email=?1",email.trim().toLowerCase(Locale.ROOT)).orElseThrow(()->new UsernameNotFoundException("Tài khoản không hợp lệ"));
            if(!u.enabled) throw new UsernameNotFoundException("Tài khoản không hợp lệ");
            return new SessionUser(u.id,u.email,u.passwordHash,u.role,u.authVersion); };
    }
    @Bean SecurityFilterChain security(HttpSecurity http,Store db,RateLimit rates) throws Exception {
        http.authorizeHttpRequests(a->a
            .requestMatchers("/css/**","/js/**","/images/**","/fonts/**","/media/**","/error","/","/movies/**","/cinemas/**","/showtimes","/catalog/showtimes","/content/**","/offers","/articles","/login","/register","/forgot-password","/reset-password","/payments/momo/ipn").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .requestMatchers("/staff/**","/api/staff/**").hasRole("STAFF")
            .anyRequest().authenticated());
        http.csrf(c->c.ignoringRequestMatchers("/payments/momo/ipn"));
        http.formLogin(f->f.loginPage("/login").usernameParameter("email").successHandler((request,response,auth)-> {
            SessionUser u=(SessionUser)auth.getPrincipal();
            var cache=new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
            var saved=cache.getRequest(request,response);cache.removeRequest(request,response);
            if(u.role().equals("CUSTOMER")&&saved!=null){
                java.net.URI uri=java.net.URI.create(saved.getRedirectUrl());String path=uri.getRawPath();
                if(path.startsWith("/booking/")||path.startsWith("/orders/")||path.equals("/account")){
                    response.sendRedirect(path+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery()));return;
                }
            }
            response.sendRedirect(switch(u.role()){case "ADMIN"->"/admin";case "STAFF"->"/staff";default->"/account";});
        }).permitAll());
        http.logout(l->l.logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID"));
        http.addFilterBefore(new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                if(req.getMethod().equals("POST")&&req.getServletPath().equals("/login")&&!rates.allow("login:"+req.getRemoteAddr(),15,60)) { res.sendError(429,"Thử lại sau một phút.");return; }
                Authentication a=SecurityContextHolder.getContext().getAuthentication();
                if(a!=null&&a.getPrincipal() instanceof SessionUser p) {
                    Optional<User> current=db.first(User.class,"where e.id=?1",p.id());
                    if(current.isEmpty()||!current.get().enabled||current.get().authVersion!=p.version()||!current.get().role.equals(p.role())) {
                        SecurityContextHolder.clearContext(); if(req.getSession(false)!=null)req.getSession(false).invalidate();
                        res.sendRedirect("/login?expired");return;
                    }
                }
                chain.doFilter(req,res);
            }
        },UsernamePasswordAuthenticationFilter.class);
        http.headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; frame-src https://www.youtube.com https://www.youtube-nocookie.com; media-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'")));
        return http.build();
    }
    @org.springframework.stereotype.Component
    public static class RateLimit {
        private record Window(long start,int count) {}
        private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
        public boolean allow(String key,int max,int seconds) {
            long now=System.currentTimeMillis();
            if(windows.size()>10000) windows.entrySet().removeIf(e->now-e.getValue().start>seconds*1000L);
            Window result=windows.compute(key,(k,w)->w==null||now-w.start>=seconds*1000L?new Window(now,1):new Window(w.start,w.count+1));
            return result.count<=max;
        }
    }
}
