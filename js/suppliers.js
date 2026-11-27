import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";

const rows = document.getElementById("rows");
const empty = document.getElementById("empty");
const banner = document.getElementById("banner");
const unassigned = document.getElementById("unassigned");

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function cell(text) {
  const td = document.createElement("td");
  td.textContent = text;
  return td;
}

async function load() {
  const [list, loose] = await Promise.all([
    api("/api/suppliers"),
    api("/api/supplier-lines?unassigned=1")
  ]);
  if (list.status === 401) {
    location.replace("login.html");
    return;
  }
  if (!list.ok) {
    banner.hidden = false;
    banner.textContent = "Сервер ещё не отдаёт поставщиков.";
    return;
  }
  const body = await list.json();
  const suppliers = Array.isArray(body) ? body : (body.suppliers || []);
  rows.replaceChildren();
  document.getElementById("table-wrap").hidden = suppliers.length === 0;
  empty.hidden = suppliers.length !== 0;
  document.getElementById("title").textContent = "Поставщики · " + suppliers.length;
  suppliers.forEach(function (item) {
    const tr = document.createElement("tr");
    tr.append(cell(item.name || "Поставщик"));
    tr.append(cell(sumText(item.purchased)));
    tr.append(cell(sumText(item.paid)));
    tr.append(cell(sumText(item.debt)));
    tr.append(cell(item.nextDue || "—"));
    const late = document.createElement("td");
    late.textContent = sumText(item.overdue);
    if (item.overdue) late.className = "late";
    tr.append(late);
    tr.addEventListener("click", function () { location.href = "supplier.html?id=" + item.id; });
    rows.append(tr);
  });
  if (loose.ok) {
    const lines = await loose.json();
    const items = Array.isArray(lines) ? lines : (lines.lines || []);
    unassigned.hidden = items.length === 0;
    unassigned.textContent = "Нужно разнести: " + items.length;
  }
}

document.getElementById("add").addEventListener("click", function () {
  document.getElementById("create").showModal();
});
document.getElementById("create-close").addEventListener("click", function () {
  document.getElementById("create").close();
});
document.getElementById("create-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const response = await api("/api/suppliers", {
    method: "POST",
    body: JSON.stringify({
      name: document.getElementById("name").value.trim(),
      inn: document.getElementById("inn").value.trim(),
      phone: document.getElementById("phone").value.trim()
    })
  });
  if (!response.ok) {
    document.getElementById("create-status").hidden = false;
    document.getElementById("create-status").textContent = "Не удалось сохранить поставщика.";
    return;
  }
  const created = await response.json();
  location.href = "supplier.html?id=" + created.id;
});

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else {
  banner.hidden = false;
  banner.textContent = "Нет сети.";
}
