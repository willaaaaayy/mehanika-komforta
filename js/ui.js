export function toast(message, options = {}) {
  const region = document.getElementById("toasts");
  if (!region) return function () {};
  const item = document.createElement("div");
  item.className = "toast";
  item.setAttribute("role", "status");
  const text = document.createElement("p");
  text.textContent = message;
  item.append(text);
  const remove = function () { item.remove(); };
  const timer = window.setTimeout(remove, 8000);
  if (options.undo) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-text";
    button.textContent = "Отменить";
    button.addEventListener("click", async function () {
      window.clearTimeout(timer);
      button.disabled = true;
      try {
        await options.undo();
      } finally {
        remove();
      }
    });
    item.append(button);
  }
  region.append(item);
  return remove;
}

export function setBusy(button, busy, label) {
  if (!button) return;
  if (busy) {
    button.dataset.label = button.textContent;
    button.disabled = true;
    button.classList.add("is-loading");
    button.setAttribute("aria-busy", "true");
    if (label) button.textContent = label;
    return;
  }
  button.disabled = false;
  button.classList.remove("is-loading");
  button.removeAttribute("aria-busy");
  if (button.dataset.label) button.textContent = button.dataset.label;
}

export function fieldError(input, message) {
  const error = document.getElementById(input.id + "-error");
  input.classList.toggle("is-invalid", Boolean(message));
  input.setAttribute("aria-invalid", message ? "true" : "false");
  if (error) {
    error.hidden = !message;
    error.textContent = message || "";
  }
}

export function clearErrors(form) {
  form.querySelectorAll("input, select, textarea").forEach(function (input) {
    fieldError(input, "");
  });
}
