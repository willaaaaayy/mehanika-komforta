import { api, bindLogout, errorCode, requireOwner } from "./api.js";
import { ago, dayKey, digits, fileKind, fileSize, shortWhen, SOURCES, stageMeta, STAGES, telHref } from "./format.js";
import { clearErrors, fieldError, setBusy, toast } from "./ui.js";

const banner = document.getElementById("banner");
const skeleton = document.getElementById("skeleton");
const empty = document.getElementById("empty");
const none = document.getElementById("none");
const tableWrap = document.getElementById("table-wrap");
const rows = document.getElementById("rows");
const title = document.getElementById("title");
const query = document.getElementById("query");
const stage = document.getElementById("stage");
const source = document.getElementById("source");
const from = document.getElementById("from");
const to = document.getElementById("to");
const chips = document.getElementById("chips");
const createDialog = document.getElementById("create");
const pricesDialog = document.getElementById("prices");
const pricesFile = document.getElementById("prices-file");
const pricesNote = document.getElementById("prices-note");
const pricesFileNote = document.getElementById("prices-file-note");
const pricesStatus = document.getElementById("prices-status");
const pricesSave = document.getElementById("prices-save");

let all = [];
let chosen = null;

STAGES.forEach(function (item) {
  stage.append(new Option(item[1], item[0]));
});

function readFilters() {
  const params = new URLSearchParams(location.search);
  query.value = params.get("q") || "";
  stage.value = params.get("stage") || "";
  source.value = params.get("source") || "";
  from.value = params.get("from") || "";
  to.value = params.get("to") || "";
}

function writeFilters() {
  const params = new URLSearchParams();
  if (query.value.trim()) params.set("q", query.value.trim());
  if (stage.value) params.set("stage", stage.value);
  if (source.value) params.set("source", source.value);
  if (from.value) params.set("from", from.value);
  if (to.value) params.set("to", to.value);
  const search = params.toString();
  history.replaceState(null, "", search ? "leads.html?" + search : "leads.html");
}

function openedSet() {
  try {
    return new Set(JSON.parse(localStorage.getItem("mk-opened-leads") || "[]"));
  } catch {
    return new Set();
  }
}

function visible() {
  const text = query.value.trim().toLowerCase();
  return all.filter(function (lead) {
    const haystack = (lead.name + " " + lead.phone + " " + lead.objectType).toLowerCase();
    const day = dayKey(lead.createdAt);
    if (text && haystack.indexOf(text) === -1) return false;
    if (stage.value && lead.stage !== stage.value) return false;
    if (source.value && lead.source !== source.value) return false;
    if (from.value && day < from.value) return false;
    if (to.value && day > to.value) return false;
    return true;
  }).sort(function (a, b) {
    return new Date(b.createdAt) - new Date(a.createdAt);
  });
}

function cell(text, label, className) {
  const td = document.createElement("td");
  td.dataset.label = label;
  if (className) td.className = className;
  td.textContent = text;
  return td;
}

function draw() {
  const list = visible();
  const opened = openedSet();
  title.textContent = "Заявки · " + all.length;
  rows.replaceChildren();
  empty.hidden = all.length !== 0;
  none.hidden = all.length === 0 || list.length !== 0;
  tableWrap.hidden = list.length === 0;
  list.forEach(function (lead) {
    const meta = stageMeta(lead.stage);
    const tr = document.createElement("tr");
    const fresh = lead.stage === "new" && !opened.has(lead.id);
    if (fresh) tr.classList.add("is-fresh");
    tr.append(cell(SOURCES[lead.source] || lead.source || "—", "Источник"));
    const date = cell("", "Дата");
    date.append(document.createTextNode(shortWhen(lead.createdAt)));
    const relative = ago(lead.createdAt);
    if (relative) {
      const sub = document.createElement("span");
      sub.className = "sub";
      sub.textContent = relative;
      date.append(sub);
    }
    tr.append(date);
    const name = cell(lead.name, "Имя", "cell-name");
    if (fresh) {
      const sub = document.createElement("span");
      sub.className = "sub";
      sub.textContent = "не открыта";
      name.append(sub);
    }
    tr.append(name);
    const phone = document.createElement("td");
    phone.dataset.label = "Телефон";
    const link = document.createElement("a");
    link.className = "call";
    link.href = telHref(lead.phone);
    link.textContent = lead.phone;
    phone.append(link);
    tr.append(phone);
    tr.append(cell(lead.objectType, "Тип объекта"));
    const stageCell = document.createElement("td");
    stageCell.dataset.label = "Этап";
    const pill = document.createElement("span");
    pill.className = "status";
    pill.dataset.tone = meta[2];
    pill.textContent = meta[1];
    stageCell.append(pill);
    tr.append(stageCell);
    const actions = document.createElement("td");
    actions.dataset.label = "Действия";
    actions.className = "cell-actions";
    const open = document.createElement("button");
    open.type = "button";
    open.className = "btn btn-text";
    open.textContent = "Открыть";
    open.addEventListener("click", function () { openLead(lead.id); });
    actions.append(open);
    tr.append(actions);
    tr.addEventListener("click", function (event) {
      if (event.target.closest("a, button")) return;
      openLead(lead.id);
    });
    rows.append(tr);
  });
  drawChips();
}

