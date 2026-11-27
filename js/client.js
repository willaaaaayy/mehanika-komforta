import { api, bindLogout, requireOwner } from "./api.js";
import { money, telHref } from "./format.js";
import { toast } from "./ui.js";
import { contractBlock, openContractUpload } from "./contract-upload.js";

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

function renderData() {
  const box = document.getElementById("panel-data");
  box.replaceChildren();
  const form = document.createElement("form");
  const fields = [
    ["name", "Имя", client.name],
    ["phone", "Телефон", client.phone],
    ["email", "Почта", client.email],
    ["inn", "ИНН", client.inn],
    ["address", "Адрес", client.address]
  ];
  const inputs = {};
  fields.forEach(function (item) {
    const wrap = document.createElement("div");
    wrap.className = "field";
    const label = document.createElement("label");
    label.textContent = item[1];
    const input = document.createElement("input");
    input.value = item[2] || "";
    inputs[item[0]] = input;
    wrap.append(label, input);
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
    const body = {
      name: inputs.name.value.trim(),
      phone: inputs.phone.value.trim(),
      email: inputs.email.value.trim(),
      inn: inputs.inn.value.trim(),
      address: inputs.address.value.trim(),
      type: type.value
    };
    const response = await api("/api/clients/" + id, { method: "PATCH", body: JSON.stringify(body) });
    if (response.ok) {
      client = Object.assign(client, body);
      document.getElementById("card-title").textContent = body.name || "Клиент";
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

function renderFinance() {
  const box = document.getElementById("panel-finance");
  box.replaceChildren();
  box.append(line("Сумма", sumText(client.amount)));
  box.append(line("Оплачено", sumText(client.paid)));
  box.append(line("Остаток", sumText(client.due)));
  box.append(line("Просрочено", sumText(client.overdue)));
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
    document.getElementById("card-title").textContent = client.name || "Клиент";
    document.getElementById("card-status").textContent = client.status || "";
    renderOverview();
    await loadPhotos();
    renderData();
    const contracts = document.getElementById("panel-contracts");
    contracts.replaceChildren();
    const upload = document.createElement("button");
    upload.type = "button";
    upload.className = "btn btn-primary";
    upload.textContent = "Загрузить договор";
    upload.addEventListener("click", function () {
      openContractUpload({ clientId: id, stay: true, onDone: function () { location.reload(); } });
    });
    contracts.append(upload);
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
    renderList("panel-objects", client.objects, "Объектов пока нет.", function (item) {
      return line(item.address || "Объект", item.status || "");
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
