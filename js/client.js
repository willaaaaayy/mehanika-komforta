import { api, bindLogout, requireOwner } from "./api.js";
import { money, telHref } from "./format.js";
import { toast } from "./ui.js";
import { contractBlock, openContractUpload } from "./contract-upload.js";
import { objectStatus } from "./object-status.js";

const params = new URLSearchParams(location.search);
const id = Number(params.get("id"));
const tab = params.get("tab") || "overview";
const banner = document.getElementById("banner");
const skeleton = document.getElementById("skeleton");
const card = document.getElementById("card");
let client = null;
let sourceLead = null;

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function showTab(name) {
  ["overview", "data", "contracts", "objects", "finance", "history"].forEach(function (key) {
    document.getElementById("panel-" + key).hidden = key !== name;
  });
  document.querySelectorAll("#tabs a").forEach(function (link) {
    const next = new URLSearchParams(location.search);
    next.set("id", String(id));
    next.set("tab", link.dataset.tab);
    next.delete("created");
    link.href = "client.html?" + next.toString();
    if (link.dataset.tab === name) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
}

function line(label, value) {
  const row = document.createElement("p");
  const name = document.createElement("strong");
  name.textContent = label + " ";
  row.append(name, document.createTextNode(value == null || value === "" ? "—" : String(value)));
  return row;
}

function renderOverview() {
  const box = document.getElementById("panel-overview");
  box.replaceChildren();
  const lead = sourceLead;
  const block = document.createElement("div");
  const title = document.createElement("h2");
  title.textContent = "Исходная заявка";
  block.append(title);
  if (!client.sourceLeadId && !lead) {
    block.append(line("Заявка", "не привязана"));
    box.append(block);
    return;
  }
  if (lead) {
    block.append(line("Имя", lead.name));
    block.append(line("Телефон", lead.phone));
    if (lead.estimate) {
      const estimate = lead.estimate;
      const text = estimate.low != null && estimate.high != null
        ? money(estimate.low) + " – " + money(estimate.high)
        : "—";
      block.append(line("Вилка", estimate.teaching ? "Учебный прайс. " + text : text));
    }
    if (lead.telegram && lead.telegram.username) block.append(line("Telegram", "@" + lead.telegram.username));
    const profile = document.createElement("img");
    profile.className = "profile";
    profile.alt = "Фото профиля в Telegram";
    if (lead.telegram && lead.telegram.photo && client.sourceLeadId) {
      profile.src = "/api/leads/" + client.sourceLeadId + "/telegram-photo";
      block.append(profile);
    }
    const photos = document.createElement("div");
    photos.className = "photos";
    photos.id = "source-photos";
    block.append(photos);
  } else {
    block.append(line("Заявка", "не открылась"));
  }
  if (params.get("next") === "contract") {
    const link = document.createElement("a");
    link.className = "btn";
    link.href = "finance.html";
    link.textContent = "Загрузить договор";
    block.append(link);
  }
  box.append(block);
}

async function loadPhotos() {
  if (!client.sourceLeadId) return;
  const box = document.getElementById("source-photos");
  if (!box) return;
  const response = await api("/api/leads/" + client.sourceLeadId + "/photos");
  if (!response.ok) return;
  const items = await response.json();
  if (!Array.isArray(items)) return;
  items.forEach(function (item) {
    const image = document.createElement("img");
    image.alt = "Фото объекта";
    image.src = "/api/leads/" + client.sourceLeadId + "/photos/" + item.id;
    box.append(image);
  });
}

function controlFor(field, value) {
  const kind = field.fieldType || field.type;
  if (kind === "checkbox") {
    const input = document.createElement("input");
    input.type = "checkbox";
    input.checked = value === true || value === "true";
    return input;
  }
  if (kind === "select" || kind === "multiselect") {
    const box = document.createElement("div");
    const options = Array.isArray(field.options) ? field.options : String(field.options || "").split(",");
    options.map(function (item) { return String(item).trim(); }).filter(Boolean).forEach(function (option) {
      const label = document.createElement("label");
      const input = document.createElement("input");
      input.type = kind === "multiselect" ? "checkbox" : "radio";
      input.name = field.key;
      input.value = option;
      const selected = Array.isArray(value) ? value.indexOf(option) >= 0 : value === option;
      input.checked = selected;
      label.append(input, document.createTextNode(" " + option));
      box.append(label);
    });
    return box;
  }
  const input = document.createElement("input");
  if (kind === "date") input.type = "date";
  else if (kind === "email") input.type = "email";
  else if (kind === "phone") input.type = "tel";
  else if (kind === "link") input.type = "url";
  else if (kind === "file") input.type = "file";
  else if (kind === "number" || kind === "money") input.inputMode = "decimal";
  if (kind !== "file") input.value = value == null || value === "" ? (field.defaultValue || "") : value;
  return input;
}

function readControl(field, node) {
  const kind = field.fieldType || field.type;
  if (kind === "checkbox") return node.checked;
  if (kind === "multiselect" || kind === "select") {
    const chosen = [...node.querySelectorAll("input:checked")].map(function (item) { return item.value; });
    return kind === "select" ? (chosen[0] || "") : chosen;
  }
  if (kind === "file") return node.files && node.files[0] ? node.files[0] : null;
  return node.value.trim();
}

async function renderData() {
  const box = document.getElementById("panel-data");
  box.replaceChildren();
  const settings = document.createElement("p");
  const settingsLink = document.createElement("a");
  settingsLink.href = "client-fields.html?type=" + encodeURIComponent(client.type || "person");
  settingsLink.textContent = "Настроить поля";
  settings.append(settingsLink);
  box.append(settings);
  let layout = null;
  const layoutResponse = await api("/api/client-layout?type=" + encodeURIComponent(client.type || "person"));
  if (layoutResponse.ok) layout = await layoutResponse.json();
  const form = document.createElement("form");
  const inputs = {};
  const custom = client.customFields || {};
  const defined = layout && Array.isArray(layout.fields) ? layout.fields.filter(function (field) {
    return !field.hidden && !field.deleted;
  }) : [
    { key: "name", title: "Имя", fieldType: "text", system: true },
    { key: "phone", title: "Телефон", fieldType: "phone", system: true },
    { key: "email", title: "Почта", fieldType: "email" },
    { key: "inn", title: "ИНН", fieldType: "text", system: true },
    { key: "address", title: "Адрес", fieldType: "text" }
  ];
  defined.forEach(function (field) {
    const wrap = document.createElement("div");
    wrap.className = "field";
    const label = document.createElement("label");
    label.textContent = field.title || field.key;
    const stored = Object.prototype.hasOwnProperty.call(client, field.key) && ["name", "phone", "email", "inn", "address"].indexOf(field.key) >= 0
      ? client[field.key]
      : custom[field.key];
    const node = controlFor(field, stored);
    inputs[field.key] = { field: field, node: node };
    if (field.hint) {
      const hint = document.createElement("p");
      hint.className = "muted";
      hint.textContent = field.hint;
      wrap.append(label, node, hint);
    } else wrap.append(label, node);
    form.append(wrap);
  });
  const type = document.createElement("select");
  [["person", "Физлицо"], ["sole", "ИП"], ["company", "Юрлицо"]].forEach(function (pair) {
    type.append(new Option(pair[1], pair[0]));
  });
  type.value = client.type || "person";
  const typeWrap = document.createElement("div");
  typeWrap.className = "field";
  const typeLabel = document.createElement("label");
  typeLabel.textContent = "Тип";
  typeWrap.append(typeLabel, type);
  form.append(typeWrap);
  const button = document.createElement("button");
  button.className = "btn btn-primary";
  button.type = "submit";
  button.textContent = "Сохранить";
  form.append(button);
  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    const body = { type: type.value, customFields: Object.assign({}, custom) };
    const files = [];
    Object.keys(inputs).forEach(function (key) {
      const item = inputs[key];
      const value = readControl(item.field, item.node);
      if ((item.field.fieldType || item.field.type) === "file") {
        if (value) files.push({ key: key, file: value });
        return;
      }
      if (["name", "phone", "email", "inn", "address"].indexOf(key) >= 0) body[key] = value;
      else body.customFields[key] = value;
    });
    const response = await api("/api/clients/" + id, { method: "PATCH", body: JSON.stringify(body) });
    if (response.ok) {
      client = Object.assign(client, body);
      for (const item of files) {
        const formData = new FormData();
        formData.append("key", item.key);
        formData.append("file", item.file, item.file.name);
        await api("/api/clients/" + id + "/files", { method: "POST", body: formData });
      }
      document.getElementById("card-title").textContent = body.name || client.name || "Клиент";
      toast("Сохранили.");
    } else {
      banner.hidden = false;
      banner.textContent = "Не удалось сохранить.";
    }
  });
  box.append(form);
}

