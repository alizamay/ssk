package dev.niceone.smartsiteappserver;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
//        if (!System.getProperties().containsKey("spring.config.name")) {
//            application = application.properties("spring.config.name:ssws");
//        }
        return application.sources(SmartsiteAppserverApplication.class)
                .properties(SmartsiteAppserverApplication.defaultProperties());
    }


}
