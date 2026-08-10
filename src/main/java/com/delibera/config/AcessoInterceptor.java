package com.delibera.config;

import com.delibera.model.entity.Instituicao;
import com.delibera.model.entity.Usuario;
import com.delibera.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Corta o acesso da coordenação quando o piloto expirou sem voucher, quando o
 * voucher expirou, ou quando a instituição foi desativada.
 *
 * <p>Sem isto {@code Instituicao.isAcessoAtivo()} não era consultado em lugar nenhum
 * do fluxo autenticado: o piloto de 30 dias nunca terminava e não havia caminho
 * técnico para converter o cliente em pagante.
 *
 * <p>Usuários cross-tenant (ROOT/GESTAO) passam sempre — eles precisam entrar
 * justamente para reativar quem expirou.
 */
public class AcessoInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AcessoInterceptor.class);

    private final UsuarioService usuarioService;

    public AcessoInterceptor(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return true; // quem cuida disso é o SecurityConfig
        }

        Usuario usuario;
        try {
            usuario = usuarioService.buscarPorEmail(auth.getName());
        } catch (RuntimeException e) {
            return true; // usuário sumiu do banco: deixa o fluxo normal tratar
        }

        if (usuario.isCrossTenant()) {
            return true;
        }

        Instituicao inst = usuario.getInstituicao();
        if (inst != null && inst.isAtivo() && inst.isAcessoAtivo()) {
            return true;
        }

        String motivo = (inst == null) ? "sem instituição vinculada"
                : (!inst.isAtivo() ? "instituição desativada" : "piloto/voucher expirado");
        logger.info("[ACESSO BLOQUEADO] {} — {}", usuario.getEmail(), motivo);

        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        SecurityContextHolder.clearContext();

        try {
            response.sendRedirect(request.getContextPath() + "/login?acessoExpirado=true");
        } catch (java.io.IOException e) {
            logger.warn("[ACESSO BLOQUEADO] falha ao redirecionar: {}", e.getMessage());
        }
        return false;
    }
}