function renderList(panel, items, emptyText, line) {
  const box = document.getElementById(panel);
  box.replaceChildren();
  if (!items || !items.length) {
    box.textContent = emptyText;
    return;
  }
  items.forEach(function (item) { box.append(line(item)); });
}

function paymentText(next) {
  if (!next) return "—";
  const amount = sumText(next.amount);
  if (next.date || next.due) return amount + ", " + (next.date || next.due);
  if (next.dueRule && next.dueRule.basis === "act") return amount + ", по акту";
  return amount;
}

function renderHeader() {
  const status = { current: "Текущий", archived: "Архив" };
  document.getElementById("card-title").textContent = client.name || "Клиент";
  document.getElementById("card-status").textContent = status[client.status] || client.status || "";
  const box = document.getElementById("client-metrics");
  box.replaceChildren();
  [["Сумма договоров", sumText(client.amount)], ["Оплачено", sumText(client.paid)], ["Остаток", sumText(client.due)], ["Ближайший платёж", paymentText(client.nextPayment)]].forEach(function (item) {
    const card = document.createElement("div");
    card.className = "metric";
    const name = document.createElement("span");
    name.textContent = item[0];
    const value = document.createElement("strong");
    value.className = "num";
    value.textContent = item[1];
    card.append(name, value);
    box.append(card);
  });
}

