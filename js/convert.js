import { api } from "./api.js";
import { SOURCES } from "./format.js";

const TYPES = [
  ["person", "Физлицо"],
  ["sole", "ИП"],
  ["company", "Юрлицо"]
];

function field(label, control) {
  const wrap = document.createElement("div");
  wrap.className = "field";
  const name = document.createElement("label");
  name.textContent = label;
  wrap.append(name, control);
  return wrap;
}

function input(value) {
  const node = document.createElement("input");
  node.value = value || "";
  return node;
}

export function offerConvert(lead) {
  if (!lead || lead.clientId) return;
  const dialog = document.createElement("dialog");
  const body = document.createElement("form");
  body.className = "dialog-body";
  body.method = "dialog";
  const title = document.createElement("h2");
  title.textContent = "Этап «Договор»";
  const text = document.createElement("p");
  text.textContent = "Перевести заявку в клиенты? Сам перенос не выполнится, пока вы не подтвердите его в следующем окне.";
  const actions = document.createElement("div");
  actions.className = "dialog-actions";
  const yes = document.createElement("button");
  yes.className = "btn btn-primary";
  yes.type = "submit";
  yes.value = "yes";
  yes.textContent = "Перевести";
  const later = document.createElement("button");
  later.className = "btn";
  later.type = "submit";
  later.value = "later";
  later.textContent = "Позже";
  actions.append(yes, later);
  body.append(title, text, actions);
  dialog.append(body);
  document.body.append(dialog);
  dialog.showModal();
  dialog.addEventListener("close", function () {
    const go = dialog.returnValue === "yes";
    dialog.remove();
    if (go) openConvert(lead);
  }, { once: true });
}

export async function openConvert(lead) {
  if (!lead || lead.clientId) {
    if (lead && lead.clientId) location.href = "client.html?id=" + lead.clientId;
    return;
  }
  let duplicates = [];
  try {
    const response = await api("/api/clients/duplicates?phone=" + encodeURIComponent(lead.phone || ""));
    if (response.ok) {
      const body = await response.json();
      duplicates = Array.isArray(body) ? body : (body.clients || []);
    }
  } catch {
    duplicates = [];
  }

  const dialog = document.createElement("dialog");
  const form = document.createElement("form");
  form.className = "dialog-body";
  const title = document.createElement("h2");
  title.textContent = "Перевести в клиенты";
  form.append(title);

  const merge = document.createElement("select");
  merge.append(new Option("Создать нового", ""));
  duplicates.forEach(function (item) {
    const label = (item.name || "Клиент") + (item.phone ? " · " + item.phone : "");
    merge.append(new Option("Объединить: " + label, String(item.id)));
  });
  if (duplicates.length) merge.value = String(duplicates[0].id);
  form.append(field("Дубль", merge));

  const name = input(lead.name);
  const phone = input(lead.phone);
  const objectType = input(lead.objectType);
  const source = document.createElement("select");
  Object.keys(SOURCES).forEach(function (key) { source.append(new Option(SOURCES[key], key)); });
  if (lead.source) source.value = lead.source;
  const type = document.createElement("select");
  TYPES.forEach(function (pair) { type.append(new Option(pair[1], pair[0])); });
  const telegram = input(lead.telegram && lead.telegram.username ? lead.telegram.username : "");
  const responsible = input(lead.responsible || lead.managerName || "");
  form.append(field("Имя", name));
  form.append(field("Телефон", phone));
  form.append(field("Тип объекта", objectType));
  form.append(field("Источник", source));
  form.append(field("Аккаунт Telegram", telegram));
  form.append(field("Ответственный", responsible));
  form.append(field("Тип клиента", type));

  const next = document.createElement("select");
  next.append(new Option("Позже", "later"));
  next.append(new Option("Загрузить договор", "contract"));
  form.append(field("Дальше", next));

  const status = document.createElement("p");
  status.className = "note";
  status.hidden = true;
  const actions = document.createElement("div");
  actions.className = "dialog-actions";
  const submit = document.createElement("button");
  submit.className = "btn btn-primary";
  submit.type = "submit";
  submit.textContent = "Перевести";
  const cancel = document.createElement("button");
  cancel.className = "btn";
  cancel.type = "button";
  cancel.textContent = "Закрыть";
  cancel.addEventListener("click", function () { dialog.close(); });
  actions.append(submit, cancel);
  form.append(status, actions);
  dialog.append(form);
  document.body.append(dialog);
  dialog.showModal();

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    status.hidden = true;
    submit.disabled = true;
    const body = {
      merge: Boolean(merge.value),
      clientId: merge.value ? Number(merge.value) : null,
      type: type.value,
      name: name.value.trim(),
      phone: phone.value.trim(),
      objectType: objectType.value.trim(),
      source: source.value,
      telegramUsername: telegram.value.trim().replace(/^@+/, ""),
      next: next.value
    };
    if (responsible.value.trim()) body.responsible = responsible.value.trim();
    try {
      const response = await api("/api/leads/" + lead.id + "/convert", {
        method: "POST",
        body: JSON.stringify(body)
      });
      if (response.status === 401) {
        location.replace("login.html");
        return;
      }
      const payload = await response.json().catch(function () { return {}; });
      if (response.status === 409 && payload.error === "converted") {
        status.hidden = false;
        status.textContent = "Заявка уже перенесена.";
        if (payload.clientId) {
          const link = document.createElement("a");
          link.href = "client.html?id=" + payload.clientId;
          link.textContent = " Открыть клиента";
          status.append(link);
        }
        return;
      }
      if (response.status === 409) {
        status.hidden = false;
        status.textContent = "Такой клиент уже есть. Выберите объединение.";
        return;
      }
      if (!response.ok) {
        status.hidden = false;
        status.textContent = "Не удалось перевести заявку.";
        return;
      }
      const clientId = payload.clientId || payload.id;
      const query = new URLSearchParams();
      query.set("id", clientId);
      query.set("created", "1");
      if (next.value === "contract") query.set("next", "contract");
      location.href = "client.html?" + query.toString();
    } catch {
      status.hidden = false;
      status.textContent = "Не удалось перевести заявку.";
    } finally {
      submit.disabled = false;
    }
  });
  dialog.addEventListener("close", function () { dialog.remove(); });
}
