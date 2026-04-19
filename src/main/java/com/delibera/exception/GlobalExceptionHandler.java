package com.delibera.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
}