function renderFinance() {
  const box = document.getElementById("panel-finance");
  box.replaceChildren();
  box.append(line("Сумма", sumText(client.amount)));
  box.append(line("Оплачено", sumText(client.paid)));
  box.append(line("Остаток", sumText(client.due)));
  box.append(line("Просрочено", sumText(client.overdue)));
  box.append(line("Потрачено", sumText(client.spent)));
  box.append(line("Прибыль", sumText(client.profit)));
  box.append(line("Мы должны", sumText(client.payable)));
  box.append(line("Деньги в объектах", sumText(client.invested)));
  box.append(line("Рентабельность", client.profitability == null ? "—" : String(client.profitability).replace(".", ",") + " %"));
  const link = document.createElement("a");
  link.href = "finance.html?tab=client&client=" + id;
  link.textContent = "Те же цифры в финансах";
  box.append(link);
}

function renderHistory() {
  renderList("panel-history", client.history, "Записей пока нет.", function (item) {
    return line(item.at || "", item.text || "");
  });
}

async function loadSource() {
  sourceLead = client.lead || null;
  if (sourceLead || !client.sourceLeadId) return;
  const response = await api("/api/leads/" + client.sourceLeadId);
  if (!response.ok) return;
  const detail = await response.json();
  sourceLead = detail && detail.lead ? detail.lead : detail;
  if (client.lead == null && detail && detail.estimate && sourceLead) sourceLead.estimate = detail.estimate;
}