function drawChips() {
  chips.replaceChildren();
  const items = [];
  if (query.value.trim()) items.push(["q", "Поиск: " + query.value.trim()]);
  if (stage.value) items.push(["stage", stageMeta(stage.value)[1]]);
  if (source.value) items.push(["source", SOURCES[source.value] || source.value]);
  if (from.value) items.push(["from", "С " + from.value]);
  if (to.value) items.push(["to", "По " + to.value]);
  items.forEach(function (item) {
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.append(document.createTextNode(item[1]));
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "×";
    button.setAttribute("aria-label", "Снять фильтр " + item[1]);
    button.addEventListener("click", function () {
      if (item[0] === "q") query.value = "";
      if (item[0] === "stage") stage.value = "";
      if (item[0] === "source") source.value = "";
      if (item[0] === "from") from.value = "";
      if (item[0] === "to") to.value = "";
      writeFilters();
      draw();
    });
    chip.append(button);
    chips.append(chip);
  });
  if (items.length) {
    const reset = document.createElement("button");
    reset.type = "button";
    reset.className = "btn btn-text";
    reset.textContent = "Сбросить всё";
    reset.addEventListener("click", resetFilters);
    chips.append(reset);
  }
}

function resetFilters() {
  query.value = "";
  stage.value = "";
  source.value = "";
  from.value = "";
  to.value = "";
  writeFilters();
  draw();
}

function openLead(id) {
  const opened = openedSet();
  opened.add(String(id));
  opened.add(id);
  localStorage.setItem("mk-opened-leads", JSON.stringify([...opened]));
  sessionStorage.setItem("mk-list-scroll", String(window.scrollY));
  const search = location.search || "";
  sessionStorage.setItem("mk-list-query", search);
  location.href = "lead.html?id=" + encodeURIComponent(id);
}

function showBanner(message) {
  banner.hidden = !message;
  banner.textContent = message || "";
  if (message) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn";
    button.textContent = "Повторить";
    button.addEventListener("click", load);
    banner.append(button);
  }
}

async function load() {
  skeleton.hidden = false;
  showBanner("");
  try {
    const response = await api("/api/leads");
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (!response.ok) {
      skeleton.hidden = true;
      showBanner("Не удалось загрузить заявки.");
      return;
    }
    all = await response.json();
    skeleton.hidden = true;
    draw();
    const scroll = Number(sessionStorage.getItem("mk-list-scroll"));
    if (scroll) window.scrollTo(0, scroll);
    sessionStorage.removeItem("mk-list-scroll");
  } catch {
    skeleton.hidden = true;
    showBanner("Нет сети. Список не обновился.");
  }
}

async function loadPrices() {
  const box = document.getElementById("prices-banner");
  try {
    const response = await api("/api/prices");
    if (!response.ok) return;
    const prices = await response.json();
    if (prices.teaching) {
      box.hidden = false;
      box.textContent = prices.note ? "Учебный прайс. " + prices.note : "Учебный прайс.";
      pricesNote.textContent = prices.note || "В файле учебная пометка. Это не прайс компании.";
    } else {
      box.hidden = true;
      pricesNote.textContent = "Прайс компании. Бот считает по нему. Можно заменить файлом PDF или Excel.";
    }
  } catch {
    pricesNote.textContent = "Загрузите прайс файлом PDF или Excel.";
  }
}

function validName(value) {
  const text = value.trim();
  return text.length >= 2 && text.length <= 80 ? "" : "Имя — от 2 до 80 символов.";
}

function validPhone(value) {
  if (value.trim().length > 40) return "Телефон длиннее 40 символов.";
  const count = digits(value).length;
  return count >= 10 && count <= 15 ? "" : "Нужен телефон из 10–15 цифр.";
}

function validObject(value) {
  const text = value.trim();
  return text.length >= 2 && text.length <= 80 ? "" : "Укажите тип объекта, от 2 до 80 символов.";
}

function openCreate() {
  createDialog.showModal();
  document.getElementById("manual-name").focus();
}

document.getElementById("create-open").addEventListener("click", openCreate);
document.getElementById("empty-create").addEventListener("click", openCreate);
document.getElementById("create-close").addEventListener("click", function () {
  createDialog.close();
});
document.getElementById("reset").addEventListener("click", resetFilters);
document.getElementById("refresh").addEventListener("click", function () {
  load();
  loadPrices();
});

["query", "stage", "source", "from", "to"].forEach(function (id) {
  document.getElementById(id).addEventListener("input", function () {
    writeFilters();
    draw();
  });
  document.getElementById(id).addEventListener("change", function () {
    writeFilters();
    draw();
  });
});

