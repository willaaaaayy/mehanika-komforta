import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";
import { setBusy } from "./ui.js";

const params = new URLSearchParams(location.search);
const tab = params.get("tab") || "";
const banner = document.getElementById("banner");
const skeleton = document.getElementById("skeleton");
const overview = document.getElementById("overview");
const customers = document.getElementById("customers");
const period = document.getElementById("period");
const from = document.getElementById("from");
const to = document.getElementById("to");
const client = document.getElementById("client");
const objectSelect = document.getElementById("object");
const mode = document.getElementById("mode");
const upload = document.getElementById("upload");
const review = document.getElementById("review");
const create = document.getElementById("create");
const uploadStatus = document.getElementById("upload-status");
const scheduleBox = document.getElementById("schedule");
const fileInput = document.getElementById("file");

let report = null;
let draftId = null;
let payTarget = null;
const limit = 20 * 1024 * 1024;

const METRICS = [
  ["receivable", "Нам должны", "clients"],
  ["payable", "Мы должны", ""],
  ["spent", "Потрачено", ""],
  ["received", "Получено", "clients"],
  ["profit", "Прибыль", ""],
  ["invested", "Вложено в объекты", ""]
];

function todayKey() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Saratov" }).format(new Date());
}

function applyPeriod(name) {
  const today = todayKey();
  const year = Number(today.slice(0, 4));
  const month = Number(today.slice(5, 7));
  if (name === "month") {
    from.value = today.slice(0, 8) + "01";
    to.value = today;
  } else if (name === "quarter") {
    const startMonth = Math.floor((month - 1) / 3) * 3 + 1;
    from.value = year + "-" + String(startMonth).padStart(2, "0") + "-01";
    to.value = today;
  } else if (name === "year") {
    from.value = year + "-01-01";
    to.value = today;
  }
  from.disabled = name !== "custom";
  to.disabled = name !== "custom";
}

function readFilters() {
  period.value = params.get("period") || "month";
  mode.value = params.get("mode") === "accrual" ? "accrual" : "cash";
  if (period.value === "custom") {
    from.value = params.get("from") || "";
    to.value = params.get("to") || "";
    from.disabled = false;
    to.disabled = false;
  } else {
    applyPeriod(period.value);
    if (params.get("from")) from.value = params.get("from");
    if (params.get("to")) to.value = params.get("to");
  }
  client.value = params.get("client") || "";
  objectSelect.value = params.get("object") || "";
}

function writeFilters() {
  const next = new URLSearchParams();
  if (tab) next.set("tab", tab);
  if (tab === "client" && params.get("client")) next.set("client", params.get("client"));
  next.set("period", period.value);
  if (from.value) next.set("from", from.value);
  if (to.value) next.set("to", to.value);
  if (client.value && tab !== "client") next.set("client", client.value);
  if (objectSelect.value) next.set("object", objectSelect.value);
  if (mode.value !== "cash") next.set("mode", mode.value);
  history.replaceState(null, "", "finance.html?" + next.toString());
}

function queryString() {
  const next = new URLSearchParams();
  if (from.value) next.set("from", from.value);
  if (to.value) next.set("to", to.value);
  const clientId = tab === "client" ? params.get("client") : client.value;
  if (clientId) next.set("client", clientId);
  if (objectSelect.value) next.set("object", objectSelect.value);
  next.set("mode", mode.value || "cash");
  return next.toString();
}

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function showBanner(message) {
  banner.hidden = !message;
  banner.textContent = message || "";
}

function fillChoices() {
  const clients = report && Array.isArray(report.clients) ? report.clients : [];
  const selectedClient = client.value;
  const selectedObject = objectSelect.value;
  client.replaceChildren(new Option("Все", ""));
  objectSelect.replaceChildren(new Option("Все", ""));
  const objects = new Set();
  clients.forEach(function (item) {
    client.append(new Option(item.name || "Клиент " + item.id, String(item.id)));
    (item.items || []).forEach(function (contract) {
      if (contract.object) objects.add(contract.object);
    });
  });
  objects.forEach(function (name) { objectSelect.append(new Option(name, name)); });
  if ([...client.options].some(function (option) { return option.value === selectedClient; })) client.value = selectedClient;
  if ([...objectSelect.options].some(function (option) { return option.value === selectedObject; })) objectSelect.value = selectedObject;
}

