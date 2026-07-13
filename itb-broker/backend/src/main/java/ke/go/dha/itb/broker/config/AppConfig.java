package ke.go.dha.itb.broker.config;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;

import ke.go.dha.itb.broker.model.enums.SessionStatus;
import ke.go.dha.itb.broker.repository.TestSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
public class AppConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private final BrokerProperties properties;
    private final ObjectProvider<TestSessionRepository> sessionRepositoryProvider;

    public AppConfig(BrokerProperties properties,
                     ObjectProvider<TestSessionRepository> sessionRepositoryProvider) {
        this.properties = properties;
        this.sessionRepositoryProvider = sessionRepositoryProvider;
    }

    @Bean(name = "brokerTaskExecutor")
    public Executor brokerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsync().getCorePoolSize());
        executor.setMaxPoolSize(properties.getAsync().getMaxPoolSize());
        executor.setThreadNamePrefix("broker-exec-");
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return brokerTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error("Uncaught async exception in {}", method.getName(), ex);
            if (params.length > 0 && params[0] instanceof UUID sessionId) {
                TestSessionRepository repository = sessionRepositoryProvider.getIfAvailable();
                if (repository != null) {
                    repository.findById(sessionId).ifPresent(session -> {
                        session.setStatus(SessionStatus.FAILED);
                        session.setCompletedAt(LocalDateTime.now());
                        repository.save(session);
                    });
                }
            }
        };
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
        };
    }
}
