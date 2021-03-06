package me.echo.community.config;

import me.echo.community.controller.interceptor.DataInterceptor;
import me.echo.community.controller.interceptor.LoginTicketInterceptor;
import me.echo.community.controller.interceptor.MessageInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyMvcConfig implements WebMvcConfigurer{

    private final LoginTicketInterceptor loginTicketInterceptor;

    // 废弃 使用springs security进行权限控制
//    private final LoginRequiredInterceptor loginRequiredInterceptor;

    private final MessageInterceptor messageInterceptor;

    private final DataInterceptor dataInterceptor;

    @Autowired
    public MyMvcConfig(LoginTicketInterceptor loginTicketInterceptor, MessageInterceptor messageInterceptor, DataInterceptor dataInterceptor) {
        this.loginTicketInterceptor = loginTicketInterceptor;
        this.messageInterceptor = messageInterceptor;
        this.dataInterceptor = dataInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.loginTicketInterceptor)
                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");

//        registry.addInterceptor(this.loginRequiredInterceptor)
//                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");

        registry.addInterceptor(this.messageInterceptor)
                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");

        registry.addInterceptor(this.dataInterceptor)
                .excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg", "/**/*.jpeg");
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            //配置跨域
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")     //允许的路径
                        .allowedMethods("*")     //允许的方法
                        .allowedOrigins("*")       //允许的网站
                        .allowedHeaders("*")     //允许的请求头
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