document.getElementById("manual").addEventListener("submit", async function (event) {
  event.preventDefault();
  const form = event.currentTarget;
  clearErrors(form);
  const name = document.getElementById("manual-name");
  const phone = document.getElementById("manual-phone");
  const objectType = document.getElementById("manual-object");
  fieldError(name, validName(name.value));
  fieldError(phone, validPhone(phone.value));
  fieldError(objectType, validObject(objectType.value));
  const first = [name, phone, objectType].find(function (input) {
    return input.classList.contains("is-invalid");
  });
  if (first) {
    first.focus();
    return;
  }
  const button = form.querySelector("button[type=submit]");
  setBusy(button, true, "Добавляем…");
  try {
    const response = await api("/api/leads", {
      method: "POST",
      body: JSON.stringify({
        name: name.value.trim(),
        phone: phone.value.trim(),
        objectType: objectType.value.trim(),
        source: "manual"
      })
    });
    if (response.status === 201) {
      form.reset();
      createDialog.close();
      toast("Заявку добавили.");
      await load();
      return;
    }
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    const code = await errorCode(response);
    if (code === "phone") fieldError(phone, "Нужен телефон из 10–15 цифр.");
    else if (code === "name") fieldError(name, "Проверьте имя.");
    else if (code === "objectType") fieldError(objectType, "Проверьте тип объекта.");
    else fieldError(name, "Проверьте поля.");
  } catch {
    fieldError(name, "Не удалось записать заявку.");
  } finally {
    setBusy(button, false);
  }
});

function setFile(file) {
  const kind = file ? fileKind(file) : "";
  chosen = kind ? file : null;
  pricesSave.disabled = !chosen;
  pricesStatus.hidden = true;
  if (!file) {
    pricesFileNote.textContent = "";
    return;
  }
  if (!kind) {
    pricesFileNote.textContent = "Нужен PDF или Excel. JSON тоже можно, если это текущий файл прайса.";
    return;
  }
  const label = kind === "pdf" ? "PDF" : kind === "excel" ? "Excel" : "JSON";
  pricesFileNote.textContent = label + " · " + file.name + " · " + fileSize(file.size);
}

function pick(accept) {
  pricesFile.accept = accept;
  pricesFile.value = "";
  pricesFile.click();
}

document.getElementById("pick-pdf").addEventListener("click", function () {
  pick(".pdf,application/pdf");
});
document.getElementById("pick-excel").addEventListener("click", function () {
  pick(".xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
});
document.getElementById("pick-json").addEventListener("click", function () {
  pick(".json,application/json");
});
pricesFile.addEventListener("change", function () {
  setFile(pricesFile.files[0] || null);
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
  setFile(event.dataTransfer.files[0] || null);
});

document.getElementById("prices-open").addEventListener("click", function () {
  pricesStatus.hidden = true;
  pricesDialog.showModal();
});
document.getElementById("prices-close").addEventListener("click", function () {
  pricesDialog.close();
});

pricesSave.addEventListener("click", async function () {
  if (!chosen) return;
  const kind = fileKind(chosen);
  pricesStatus.hidden = true;
  setBusy(pricesSave, true, "Загружаем…");
  try {
    let response;
    if (kind === "json") {
      const text = await chosen.text();
      try {
        JSON.parse(text);
      } catch {
        pricesStatus.hidden = false;
        pricesStatus.textContent = "Файл не разобрался как JSON.";
        return;
      }
      response = await api("/api/prices", { method: "PUT", body: text });
    } else {
      const form = new FormData();
      form.append("file", chosen, chosen.name);
      response = await api("/api/prices", { method: "POST", body: form });
    }
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (response.status === 200 || response.status === 201 || response.status === 204) {
      pricesStatus.hidden = false;
      pricesStatus.textContent = "Прайс записан. Бот прочитает его при следующем расчёте.";
      chosen = null;
      pricesSave.disabled = true;
      pricesFileNote.textContent = "";
      await loadPrices();
      return;
    }
    pricesStatus.hidden = false;
    if (kind !== "json" && [404, 405, 415, 501].includes(response.status)) {
      pricesStatus.textContent = "Сервер пока не принимает PDF и Excel. Файл не записан.";
      return;
    }
    pricesStatus.textContent = "Файл не подошёл. Нужен тот же формат, что у текущего прайса.";
  } catch {
    pricesStatus.hidden = false;
    pricesStatus.textContent = "Не удалось загрузить файл.";
  } finally {
    setBusy(pricesSave, false);
    pricesSave.disabled = !chosen;
  }
});

document.addEventListener("keydown", function (event) {
  const tag = document.activeElement && document.activeElement.tagName;
  const typing = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";
  if (typing || event.metaKey || event.ctrlKey || event.altKey) return;
  if (event.key === "n" || event.key === "N" || event.key === "т" || event.key === "Т") {
    event.preventDefault();
    openCreate();
  }
  if (event.key === "f" || event.key === "F" || event.key === "а" || event.key === "А") {
    event.preventDefault();
    query.focus();
  }
});

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  readFilters();
  await Promise.all([load(), loadPrices()]);
} else if (access === "offline" || access === "error") {
  skeleton.hidden = true;
  showBanner(access === "offline" ? "Нет сети." : "Не удалось проверить вход.");
}