function renderMetrics() {
  const totals = report && report.totals ? report.totals : {};
  const box = document.getElementById("metrics");
  box.replaceChildren();
  METRICS.forEach(function (item) {
    const card = document.createElement(item[2] ? "a" : "div");
    card.className = "metric";
    if (item[2]) {
      const link = new URLSearchParams(location.search);
      link.set("tab", item[2]);
      card.href = "finance.html?" + link.toString();
    }
    const name = document.createElement("span");
    name.textContent = item[1];
    const value = document.createElement("strong");
    value.className = "num";
    value.textContent = sumText(totals[item[0]]);
    card.append(name, value);
    box.append(card);
  });
}

function renderAttention() {
  const box = document.getElementById("attention");
  const items = report && Array.isArray(report.attention) ? report.attention : [];
  box.replaceChildren();
  if (!items.length) {
    box.textContent = "Нет просроченных оплат.";
    return;
  }
  const list = document.createElement("ul");
  list.className = "attention";
  items.forEach(function (item) {
    const row = document.createElement("li");
    const link = document.createElement("a");
    link.href = "finance.html?tab=client&client=" + encodeURIComponent(item.clientId || "");
    link.textContent = item.title || "Просроченная оплата";
    row.append(link, document.createTextNode(" · " + sumText(item.amount)));
    if (item.days) {
      const late = document.createElement("span");
      late.className = "late";
      late.textContent = " просрочено на " + item.days + " дн.";
      row.append(late);
    }
    list.append(row);
  });
  box.append(list);
}

function renderWeeks() {
  const box = document.getElementById("weeks");
  const weeks = report && Array.isArray(report.weeks) ? report.weeks : [];
  box.replaceChildren();
  const withMoney = weeks.filter(function (item) { return item && item.in != null; });
  if (!withMoney.length) {
    box.textContent = "Загрузите договор или внесите первый расход.";
    return;
  }
  const max = Math.max.apply(null, withMoney.map(function (item) { return Number(item.in) || 0; })) || 1;
  withMoney.forEach(function (item) {
    const row = document.createElement("div");
    row.className = "week";
    const label = document.createElement("span");
    label.textContent = item.week || "";
    const bar = document.createElement("span");
    bar.className = "bar";
    bar.style.width = Math.round(((Number(item.in) || 0) / max) * 100) + "%";
    const sum = document.createElement("span");
    sum.className = "num";
    sum.textContent = sumText(item.in);
    row.append(label, bar, sum);
    box.append(row);
  });
}

function scheduleLine(row) {
  const item = document.createElement("li");
  const title = document.createElement("span");
  title.textContent = (row.title || "Платёж") + " · " + sumText(row.amount);
  item.append(title);
  if (row.due) item.append(document.createTextNode(" · " + row.due));
  if (row.status === "overdue" || row.overdueDays) {
    const late = document.createElement("span");
    late.className = "late";
    late.textContent = row.overdueDays ? " просрочено на " + row.overdueDays + " дн." : " просрочено";
    item.append(late);
  } else if (row.status === "paid") {
    item.append(document.createTextNode(" · оплачено"));
  }
  if (row.id && row.status !== "paid") {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-text";
    button.textContent = "Отметить оплату";
    button.addEventListener("click", function () {
      payTarget = row;
      document.getElementById("pay-amount").value = row.amount == null ? "" : String(row.amount);
      document.getElementById("pay-date").value = todayKey();
      document.getElementById("pay-status").hidden = true;
      document.getElementById("pay").showModal();
    });
    item.append(button);
  }
  return item;
}

