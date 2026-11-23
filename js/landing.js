import { api, errorCode } from "./api.js";
import { digits } from "./format.js";
import { clearErrors, fieldError, setBusy } from "./ui.js";

const menu = document.getElementById("menu");
const nav = document.getElementById("site-nav");

menu.addEventListener("click", function () {
  const open = nav.classList.toggle("is-open");
  menu.setAttribute("aria-expanded", open ? "true" : "false");
});

nav.addEventListener("click", function (event) {
  if (event.target.closest("a")) {
    nav.classList.remove("is-open");
    menu.setAttribute("aria-expanded", "false");
  }
});

document.addEventListener("keydown", function (event) {
  if (event.key === "Escape") {
    nav.classList.remove("is-open");
    menu.setAttribute("aria-expanded", "false");
  }
});

const form = document.getElementById("lead");
const note = document.getElementById("form-note");

function validName(value) {
  const text = value.trim();
  if (text.length < 2 || text.length > 80) return "Имя — от 2 до 80 символов.";
  return "";
}

function validPhone(value) {
  if (value.trim().length > 40) return "Телефон длиннее 40 символов.";
  const count = digits(value).length;
  if (count < 10 || count > 15) return "Нужен телефон из 10–15 цифр.";
  return "";
}

function validObject(value) {
  const text = value.trim();
  if (text.length < 2 || text.length > 80) return "Укажите тип объекта, от 2 до 80 символов.";
  return "";
}

form.addEventListener("submit", async function (event) {
  event.preventDefault();
  clearErrors(form);
  note.hidden = true;
  note.textContent = "";
  const name = document.getElementById("name");
  const phone = document.getElementById("phone");
  const objectType = document.getElementById("object-type");
  const nameError = validName(name.value);
  const phoneError = validPhone(phone.value);
  const objectError = validObject(objectType.value);
  fieldError(name, nameError);
  fieldError(phone, phoneError);
  fieldError(objectType, objectError);
  const first = [name, phone, objectType].find(function (input) {
    return input.classList.contains("is-invalid");
  });
  if (first) {
    first.focus();
    return;
  }
  const button = form.querySelector("button[type=submit]");
  setBusy(button, true, "Отправляем…");
  try {
    const response = await fetch("/api/leads", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: name.value.trim(),
        phone: phone.value.trim(),
        objectType: objectType.value.trim(),
        source: "site"
      })
    });
    if (response.status === 201) {
      form.reset();
      note.hidden = false;
      note.textContent = "Заявку записали.";
      return;
    }
    const code = await errorCode(response);
    if (code === "phone") fieldError(phone, "Нужен телефон из 10–15 цифр.");
    else if (code === "name") fieldError(name, "Проверьте имя.");
    else if (code === "objectType") fieldError(objectType, "Проверьте тип объекта.");
    else {
      note.hidden = false;
      note.textContent = "Не удалось записать заявку. Попробуйте ещё раз.";
    }
    const invalid = [name, phone, objectType].find(function (input) {
      return input.classList.contains("is-invalid");
    });
    if (invalid) invalid.focus();
  } catch {
    note.hidden = false;
    note.textContent = "Не удалось записать заявку. Попробуйте ещё раз.";
  } finally {
    setBusy(button, false);
  }
});
