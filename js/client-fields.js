import { api, bindLogout, requireOwner } from "./api.js";

const ROLES = ["director", "admin", "rop", "manager", "accountant", "estimator", "foreman"];
const BLOCKS = [
  ["contacts", "Контакты"],
  ["requisites", "Реквизиты"],
  ["contracts", "Договоры"],
  ["objects", "Объекты"],
  ["finance", "Финансы"],
  ["lead", "Исходная заявка"]
];
const SYSTEM = new Set(["name", "phone", "inn"]);
const typeSelect = document.getElementById("layout-type");
const blocksBox = document.getElementById("blocks");
const preview = document.getElementById("preview");
const previewBody = document.getElementById("preview-body");
const banner = document.getElementById("banner");
let layout = { blocks: [], fields: [] };
let dragKey = "";

function say(text) {
  banner.hidden = !text;
  banner.textContent = text || "";
}

function blocks() {
  const known = new Map(BLOCKS);
  const list = (layout.blocks || []).slice().sort(function (a, b) { return a.order - b.order; });
  BLOCKS.forEach(function (pair, index) {
    if (!list.some(function (item) { return item.key === pair[0]; })) {
      list.push({ key: pair[0], title: pair[1], enabled: true, order: index });
    }
  });
  list.forEach(function (item) { if (!item.title) item.title = known.get(item.key) || item.key; });
  return list;
}

function fieldsOf(group) {
  return (layout.fields || []).filter(function (field) {
    return field.group === group && !field.deleted;
  }).sort(function (a, b) { return a.order - b.order; });
}

function move(list, index, step) {
  const next = index + step;
  if (next < 0 || next >= list.length) return;
  const item = list.splice(index, 1)[0];
  list.splice(next, 0, item);
  list.forEach(function (entry, order) { entry.order = order; });
}

function drawPreview() {
  previewBody.replaceChildren();
  blocks().filter(function (block) { return block.enabled !== false; }).forEach(function (block) {
    const section = document.createElement("section");
    const title = document.createElement("h3");
    title.textContent = block.title;
    section.append(title);
    fieldsOf(block.key).filter(function (field) { return !field.hidden; }).forEach(function (field) {
      const row = document.createElement("p");
      row.textContent = field.title + ": " + (field.defaultValue || "—");
      section.append(row);
    });
    previewBody.append(section);
  });
}

function draw() {
  blocksBox.replaceChildren();
  const list = blocks();
  layout.blocks = list;
  list.forEach(function (block, index) {
    const item = document.createElement("article");
    item.className = "builder-block";
    item.draggable = true;
    item.addEventListener("dragstart", function () { dragKey = block.key; });
    item.addEventListener("dragover", function (event) { event.preventDefault(); });
    item.addEventListener("drop", function (event) {
      event.preventDefault();
      const from = list.findIndex(function (entry) { return entry.key === dragKey; });
      if (from < 0 || from === index) return;
      move(list, from, index - from);
      draw();
    });
    const head = document.createElement("div");
    head.className = "page-head";
    const title = document.createElement("h2");
    title.textContent = block.title;
    const enabled = document.createElement("input");
    enabled.type = "checkbox";
    enabled.checked = block.enabled !== false;
    enabled.addEventListener("change", function () { block.enabled = enabled.checked; drawPreview(); });
    const up = document.createElement("button");
    up.type = "button";
    up.className = "btn";
    up.textContent = "Выше";
    up.addEventListener("click", function () { move(list, index, -1); draw(); });
    const down = document.createElement("button");
    down.type = "button";
    down.className = "btn";
    down.textContent = "Ниже";
    down.addEventListener("click", function () { move(list, index, 1); draw(); });
    head.append(title, enabled, up, down);
    item.append(head);
    fieldsOf(block.key).forEach(function (field, fieldIndex, fieldList) {
      item.append(fieldRow(field, fieldList, fieldIndex));
    });
    blocksBox.append(item);
  });
  const removed = (layout.fields || []).filter(function (field) { return field.deleted; });
  document.getElementById("deleted").hidden = removed.length === 0;
  const deleted = document.getElementById("deleted-list");
  deleted.replaceChildren();
  removed.forEach(function (field) {
    const row = document.createElement("p");
    row.textContent = field.title + " ";
    const back = document.createElement("button");
    back.type = "button";
    back.className = "btn";
    back.textContent = "Вернуть";
    back.addEventListener("click", function () { saveField(field, { deleted: false }); });
    row.append(back);
    deleted.append(row);
  });
  drawPreview();
}

