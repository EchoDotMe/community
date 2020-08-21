package me.echo.community.controller.interceptor;

import me.echo.community.entity.LoginTicket;
import me.echo.community.entity.User;
import me.echo.community.service.UserService;
import me.echo.community.util.CookieUtil;
import me.echo.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

@Component
public class LoginTicketInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从cookie获取凭证
        String ticket = CookieUtil.getValue(request, "ticket");
        if (ticket!=null){
            LoginTicket loginTicket = userService.findLoginTicket(ticket);
            // 检查有效性
            if (loginTicket!=null&&loginTicket.getStatus()==0&&loginTicket.getExpired().after(new Date())){
                User user = userService.findUserById(loginTicket.getUserId());
                // 在本次请求中保存用户信息
                hostHolder.setUser(user);
                // 构建用户认证的结果，存入SecurityContext,便于Security授权
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        user, user.getPassword(), userService.getAuthorities(user.getId())
                );
                SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
            }
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        User user = hostHolder.getUser();
        if (user!=null&&modelAndView!=null){
            modelAndView.addObject("loginUser", user);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        hostHolder.clear();

        SecurityContextHolder.clearContext();
    }
}
