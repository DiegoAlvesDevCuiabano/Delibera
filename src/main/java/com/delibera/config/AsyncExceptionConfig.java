package com.delibera.config;

import com.delibera.model.entity.ErroLog;
import com.delibera.repository.ErroLogRepository;
import com.delibera.service.AlertaErroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

/**
 * Captura exceções de métodos @Async que não são pegas pelo GlobalExceptionHandler.
 * Salva no ErroLog e envia alerta por email.
 */
@Configuration
public class AsyncExceptionConfig implements AsyncConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncExceptionConfig.class);

    private final ErroLogRepository erroLogRepository;
    private final AlertaErroService alertaErroService;

    public AsyncExceptionConfig(ErroLogRepository erroLogRepository, AlertaErroService alertaErroService) {
        this.erroLogRepository = erroLogRepository;
        this.alertaErroService = alertaErroService;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            logger.error("[ASYNC ERRO] {}.{}: {}", method.getDeclaringClass().getSimpleName(),
                    method.getName(), ex.getMessage(), ex);

            try {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                String stacktrace = sw.toString();
                if (stacktrace.length() > 10000) {
                    stacktrace = stacktrace.substring(0, 10000) + "\n... (truncado)";
                }

                ErroLog erro = new ErroLog();
                erro.setTipo(ex.getClass().getName());
                erro.setMensagem(ex.getMessage());
                erro.setStacktrace(stacktrace);
                erro.setUrl("@Async: " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
                erro.setMetodoHttp("ASYNC");
                erroLogRepository.save(erro);

                alertaErroService.alertarExcecao(erro);
                erro.setAlertaEnviado(true);
                erroLogRepository.save(erro);
            } catch (Exception logError) {
                logger.error("[ASYNC ERRO HANDLER] Falha ao registrar: {}", logError.getMessage());
            }
        };
    }
}
