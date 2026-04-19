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
      flatpickr(input, {
        locale: 'pt',
        dateFormat: 'Y-m-d',
        altInput: true,
        altFormat: 'd/m/Y',
        allowInput: true,
        disableMobile: true
      });
    });
  }

});