function fieldRow(field, list, index) {
  const row = document.createElement("div");
  row.className = "builder-field";
  const name = document.createElement("strong");
  name.textContent = field.title + (field.system || SYSTEM.has(field.key) ? " · системное" : "");
  const contract = document.createElement("input");
  contract.value = field.contractKey || "";
  contract.placeholder = "Поле договора";
  const hidden = document.createElement("button");
  hidden.type = "button";
  hidden.className = "btn";
  hidden.textContent = field.hidden ? "Показать" : "Скрыть";
  hidden.addEventListener("click", function () { saveField(field, { hidden: !field.hidden, contractKey: contract.value.trim() }); });
  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "btn";
  remove.textContent = "Удалить";
  remove.hidden = Boolean(field.system || SYSTEM.has(field.key));
  remove.addEventListener("click", async function () {
    const response = await api("/api/client-fields/" + field.id, { method: "DELETE" });
    if (response.ok) await load();
    else say("Системное поле можно только скрыть.");
  });
  const up = document.createElement("button");
  up.type = "button";
  up.className = "btn";
  up.textContent = "Выше";
  up.addEventListener("click", function () { move(list, index, -1); draw(); });
  const down = document.createElement("button");
  down.type = "button";
  down.className = "btn";
  down.textContent = "Ниже";
  down.addEventListener("click", function () { move(list, index, 1); draw(); });
  row.append(name, contract, hidden, up, down, remove);
  return row;
}

async function saveField(field, patch) {
  const response = await api("/api/client-fields/" + field.id, {
    method: "PUT",
    body: JSON.stringify(Object.assign({ type: typeSelect.value }, field, patch))
  });
  if (!response.ok) say("Не удалось сохранить поле.");
  else await load();
}

async function load() {
  say("");
  const response = await api("/api/client-layout?type=" + encodeURIComponent(typeSelect.value));
  if (response.status === 401) {
    say("Нет доступа к настройке карточки.");
    blocksBox.replaceChildren();
    return;
  }
  if (!response.ok) {
    say("Сервер ещё не отдаёт макет карточки.");
    layout = { blocks: [], fields: [] };
    draw();
    return;
  }
  layout = await response.json();
  if (!Array.isArray(layout.fields)) layout.fields = [];
  draw();
}

document.getElementById("new-field").addEventListener("submit", async function (event) {
  event.preventDefault();
  const body = {
    type: typeSelect.value,
    title: document.getElementById("new-title").value.trim(),
    key: document.getElementById("new-key").value.trim(),
    fieldType: document.getElementById("new-type").value,
    group: document.getElementById("new-group").value.trim() || "contacts",
    hint: document.getElementById("new-hint").value.trim(),
    defaultValue: document.getElementById("new-default").value,
    contractKey: document.getElementById("new-contract").value.trim(),
    required: document.getElementById("new-required").checked,
    roles: []
  };
  const response = await api("/api/client-fields", { method: "POST", body: JSON.stringify(body) });
  if (response.status === 401) say("Нет доступа к настройке карточки.");
  else if (!response.ok) say("Не удалось добавить поле.");
  else {
    event.target.reset();
    await load();
  }
});

document.getElementById("save-order").addEventListener("click", async function () {
  const response = await api("/api/client-fields/order", {
    method: "PUT",
    body: JSON.stringify({
      type: typeSelect.value,
      blocks: layout.blocks,
      fields: layout.fields
    })
  });
  say(response.ok ? "Порядок сохранён." : "Не удалось сохранить порядок.");
});

document.getElementById("preview-phone").addEventListener("click", function () {
  preview.classList.add("preview-phone");
  this.setAttribute("aria-pressed", "true");
  document.getElementById("preview-desktop").setAttribute("aria-pressed", "false");
});
document.getElementById("preview-desktop").addEventListener("click", function () {
  preview.classList.remove("preview-phone");
  this.setAttribute("aria-pressed", "true");
  document.getElementById("preview-phone").setAttribute("aria-pressed", "false");
});
typeSelect.addEventListener("change", function () {
  const next = new URLSearchParams(location.search);
  next.set("type", typeSelect.value);
  history.replaceState(null, "", "client-fields.html?" + next.toString());
  load();
});

const initial = new URLSearchParams(location.search).get("type");
if (initial) typeSelect.value = initial;
const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else say(access === "offline" ? "Нет сети." : "Не удалось проверить вход.");