function renderClients() {
  const box = document.getElementById("client-list");
  const empty = document.getElementById("empty");
  const people = report && Array.isArray(report.clients) ? report.clients : [];
  box.replaceChildren();
  empty.hidden = people.length !== 0;
  const title = document.getElementById("title");
  if (tab === "client") {
    title.textContent = people[0] && people[0].name ? people[0].name : "Клиент";
  } else {
    title.textContent = "Финансы";
  }
  people.forEach(function (person) {
    const article = document.createElement("article");
    article.className = "panel";
    const head = document.createElement("h2");
    if (tab === "client") head.textContent = person.name || "Клиент";
    else {
      const link = document.createElement("a");
      link.href = "finance.html?tab=client&client=" + encodeURIComponent(person.id);
      link.textContent = person.name || "Клиент";
      head.append(link);
    }
    article.append(head);
    const summary = document.createElement("p");
    summary.className = "num";
    summary.textContent = "Договоров " + (person.contracts == null ? "—" : person.contracts)
      + " · сумма " + sumText(person.amount)
      + " · выставлено " + sumText(person.invoiced)
      + " · оплачено " + sumText(person.paid)
      + " · остаток " + sumText(person.due)
      + " · просрочено " + sumText(person.overdue);
    article.append(summary);
    if (person.nextPayment) {
      const next = document.createElement("p");
      next.textContent = "Ближайший платёж: " + sumText(person.nextPayment.amount)
        + (person.nextPayment.date ? ", " + person.nextPayment.date : "");
      article.append(next);
    }
    (person.items || []).forEach(function (contract) {
      const block = document.createElement("div");
      const name = document.createElement("h3");
      name.textContent = (contract.number ? "Договор " + contract.number : "Договор")
        + (contract.object ? " · " + contract.object : "");
      block.append(name);
      const list = document.createElement("ul");
      list.className = "schedule";
      (contract.schedule || []).forEach(function (row) {
        row.contractId = contract.contractId;
        list.append(scheduleLine(row));
      });
      block.append(list);
      article.append(block);
    });
    box.append(article);
  });
}

