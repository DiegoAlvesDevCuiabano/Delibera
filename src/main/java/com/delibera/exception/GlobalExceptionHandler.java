package com.delibera.exception;

import com.delibera.model.entity.ErroLog;
import com.delibera.repository.ErroLogRepository;
import com.delibera.service.AlertaErroService;
import com.delibera.util.IpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErroLogRepository erroLogRepository;
    private final AlertaErroService alertaErroService;

    public GlobalExceptionHandler(ErroLogRepository erroLogRepository, AlertaErroService alertaErroService) {
        this.erroLogRepository = erroLogRepository;
        this.alertaErroService = alertaErroService;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException e, RedirectAttributes redirectAttributes) {
        logger.warn("[ACESSO NEGADO] {}", e.getMessage());
        redirectAttributes.addFlashAttribute("erro", "Acesso não autorizado.");
        return "redirect:/coordenacao";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException e, RedirectAttributes redirectAttributes) {
        logger.warn("[ARGUMENTO INVÁLIDO] {}", e.getMessage());
        redirectAttributes.addFlashAttribute("erro", e.getMessage());
        return "redirect:/";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException e, RedirectAttributes redirectAttributes) {
        logger.warn("[ESTADO INVÁLIDO] {}", e.getMessage());
        redirectAttributes.addFlashAttribute("erro", e.getMessage());
        return "redirect:/coordenacao";
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public String handleNotFound() {
        return "error/404";
    }

    /**
     * Catch-all para exceções não tratadas.
     * Salva no banco, envia alerta, e redireciona para página de erro.
     */
    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception e, HttpServletRequest request) {
        logger.error("[EXCEÇÃO NÃO TRATADA] {} em {}: {}",
                e.getClass().getSimpleName(), request.getRequestURI(), e.getMessage(), e);

        try {
            // Capturar stacktrace
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stacktrace = sw.toString();
            if (stacktrace.length() > 10000) {
                stacktrace = stacktrace.substring(0, 10000) + "\n... (truncado)";
            }

            // Identificar usuário
            String usuario = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                usuario = auth.getName();
            }

            // Salvar no banco
            ErroLog erro = new ErroLog();
            erro.setTipo(e.getClass().getName());
            erro.setMensagem(e.getMessage());
            erro.setStacktrace(stacktrace);
            erro.setUrl(request.getRequestURI());
            erro.setMetodoHttp(request.getMethod());
            erro.setUsuario(usuario);
            erro.setIp(IpExtractor.getClientIp(request));
            erroLogRepository.save(erro);

            // Enviar alerta
            alertaErroService.alertarExcecao(erro);
            erro.setAlertaEnviado(true);
            erroLogRepository.save(erro);

        } catch (Exception logError) {
            logger.error("[ERRO NO HANDLER] Falha ao registrar exceção: {}", logError.getMessage());
        }

        return "error/500";
    }
}
