import { api } from "./api.js";
import { fieldError, setBusy } from "./ui.js";

const form = document.getElementById("login");
const note = document.getElementById("note");
const password = document.getElementById("password");
const toggle = document.getElementById("toggle");

const session = await api("/api/session").catch(function () { return null; });
if (session && session.status === 204) {
  location.replace("leads.html");
}

toggle.addEventListener("click", function () {
  const show = password.type === "password";
  password.type = show ? "text" : "password";
  toggle.textContent = show ? "Скрыть" : "Показать";
  toggle.setAttribute("aria-pressed", show ? "true" : "false");
  password.focus();
});

form.addEventListener("submit", async function (event) {
  event.preventDefault();
  note.hidden = true;
  note.textContent = "";
  fieldError(password, "");
  if (!password.value) {
    fieldError(password, "Введите пароль.");
    password.focus();
    return;
  }
  const button = form.querySelector("button[type=submit]");
  setBusy(button, true, "Входим…");
  try {
    const response = await api("/api/session", {
      method: "POST",
      body: JSON.stringify({ password: password.value })
    });
    if (response.status === 204) {
      password.value = "";
      location.href = "leads.html";
      return;
    }
    note.hidden = false;
    note.textContent = "Неверный логин или пароль.";
    password.focus();
  } catch {
    note.hidden = false;
    note.textContent = "Не удалось войти. Попробуйте ещё раз.";
  } finally {
    setBusy(button, false);
  }
});
