/**
 * Delibera — JavaScript global
 */

document.addEventListener('DOMContentLoaded', function() {

  // ── Loading state nos botões de submit ──────────────────────
  document.querySelectorAll('form').forEach(function(form) {
    form.addEventListener('submit', function() {
      var btn = form.querySelector('button[type="submit"]');
      if (btn && !btn.classList.contains('btn-loading')) {
        btn.classList.add('btn-loading');
        btn.innerHTML = '<span class="btn-text">' + btn.innerHTML + '</span>';
      }
    });
  });

  // ── Flatpickr — inicializar todos os input[type="date"] ────
  if (typeof flatpickr !== 'undefined') {
    document.querySelectorAll('input[type="date"]').forEach(function(input) {
      // Trocar type para text (evita picker nativo)
      input.type = 'text';
      input.placeholder = input.getAttribute('placeholder') || 'Selecione a data';

      // Wrap com ícone à direita se não estiver já em icon-group
      if (!input.closest('.input-icon-group') && !input.closest('.flatpickr-wrapped')) {
        var wrapper = document.createElement('div');
        wrapper.className = 'flatpickr-wrapped';
        wrapper.style.cssText = 'position:relative;';
        input.parentNode.insertBefore(wrapper, input);
        wrapper.appendChild(input);

        var icon = document.createElement('span');
        icon.className = 'flatpickr-icon-right';
        icon.innerHTML = '<i class="bi bi-calendar3"></i>';
        icon.style.cssText = 'position:absolute;right:14px;top:50%;transform:translateY(-50%);color:#94a3b8;pointer-events:none;font-size:1.1rem;transition:color 0.2s;';
        wrapper.appendChild(icon);
      }

      var fp = flatpickr(input, {
        locale: 'pt',
        dateFormat: 'Y-m-d',
        altInput: true,
        altFormat: 'd/m/Y',
        allowInput: true,
        disableMobile: true,
        position: 'below'
      });

      // Atualizar ícone no focus
      if (fp.altInput) {
        fp.altInput.placeholder = input.getAttribute('placeholder') || 'Selecione a data';
        fp.altInput.style.paddingRight = '2.75rem';
      }
    });
  }

});
