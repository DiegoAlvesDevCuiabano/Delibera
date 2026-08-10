package com.delibera.controller;

import com.delibera.model.entity.ErroLog;
import com.delibera.repository.ErroLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/root/erros")
public class AdminErroController {

    private final ErroLogRepository erroLogRepository;

    public AdminErroController(ErroLogRepository erroLogRepository) {
        this.erroLogRepository = erroLogRepository;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "false") boolean todos,
                          Model model) {
        Page<ErroLog> erros;
        if (todos) {
            erros = erroLogRepository.findAllByOrderByCriadoEmDesc(PageRequest.of(page, 20));
        } else {
            erros = erroLogRepository.findByResolvidoFalseOrderByCriadoEmDesc(PageRequest.of(page, 20));
        }

        model.addAttribute("erros", erros);
        model.addAttribute("pendentes", erroLogRepository.countByResolvidoFalse());
        model.addAttribute("todos", todos);
        return "pages/root/erros";
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        ErroLog erro = erroLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Erro não encontrado"));
        model.addAttribute("erro", erro);
        return "pages/root/erro-detalhe";
    }

    @PostMapping("/{id}/resolver")
    public String resolver(@PathVariable Long id) {
        ErroLog erro = erroLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Erro não encontrado"));
        erro.setResolvido(true);
        erroLogRepository.save(erro);
        return "redirect:/root/erros";
    }
}
