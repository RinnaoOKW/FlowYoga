package com.flowyoga.course.management;
import com.flowyoga.management.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
@Configuration
@EnableScheduling
@EnableFeignClients(clients=AccountClient.class)
@Import({DirectorySecurityConfiguration.class,ManagementApiExceptionHandler.class})
public class CoachManagementConfiguration {
    @Bean DirectoryStore coachDirectoryStore(JdbcTemplate jdbc,PlatformTransactionManager manager) { return new DirectoryStore(jdbc,manager,DirectoryKind.COACH); }
    @Bean DirectoryService coachDirectoryService(DirectoryStore store,AccountClient accounts,@Value("${flowyoga.demo.enabled:false}") boolean demo) { return new DirectoryService(DirectoryKind.COACH,store,accounts,Clock.systemUTC(),demo); }
}
