package me.echo.community.service;

import me.echo.community.dao.UserMapper;
import me.echo.community.entity.LoginTicket;
import me.echo.community.entity.User;
import me.echo.community.enums.UserStatus;
import me.echo.community.enums.UserType;
import me.echo.community.util.CommunityConstant;
import me.echo.community.util.CommunityUtil;
import me.echo.community.util.MailClient;
import me.echo.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class UserService implements CommunityConstant {

    @Autowired
    private MailClient mailClient;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired(required = false)
    private UserMapper userMapper;

//    @Autowired(required = false)
//    private LoginTicketMapper loginTicketMapper;

    /**
     * 根据用户id查询用户信息
     * @param userId id主键值
     * @return User 对象
     */
    public User findUserById(Integer userId){
//        return userMapper.selectUserById(userId);
        User user = getCache(userId);
        if (user == null){
            user = initCache(userId);
        }
        return user;
    }

    public Map<String, Object> register(User user) {
        Map<String, Object> map = new HashMap<>();
        if (user == null) {
            throw new IllegalArgumentException("参数不能为空!");
        }

        if (StringUtils.isBlank(user.getUserName())) {
            map.put("usernameError", "用户名不能为空");
            return map;
        }
        if (StringUtils.isBlank(user.getEmail())) {
            map.put("emailError", "邮箱不能为空");
            return map;
        }
        if (StringUtils.isBlank(user.getPassword())) {
            map.put("passwordError", "密码不能为空");
            return map;
        }

        user.setSalt(CommunityUtil.generateUUID().substring(0, 5));
        user.setPassword(CommunityUtil.md5(user.getPassword() + user.getSalt()));
        user.setType(UserType.ORDINARY.getKey());
        user.setStatus(UserStatus.INACTIVATED.getKey());
        user.setActivationCode(CommunityUtil.generateUUID());
//        user.setHeaderUrl();
        user.setCreateTime(new Date());

        userMapper.saveUser(user);

        // 发送激活邮件
        Context context = new Context();
        context.setVariable("email", user.getEmail());
        // http://localhost:8080/community/activation/id/code
        String url = domain+contextPath+"/activation/"+user.getId()+"/"+user.getActivationCode();
        context.setVariable("url", url);
        String content = templateEngine.process("/mail/activation", context);
        mailClient.sendMail(user.getEmail(), "激活账号", content);
        return map;
    }

    /**
     * 激活账号
     * @param userId 用户id主键
     * @param code 激活码
     * @return 激活状态
     */
    public int activation(int userId, String code){
        User user = userMapper.selectUserById(userId);
        if (user.getStatus()==UserStatus.ACTIVATED.getKey()){
            return ACTIVATION_REPEAT;
        }else if (user.getActivationCode().equals(code)){
            userMapper.updateStatus(userId, UserStatus.ACTIVATED.getKey());
            cleanCache(userId);
            return ACTIVATION_SUCCESS;
        }else {
            return ACTIVATION_FAILURE;
        }
    }

    /**
     * 登录服务 校验登录数据 生成登录凭证
     * @param userName 用户名
     * @param password 密码
     * @param expireSeconds 过期时间 单位为秒
     * @return 包含操作结果的 map
     */
    public Map<String, Object> login(String userName, String password, int expireSeconds){
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isBlank(userName)){
            map.put("userNameMsg", "用户名不能为空!");
            return map;
        }

        if (StringUtils.isBlank(password)){
            map.put("passwordMsg", "密码不能为空!");
            return map;
        }

        // 判断用户是否存在
        User user = userMapper.selectUserByUserName(userName);
        if (user==null){
            map.put("userNameMsg", "用户不存在!");
            return map;
        }
        // 验证状态
        if (user.getStatus()==UserStatus.INACTIVATED.getKey()){
            map.put("userNameMsg", "用户未激活!");
            return map;
        }

        // 验证密码
        password = CommunityUtil.md5(password+user.getSalt());
        if (!user.getPassword().equals(password)){
            map.put("passwordMsg", "密码错误!");
            return map;
        }

        // 登录成功 生成登录凭证
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(user.getId());
        loginTicket.setTicket(CommunityUtil.generateUUID());
        loginTicket.setStatus(0);
        loginTicket.setExpired(new Date(System.currentTimeMillis()+expireSeconds*1000));

        /*
         * 数据库中有该用户的数据则更新记录 否则新建记录
         */
//        if (loginTicketMapper.selectLoginTicketById(user.getId())==null){
//            // 不存在该用户以前的登录记录
//            loginTicketMapper.insertLoginTicket(loginTicket);
//        }else {
//            loginTicketMapper.updateLoginTicket(loginTicket);
//        }
        String redisKey = RedisKeyUtil.getTicketKey(loginTicket.getTicket());
        redisTemplate.opsForValue().set(redisKey, loginTicket);

        map.put("ticket", loginTicket.getTicket());
        return map;
    }

    public void logout(String ticket){
//        loginTicketMapper.updateStatus(ticket, 1);
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(redisKey);
        loginTicket.setStatus(1);
        redisTemplate.opsForValue().set(redisKey, loginTicket);
    }

    /**
     * 获取登录凭证
     */
    public LoginTicket findLoginTicket(String ticket){
//        return loginTicketMapper.selectLoginTicket(ticket);
        String redisKey = RedisKeyUtil.getTicketKey(ticket);
        return (LoginTicket) redisTemplate.opsForValue().get(redisKey);
    }

    /**
     * 更新用户头像
     * @param userId 哪个用户
     * @param headerUrl 更新后的地址
     * @return 数据库影响的条数
     */
    public int updateHeaderUrl(Integer userId, String headerUrl){
//        return userMapper.updateHeader(userId, headerUrl);
        int rows = userMapper.updateHeader(userId, headerUrl);
        cleanCache(userId);
        return rows;
    }

    public void updatePassword(Integer userId, String password){
        cleanCache(userId);
        userMapper.updatePassword(userId, password);
    }

    public User findUserByName(String username){
        return userMapper.selectUserByUserName(username);
    }

    // 尝试从缓存中取值
    private User getCache(Integer userId){
        String redisKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(redisKey);
    }

    // 缓存没有命中，从MySQL中查询并加入缓存
    private User initCache(Integer userId){
        User user = userMapper.selectUserById(userId);
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.opsForValue().set(redisKey, user, 3600, TimeUnit.SECONDS);
        return user;
    }

    // 当用户数据变更时，直接清除缓存
    private void cleanCache(Integer userId){
        String redisKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(redisKey);
    }

    // 获取用户角色
    public Collection<? extends GrantedAuthority> getAuthorities(Integer userId){
        User user = userMapper.selectUserById(userId);
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                switch (user.getType()){
                    case 1:
                        return AUTHORITY_ADMIN;
                    case 2:
                        return AUTHORITY_MODERATOR;
                    default:
                        return AUTHORITY_USER;
                }
            }
        });

        return list;
    }
}
