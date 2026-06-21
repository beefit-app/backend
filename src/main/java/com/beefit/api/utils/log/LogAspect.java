package com.beefit.api.utils.log;

import com.beefit.api.domain.AuditLog;
import com.beefit.api.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

// Essa é a classe principal. Ela intercepta todas as requisições decoradas com @LogAudit e gera o log efetivamente
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final AuditLogRepository auditLogRepository;

    @AfterReturning("@annotation(logAudit)") // Executa esse método depois do método decorado pela annotation
    public void logAction(JoinPoint joinPoint, LogAudit logAudit) {

        String action = logAudit.action();
        String description = logAudit.description();

        String adminUsername = "UNDEFINED";

        //SecurityContextHolder é uma classe fundamental do SpringSecurity que armazena o usuário logado durante a requsição
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth != null && auth.isAuthenticated()){
            adminUsername = auth.getName();
        }

        Object[] args = joinPoint.getArgs();
        String details = "Arguments: " + Arrays.toString(args);

        AuditLog auditLog = new AuditLog();
        auditLog.setAdminUsername(adminUsername);
        auditLog.setAction(action);
        auditLog.setDescription(description);
        auditLog.setDetails(details);

        auditLogRepository.save(auditLog);
    }

}