async function load() {
  if (!Number.isInteger(id) || id <= 0) {
    skeleton.hidden = true;
    banner.hidden = false;
    banner.textContent = "Не указан клиент.";
    return;
  }
  try {
    const response = await api("/api/clients/" + id);
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (!response.ok) {
      skeleton.hidden = true;
      banner.hidden = false;
      banner.textContent = "Клиент не открылся.";
      return;
    }
    client = await response.json();
    await loadSource();
    skeleton.hidden = true;
    card.hidden = false;
    renderHeader();
    renderOverview();
    await loadPhotos();
    await renderData();
    const contracts = document.getElementById("panel-contracts");
    contracts.replaceChildren();
    if (!client.contracts || !client.contracts.length) {
      const empty = document.createElement("p");
      empty.textContent = "Договоров пока нет.";
      contracts.append(empty);
    } else {
      client.contracts.forEach(function (item) {
        contracts.append(contractBlock(item, function (contractId) {
          openContractUpload({ clientId: id, addendumOf: contractId, stay: true, onDone: function () { location.reload(); } });
        }));
      });
    }
    const objectsBox = document.getElementById("panel-objects");
    objectsBox.replaceChildren();
    const createObject = document.createElement("a");
    createObject.className = "btn";
    createObject.href = "objects.html?client=" + id;
    createObject.textContent = "Все объекты";
    objectsBox.append(createObject);
    let objects = client.objects || [];
    const objectsResponse = await api("/api/objects?client=" + id);
    if (objectsResponse.ok) {
      const body = await objectsResponse.json();
      objects = Array.isArray(body) ? body : (body.objects || []);
    }
    if (!objects.length) {
      const emptyObjects = document.createElement("p");
      emptyObjects.textContent = "Объектов пока нет.";
      objectsBox.append(emptyObjects);
    }
    objects.forEach(function (item) {
      const link = document.createElement("a");
      link.className = "object-tile";
      link.href = "object.html?id=" + item.id;
      const heading = document.createElement("h3");
      heading.textContent = item.address || "Объект";
      const status = document.createElement("p");
      status.textContent = objectStatus(item.status);
      link.append(heading, status);
      objectsBox.append(link);
      (item.contracts || []).forEach(function (contract) {
        objectsBox.append(contractBlock(contract));
      });
    });
    renderFinance();
    renderHistory();
    showTab(tab);
    if (params.get("created") === "1" && client.sourceLeadId) {
      toast("Клиент создан.", {
        undo: async function () {
          await api("/api/leads/" + client.sourceLeadId + "/unconvert", { method: "POST" });
          location.href = "leads.html";
        }
      });
    }
  } catch {
    skeleton.hidden = true;
    banner.hidden = false;
    banner.textContent = "Нет сети.";
  }
}

document.getElementById("upload-contract").addEventListener("click", function () {
  openContractUpload({ clientId: id, stay: true, onDone: function () { location.reload(); } });
});

document.getElementById("unconvert").addEventListener("click", async function () {
  if (!client || !client.sourceLeadId) return;
  const response = await api("/api/leads/" + client.sourceLeadId + "/unconvert", { method: "POST" });
  if (response.ok) {
    location.href = "leads.html";
    return;
  }
  banner.hidden = false;
  banner.textContent = response.status === 409
    ? "У клиента уже есть договоры, оплаты или расходы. Можно только архивировать."
    : "Вернуть заявку нельзя.";
});

document.getElementById("archive").addEventListener("click", async function () {
  const response = await api("/api/clients/" + id, {
    method: "PATCH",
    body: JSON.stringify({ status: "archived" })
  });
  if (response.ok) location.href = "clients.html?status=archived";
  else {
    banner.hidden = false;
    banner.textContent = "Не удалось архивировать.";
  }
});

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else if (access !== "out") {
  skeleton.hidden = true;
  banner.hidden = false;
  banner.textContent = access === "offline" ? "Нет сети." : "Не удалось проверить вход.";
}
