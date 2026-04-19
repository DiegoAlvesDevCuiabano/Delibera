/**
 * Delibera — JavaScript global
 */

// ── Loading state nos botões de submit ──────────────────────────
document.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('form').forEach(function(form) {
    form.addEventListener('submit', function() {
      var btn = form.querySelector('button[type="submit"]');
      if (btn && !btn.classList.contains('btn-loading')) {
        btn.classList.add('btn-loading');
        btn.innerHTML = '<span class="btn-text">' + btn.innerHTML + '</span>';
      }
    });
  });
});
