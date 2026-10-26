package dev.niceone.smartsiteappserver.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            Map<String, String> jwt = getJwtFromRequest(request);
            String token = jwt.get("token");

            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
//                Long userId = tokenProvider.getUserIdFromJWT(token);
                String userName = tokenProvider.getUserNameFromJWT(token);

                /*
                    Note that you could also encode the user's username and roles inside JWT claims
                    and create the UserDetails object by parsing those claims from the JWT.
                    That would avoid the following database hit. It's completely up to you.
                 */
//                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(userName);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private Map<String, String> getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        Map<String, String> jwt = new HashMap<>();
        jwt.put("token", null);
        jwt.put("token_type", null);
        // user token
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            jwt.put("token", bearerToken.substring(7, bearerToken.length()));
            jwt.put("token_type", "Bearer");
        }
        // rpi token
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Rpi ")) {
            jwt.put("token", bearerToken.substring(4, bearerToken.length()));
            jwt.put("token_type", "Rpi");
        }

//        logger.info("type: "+jwt.get("token_type")+" token: "+jwt.get("token"));
        return jwt;
    }
}