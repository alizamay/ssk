package dev.niceone.smartsiteappserver.config;

import dev.niceone.smartsiteappserver.model.User;
import dev.niceone.smartsiteappserver.repository.UserRepository;
import dev.niceone.smartsiteappserver.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.LdapShaPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Properties;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true,
        prePostEnabled = true
)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private CustomAuthenticationProvider customAuthProvider;

    @Autowired
    @Qualifier("customUserDetailsService")
    CustomUserDetailsService customUserDetailsService;

    @Autowired
    @Qualifier("myAuthPopulator")
    MyAuthoritiesPopulator myAuthPopulator;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Autowired
    private UserRepository userRepo;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public InMemoryUserDetailsManager inMemoryUserDetailsManager() {
        final Properties users = new Properties();
        users.put("user","password,ROLE_USER,enabled"); //add whatever other user you need
        users.put("admin","password,ADMIN,enabled");
        return new InMemoryUserDetailsManager(users);
    }

//    @Autowired
//    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
//        auth.inMemoryAuthentication()
//                .withUser("user").password(passwordEncoder().encode("password")).roles("USER")
//                .and()
//                .withUser("admin").password(passwordEncoder().encode("password")).roles("USER", "ADMIN");
//    }

    @Bean
    public UserDetailsManager userDetailsManager() {
        return new InMemoryCoreUserDetailsManager();
    }

//    @Override
//    public void configure(AuthenticationManagerBuilder authenticationManagerBuilder) throws Exception {
//        authenticationManagerBuilder
//                .userDetailsService(customUserDetailsService)
//                .passwordEncoder(passwordEncoder());
//
//        UserDetailsManager userDetailsManager = userDetailsManager();
//        authenticationManagerBuilder.userDetailsService(userDetailsManager);
//
//        Optional<User> user1 = userRepo.findById((long) 1);
//        Optional<User> user2 = userRepo.findById((long) 2);
//
//
////        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
////                .orElseThrow(() -> new AppException("User Role not set."));
////
////        user.setRoles(Collections.singleton(userRole));
////
////        User result = userRepository.save(user);
//        user1.ifPresent(user1unwrap -> .createUser(UserPrincipal.create(user1unwrap)));
//        user2.ifPresent(user2unwrap ->userDetailsManager.createUser(UserPrincipal.create(user2unwrap)));
//    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        //internal ldap test
//        auth
//            .ldapAuthentication()
//            .userDnPatterns("uid={0},ou=employees")
//            .groupSearchBase("ou=groups")
//            .contextSource(contextSource())
//            .userDetailsContextMapper(userDetailsContextMapper())
////            .ldapAuthoritiesPopulator(myAuthPopulator)
//            .passwordCompare()
//            .passwordEncoder(new LdapShaPasswordEncoder())
//            .passwordAttribute("userPassword");
        auth.authenticationProvider(customAuthProvider);
    }

    @Bean
    public DefaultSpringSecurityContextSource contextSource() {
        return new DefaultSpringSecurityContextSource(Arrays.asList("ldap://localhost:8389/"), "dc=springframework,dc=org");
    }

//    @Bean
//    public UserDetailsContextMapper userDetailsContextMapper() {
//        return new LdapUserDetailsMapper() {
//            @Override
//            public UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
//                UserDetails details = super.mapUserFromContext(ctx, username, authorities);
//                return new CustomLdapUserDetails((LdapUserDetails) details);
//            }
//        };
//    }

    @Bean(BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http
                .cors()
                .and()
                .csrf()
                .disable()
                .exceptionHandling()
                .authenticationEntryPoint(unauthorizedHandler)
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/",
                        "/ws/**",
                        "/favicon.ico",
                        "/**/*.png",
                        "/**/*.gif",
                        "/**/*.svg",
                        "/**/*.jpg",
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js")
                .permitAll()
                .antMatchers("/api/site/stream/**")
                .permitAll()
                .antMatchers("/api/auth/**")
                .permitAll()
                .antMatchers("/api/user/checkUsernameAvailability", "/api/user/checkEmailAvailability")
                .permitAll()
                .antMatchers("/swagger-ui.html", "/swagger-resources/**", "/v2/**")
                .permitAll()
                .antMatchers("/device/**")
                .permitAll()
                .antMatchers("/ttsm/cabinet/**")
                .permitAll()
                .antMatchers("/api/alarm/update/**")
                .permitAll()
                .antMatchers("/api/check/onsite/**")
                .permitAll()
                .antMatchers("/api/cam-img-log/img/**")
                .permitAll()
                .antMatchers("/api/scheduler/**")
                .permitAll()
                .antMatchers("/h2-console/**")
                .permitAll()
                .antMatchers("/api/internal/authin/**")
                .permitAll()
                .antMatchers("/api/summary/**")
                .permitAll()
                .antMatchers("/api/ttsm/webhook/**")
                .permitAll()
                .antMatchers("/api/site/version-summary")
                .permitAll()
                .anyRequest()
                .authenticated();

//        .antMatchers(HttpMethod.GET, "/regions/**").hasRole("USER")
//                .antMatchers(HttpMethod.POST, "/regions").hasRole("ADMIN")
//                .antMatchers(HttpMethod.PUT, "/regions/**").hasRole("ADMIN")
//                .antMatchers(HttpMethod.PATCH, "/regions/**").hasRole("ADMIN")
//                .antMatchers(HttpMethod.DELETE, "/regions /**").hasRole("ADMIN")

        // Add our custom JWT security filter
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        http.headers().disable(); //for h2-console test
    }
}