function showTab() {
  const clientView = tab === "client" || tab === "clients";
  overview.hidden = clientView;
  customers.hidden = !clientView;
  document.querySelectorAll("#tabs a").forEach(function (link) {
    const next = new URLSearchParams();
    const name = link.dataset.tab || "";
    if (name) next.set("tab", name);
    if (period.value) next.set("period", period.value);
    if (from.value) next.set("from", from.value);
    if (to.value) next.set("to", to.value);
    if (name !== "client" && client.value) next.set("client", client.value);
    if (objectSelect.value) next.set("object", objectSelect.value);
    if (mode.value && mode.value !== "cash") next.set("mode", mode.value);
    link.href = "finance.html" + (next.toString() ? "?" + next.toString() : "");
    const current = name === (tab === "client" ? "clients" : tab);
    if (current) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
}

async function load() {
  skeleton.hidden = false;
  showBanner("");
  try {
    const response = await api("/api/finance?" + queryString());
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (response.status === 404 || response.status === 405) {
      report = { totals: {}, clients: [], attention: [], weeks: [] };
      skeleton.hidden = true;
      showBanner("Сервер ещё не отдаёт финансы.");
      fillChoices();
      renderMetrics();
      renderAttention();
      renderWeeks();
      renderClients();
      showTab();
      return;
    }
    if (!response.ok) {
      skeleton.hidden = true;
      showBanner("Не удалось загрузить финансы.");
      return;
    }
    report = await response.json();
    skeleton.hidden = true;
    fillChoices();
    renderMetrics();
    renderAttention();
    renderWeeks();
    renderClients();
    showTab();
  } catch {
    skeleton.hidden = true;
    showBanner("Нет сети.");
  }
}

function numberOrNull(value) {
  const text = String(value || "").trim().replace(/\s/g, "").replace(",", ".");
  if (!text) return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
}

function addScheduleRow(row) {
  const line = document.createElement("div");
  line.className = "schedule-edit";
  const title = document.createElement("input");
  title.placeholder = "Название";
  title.value = row && row.title ? row.title : "";
  const amount = document.createElement("input");
  amount.inputMode = "decimal";
  amount.placeholder = "Сумма";
  amount.value = row && row.amount != null ? String(row.amount) : "";
  const due = document.createElement("input");
  due.type = "date";
  due.value = row && row.due ? row.due : "";
  const kind = document.createElement("select");
  [["advance", "Аванс"], ["stage", "Этап"], ["final", "Окончательный"]].forEach(function (pair) {
    kind.append(new Option(pair[1], pair[0]));
  });
  if (row && row.kind) kind.value = row.kind;
  line.append(title, amount, due, kind);
  line.dataset.kind = "row";
  scheduleBox.append(line);
}

function collectSchedule() {
  return [...scheduleBox.querySelectorAll(".schedule-edit")].map(function (line) {
    const fields = line.querySelectorAll("input, select");
    return {
      title: fields[0].value.trim(),
      amount: numberOrNull(fields[1].value),
      due: fields[2].value || null,
      kind: fields[3].value
    };
  });
}

function fillReview(draft) {
  draftId = draft && draft.id ? draft.id : null;
  review.hidden = false;
  create.disabled = false;
  document.getElementById("number").value = draft && draft.number ? draft.number : "";
  document.getElementById("amount").value = draft && draft.amount != null ? String(draft.amount) : "";
  document.getElementById("client-name").value = draft && draft.client && draft.client.name ? draft.client.name : "";
  document.getElementById("client-inn").value = draft && draft.client && draft.client.inn ? draft.client.inn : "";
  document.getElementById("object-address").value = draft && draft.object && draft.object.address ? draft.object.address : "";
  scheduleBox.replaceChildren();
  const rows = draft && Array.isArray(draft.schedule) && draft.schedule.length ? draft.schedule : [{ title: "", amount: null, due: null, kind: "advance" }];
  rows.forEach(addScheduleRow);
}

function openUpload() {
  uploadStatus.hidden = true;
  review.hidden = true;
  create.disabled = true;
  draftId = null;
  fileInput.value = "";
  document.getElementById("file-note").textContent = "";
  upload.showModal();
}

async function sendFile(file) {
  uploadStatus.hidden = true;
  const name = file.name.toLowerCase();
  const type = (file.type || "").toLowerCase();
  const pdf = name.endsWith(".pdf") || type === "application/pdf";
  const docx = name.endsWith(".docx") || type === "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  if (!pdf && !docx) {
    uploadStatus.hidden = false;
    uploadStatus.textContent = "Нужен договор в формате PDF или DOCX.";
    return;
  }
  if (file.size > limit) {
    uploadStatus.hidden = false;
    uploadStatus.textContent = "Файл больше 20 МБ.";
    return;
  }
  document.getElementById("file-note").textContent = file.name;
  setBusy(create, true, "Читаем документ…");
  try {
    const form = new FormData();
    form.append("file", file, file.name);
    const response = await api("/api/contracts", { method: "POST", body: form });
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (response.status === 409) {
      uploadStatus.hidden = false;
      uploadStatus.textContent = "Такой договор уже загружен.";
      return;
    }
    if (response.status === 400) {
      uploadStatus.hidden = false;
      uploadStatus.textContent = "Документ не читается. Введите сумму, клиента, объект и график вручную.";
      fillReview(null);
      return;
    }
    if (response.status === 404 || response.status === 405 || response.status === 415) {
      uploadStatus.hidden = false;
      uploadStatus.textContent = "Сервер пока не принимает договор. Файл не записан.";
      return;
    }
    if (response.status !== 200 && response.status !== 201) {
      uploadStatus.hidden = false;
      uploadStatus.textContent = "Не удалось прочитать договор.";
      return;
    }
    fillReview(await response.json());
  } catch {
    uploadStatus.hidden = false;
    uploadStatus.textContent = "Не удалось загрузить файл.";
  } finally {
    setBusy(create, false);
    create.disabled = review.hidden;
  }
}

document.getElementById("upload-open").addEventListener("click", openUpload);
document.getElementById("empty-upload").addEventListener("click", openUpload);
document.getElementById("upload-close").addEventListener("click", function () { upload.close(); });
function pick(accept) {
  fileInput.accept = accept;
  fileInput.value = "";
  fileInput.click();
}
document.getElementById("pick-pdf").addEventListener("click", function () {
  pick(".pdf,application/pdf");
});
document.getElementById("pick-docx").addEventListener("click", function () {
  pick(".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document");
});
fileInput.addEventListener("change", function () {
  if (fileInput.files[0]) sendFile(fileInput.files[0]);
});
const drop = document.getElementById("drop");
["dragenter", "dragover"].forEach(function (name) {
  drop.addEventListener(name, function (event) {
    event.preventDefault();
    drop.classList.add("is-over");
  });
});
["dragleave", "drop"].forEach(function (name) {
  drop.addEventListener(name, function (event) {
    event.preventDefault();
    drop.classList.remove("is-over");
  });
});
drop.addEventListener("drop", function (event) {
  const file = event.dataTransfer.files[0];
  if (file) sendFile(file);
});
document.getElementById("add-row").addEventListener("click", function () { addScheduleRow(null); });

document.getElementById("upload-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const body = {
    number: document.getElementById("number").value.trim(),
    amount: numberOrNull(document.getElementById("amount").value),
    client: {
      name: document.getElementById("client-name").value.trim(),
      inn: document.getElementById("client-inn").value.trim()
    },
    object: { address: document.getElementById("object-address").value.trim() },
    schedule: collectSchedule()
  };
  setBusy(create, true, "Создаём…");
  try {
    const response = draftId
      ? await api("/api/contracts/" + draftId, { method: "PATCH", body: JSON.stringify(body) })
      : await api("/api/contracts", { method: "POST", body: JSON.stringify(body) });
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (response.status === 409) {
      uploadStatus.hidden = false;
      uploadStatus.textContent = "Такой договор уже загружен.";
      return;
    }
    if (!response.ok) {
      uploadStatus.hidden = false;
      uploadStatus.textContent = "Не удалось записать договор.";
      return;
    }
    upload.close();
    await load();
  } catch {
    uploadStatus.hidden = false;
    uploadStatus.textContent = "Не удалось записать договор.";
  } finally {
    setBusy(create, false);
  }
});

