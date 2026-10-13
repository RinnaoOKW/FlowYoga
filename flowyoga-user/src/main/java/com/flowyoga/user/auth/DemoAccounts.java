package com.flowyoga.user.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "flowyoga.demo.enabled", havingValue = "true")
public class DemoAccounts implements ApplicationRunner {
    private final AuthMapper mapper;
    private final PasswordEncoder encoder;
    public DemoAccounts(AuthMapper mapper, PasswordEncoder encoder) { this.mapper = mapper; this.encoder = encoder; }
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(1001, "admin", "林馆长", "13000000001", "ADMIN");
        seed(2001, "coach", "陈教练", "13000000002", "COACH");
        seed(3001, "member", "瑜伽会员", "13000000003", "MEMBER");
    }
    private void seed(long id, String loginName, String name, String phone, String role) {
        if (mapper.findByLoginName(loginName) == null) {
            mapper.insertDemoAccount(id, loginName, encoder.encode("FlowYoga@123"), name, phone, role);
        }
    }
}
