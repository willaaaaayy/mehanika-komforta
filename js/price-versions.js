import { api } from "./api.js";
import { money } from "./format.js";

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

const CHANGES = [
  ["extra", "Допработа"],
  ["removed", "Исключено"],
  ["volume", "Объём"],
  ["price", "Цена"]
];

export async function renderPriceVersions(box, objectId) {
  box.replaceChildren();
  const response = await api("/api/objects/" + objectId + "/price-versions");
  if (response.status === 401) {
    location.replace("login.html");
    return;
  }
  const versions = response.ok ? await response.json() : [];
  const list = Array.isArray(versions) ? versions : (versions.versions || []);
  const head = document.createElement("div");
  head.className = "page-head";
  const title = document.createElement("h2");
  title.textContent = "Итоговый прайс";
  const add = document.createElement("button");
  add.type = "button";
  add.className = "btn btn-primary";
  add.textContent = "Редакция";
  add.addEventListener("click", function () { openEditor(objectId, box); });
  head.append(title, add);
  box.append(head);
  if (!response.ok) {
    const note = document.createElement("p");
    note.textContent = "Сервер ещё не отдаёт редакции прайса.";
    box.append(note);
    return;
  }
  if (!list.length) {
    const empty = document.createElement("p");
    empty.textContent = "Редакций пока нет. Первая версия берётся из договора.";
    box.append(empty);
    return;
  }
  const compare = document.createElement("div");
  compare.className = "tools";
  const from = document.createElement("select");
  const to = document.createElement("select");
  list.forEach(function (version) {
    const label = "v" + (version.version || version.id);
    from.append(new Option(label, String(version.id)));
    to.append(new Option(label, String(version.id)));
  });
  to.value = String(list[0].id);
  if (list[1]) from.value = String(list[1].id);
  const button = document.createElement("button");
  button.type = "button";
  button.className = "btn";
  button.textContent = "Сравнить";
  const result = document.createElement("div");
  button.addEventListener("click", async function () {
    const diff = await api("/api/objects/" + objectId + "/price-versions/compare?from=" + from.value + "&to=" + to.value);
    result.replaceChildren();
    if (!diff.ok) {
      result.textContent = "Сравнение не пришло.";
      return;
    }
    const body = await diff.json();
    const amount = body.amount || {};
    const line = document.createElement("p");
    line.textContent = "Сумма: " + sumText(amount.was) + " → " + sumText(amount.now)
      + (body.difference == null ? "" : " · разница " + sumText(body.difference));
    result.append(line);
    (body.lines || []).forEach(function (item) {
      const row = document.createElement("p");
      row.textContent = (item.title || "Позиция") + ": " + (item.was || "—") + " → " + (item.now || "—");
      result.append(row);
    });
  });
  compare.append(from, to, button);
  box.append(compare, result);
  list.forEach(function (version) { box.append(versionBlock(version, objectId, box)); });
}

function versionBlock(version, objectId, box) {
  const block = document.createElement("article");
  const title = document.createElement("h3");
  title.textContent = "v" + (version.version || version.id)
    + (version.approved ? " · утверждена" : " · черновик");
  block.append(title);
  const meta = document.createElement("p");
  meta.textContent = [version.date, version.author, version.reason].filter(Boolean).join(" · ")
    + " · сумма " + sumText(version.amount);
  block.append(meta);
  (version.lines || []).forEach(function (line) {
    const row = document.createElement("p");
    const change = CHANGES.find(function (item) { return item[0] === line.change; });
    row.textContent = (line.title || "Позиция")
      + (change ? " · " + change[1] : "")
      + " · план " + (line.plan == null ? "—" : line.plan)
      + " · факт " + (line.fact == null ? "—" : line.fact)
      + " · клиенту " + sumText(line.clientPrice)
      + " · себестоимость " + sumText(line.cost)
      + " · маржа " + sumText(line.margin);
    block.append(row);
  });
  if (!version.approved && version.id) {
    const approve = document.createElement("button");
    approve.type = "button";
    approve.className = "btn btn-primary";
    approve.textContent = "Утвердить";
    approve.addEventListener("click", async function () {
      const response = await api("/api/objects/" + objectId + "/price-versions/" + version.id + "/approve", { method: "POST" });
      if (!response.ok) return;
      location.href = "object.html?id=" + objectId + "&tab=price";
    });
    block.append(approve);
  }
  if (version.id) {
    const pdf = document.createElement("a");
    pdf.className = "btn";
    pdf.href = "/api/objects/" + objectId + "/price-versions/" + version.id + "/export?format=pdf";
    pdf.textContent = "PDF";
    const xlsx = document.createElement("a");
    xlsx.className = "btn";
    xlsx.href = "/api/objects/" + objectId + "/price-versions/" + version.id + "/export?format=xlsx";
    xlsx.textContent = "Excel";
    block.append(pdf, xlsx);
  }
  return block;
}

function openEditor(objectId, box) {
  const dialog = document.createElement("dialog");
  const form = document.createElement("form");
  form.className = "dialog-body";
  const title = document.createElement("h2");
  title.textContent = "Редакция";
  const reason = document.createElement("input");
  reason.required = true;
  reason.placeholder = "Причина";
  const lines = document.createElement("div");
  function addLine() {
    const row = document.createElement("div");
    row.className = "schedule-edit";
    const name = document.createElement("input");
    name.placeholder = "Работа";
    const change = document.createElement("select");
    CHANGES.forEach(function (pair) { change.append(new Option(pair[1], pair[0])); });
    const quantity = document.createElement("input");
    quantity.placeholder = "Объём";
    quantity.inputMode = "decimal";
    const price = document.createElement("input");
    price.placeholder = "Цена клиенту";
    price.inputMode = "decimal";
    row.append(name, change, quantity, price);
    lines.append(row);
  }
  addLine();
  const more = document.createElement("button");
  more.type = "button";
  more.className = "btn btn-text";
  more.textContent = "Строка";
  more.addEventListener("click", addLine);
  const status = document.createElement("p");
  status.className = "note";
  status.hidden = true;
  const actions = document.createElement("div");
  actions.className = "dialog-actions";
  const save = document.createElement("button");
  save.className = "btn btn-primary";
  save.type = "submit";
  save.textContent = "Сохранить";
  const close = document.createElement("button");
  close.className = "btn";
  close.type = "button";
  close.textContent = "Закрыть";
  close.addEventListener("click", function () { dialog.close(); });
  actions.append(save, close);
  form.append(title, reason, lines, more, status, actions);
  dialog.append(form);
  document.body.append(dialog);
  dialog.showModal();
  dialog.addEventListener("close", function () { dialog.remove(); });
  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    const payload = {
      reason: reason.value.trim(),
      lines: [...lines.querySelectorAll(".schedule-edit")].map(function (row) {
        const fields = row.querySelectorAll("input, select");
        return {
          title: fields[0].value.trim(),
          change: fields[1].value,
          quantity: fields[2].value.trim(),
          clientPrice: fields[3].value.trim()
        };
      })
    };
    const response = await api("/api/objects/" + objectId + "/price-versions", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    if (!response.ok) {
      status.hidden = false;
      status.textContent = "Не удалось сохранить редакцию.";
      return;
    }
    dialog.close();
    await renderPriceVersions(box, objectId);
  });
}
