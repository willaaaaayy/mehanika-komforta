import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";

const rows = document.getElementById("rows");
const empty = document.getElementById("empty");
const tableWrap = document.getElementById("table-wrap");
const skeleton = document.getElementById("skeleton");
const banner = document.getElementById("banner");
const title = document.getElementById("title");
const query = document.getElementById("query");
const status = document.getElementById("status");
const type = document.getElementById("type");
let all = [];

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function readFilters() {
  const params = new URLSearchParams(location.search);
  query.value = params.get("q") || "";
  status.value = params.get("status") || "current";
  type.value = params.get("type") || "";
}

function writeFilters() {
  const params = new URLSearchParams();
  if (query.value.trim()) params.set("q", query.value.trim());
  if (status.value) params.set("status", status.value);
  if (type.value) params.set("type", type.value);
  const search = params.toString();
  history.replaceState(null, "", search ? "clients.html?" + search : "clients.html");
}

function visible() {
  const text = query.value.trim().toLowerCase();
  return all.filter(function (client) {
    if (status.value && client.status && client.status !== status.value) return false;
    if (type.value && client.type !== type.value) return false;
    const haystack = ((client.name || "") + " " + (client.phone || "")).toLowerCase();
    if (text && haystack.indexOf(text) === -1) return false;
    return true;
  });
}

function cell(text, label) {
  const td = document.createElement("td");
  td.dataset.label = label;
  td.textContent = text;
  return td;
}

function draw() {
  const list = visible();
  title.textContent = "Клиенты · " + list.length;
  rows.replaceChildren();
  empty.hidden = list.length !== 0;
  tableWrap.hidden = list.length === 0;
  list.forEach(function (client) {
    const tr = document.createElement("tr");
    tr.append(cell(client.name || "—", "Клиент"));
    tr.append(cell(client.contracts == null ? "—" : String(client.contracts), "Договоры"));
    tr.append(cell(sumText(client.amount), "Сумма"));
    tr.append(cell(sumText(client.paid), "Оплачено"));
    tr.append(cell(sumText(client.due), "Остаток"));
    tr.append(cell(sumText(client.overdue), "Просрочено"));
    const next = client.nextPayment;
    tr.append(cell(next ? sumText(next.amount) + (next.date ? ", " + next.date : "") : "—", "Ближайший платёж"));
    tr.addEventListener("click", function () {
      location.href = "client.html?id=" + client.id;
    });
    rows.append(tr);
  });
}

async function load() {
  skeleton.hidden = false;
  banner.hidden = true;
  const params = new URLSearchParams();
  if (status.value) params.set("status", status.value);
  if (type.value) params.set("type", type.value);
  if (query.value.trim()) params.set("q", query.value.trim());
  try {
    const response = await api("/api/clients?" + params.toString());
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (!response.ok) {
      skeleton.hidden = true;
      banner.hidden = false;
      banner.textContent = "Сервер ещё не отдаёт клиентов.";
      all = [];
      draw();
      return;
    }
    const body = await response.json();
    all = Array.isArray(body) ? body : (body.clients || []);
    skeleton.hidden = true;
    draw();
  } catch {
    skeleton.hidden = true;
    banner.hidden = false;
    banner.textContent = "Нет сети.";
  }
}

["query", "status", "type"].forEach(function (id) {
  document.getElementById(id).addEventListener("input", function () {
    writeFilters();
    load();
  });
  document.getElementById(id).addEventListener("change", function () {
    writeFilters();
    load();
  });
});

readFilters();
const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else if (access !== "out") {
  skeleton.hidden = true;
  banner.hidden = false;
  banner.textContent = access === "offline" ? "Нет сети." : "Не удалось проверить вход.";
}