document.getElementById("pay-close").addEventListener("click", function () { document.getElementById("pay").close(); });
document.getElementById("pay-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  if (!payTarget) return;
  const status = document.getElementById("pay-status");
  status.hidden = true;
  const body = {
    scheduleId: payTarget.id,
    amount: numberOrNull(document.getElementById("pay-amount").value),
    date: document.getElementById("pay-date").value,
    method: document.getElementById("pay-method").value
  };
  try {
    const response = await api("/api/contracts/" + payTarget.contractId + "/payments", {
      method: "POST",
      body: JSON.stringify(body)
    });
    if (!response.ok) {
      status.hidden = false;
      status.textContent = "Не удалось отметить оплату.";
      return;
    }
    document.getElementById("pay").close();
    await load();
  } catch {
    status.hidden = false;
    status.textContent = "Не удалось отметить оплату.";
  }
});

["period", "from", "to", "client", "object", "mode"].forEach(function (id) {
  document.getElementById(id).addEventListener("change", function () {
    if (id === "period") applyPeriod(period.value);
    writeFilters();
    load();
  });
});
document.getElementById("refresh").addEventListener("click", load);

readFilters();
showTab();
const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else if (access !== "out") {
  skeleton.hidden = true;
  showBanner(access === "offline" ? "Нет сети." : "Не удалось проверить вход.");
}
