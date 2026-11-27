import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";

const from = document.getElementById("from");
const to = document.getElementById("to");
const banner = document.getElementById("banner");
const employeesBox = document.getElementById("employees");
const logsBox = document.getElementById("logs");
const payrollBox = document.getElementById("payroll");
const logEmployee = document.getElementById("log-employee");
let employees = [];
let editingEmployee = null;
let editingLog = null;
let payingId = null;

const TYPES = { piecework: "Сдельная", daily: "Дневная", office: "Офис" };

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function todayKey() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Saratov" }).format(new Date());
}

function periodQuery() {
  const params = new URLSearchParams();
  if (from.value) params.set("from", from.value);
  if (to.value) params.set("to", to.value);
  return params.toString();
}

function setExports() {
  const query = periodQuery();
  document.getElementById("export-xlsx").href = "/api/payroll/export?format=xlsx" + (query ? "&" + query : "");
  document.getElementById("export-pdf").href = "/api/payroll/export?format=pdf" + (query ? "&" + query : "");
}

function fillEmployees(selected) {
  logEmployee.replaceChildren();
  employees.forEach(function (person) {
    logEmployee.append(new Option(person.name || "Сотрудник", String(person.id)));
  });
  if (selected) logEmployee.value = String(selected);
}

async function load() {
  banner.hidden = true;
  setExports();
  const query = periodQuery();
  const [people, logs, payroll] = await Promise.all([
    api("/api/employees"),
    api("/api/worklogs" + (query ? "?" + query : "")),
    api("/api/payroll" + (query ? "?" + query : ""))
  ]);
  if (people.status === 401 || logs.status === 401 || payroll.status === 401) {
    location.replace("login.html");
    return;
  }
  if (!people.ok) {
    banner.hidden = false;
    banner.textContent = "Сервер ещё не отдаёт зарплаты.";
    return;
  }
  employees = await people.json();
  if (!Array.isArray(employees)) employees = employees.employees || [];
  fillEmployees();
  employeesBox.replaceChildren();
  employees.forEach(function (person) {
    const row = document.createElement("p");
    row.textContent = (person.name || "Сотрудник") + " · " + (TYPES[person.payType] || person.payType || "—")
      + " · ставка " + sumText(person.rate) + " ";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-text";
    button.textContent = "Изменить";
    button.addEventListener("click", function () { openEmployee(person); });
    row.append(button);
    employeesBox.append(row);
  });
  const logRows = logs.ok ? await logs.json() : [];
  const list = Array.isArray(logRows) ? logRows : (logRows.worklogs || []);
  logsBox.replaceChildren();
  if (!list.length) logsBox.textContent = "Выработки за период нет.";
  list.forEach(function (item) {
    const row = document.createElement("p");
    row.textContent = (item.employee || item.employeeName || "") + " · " + (item.date || "")
      + (item.object ? " · " + item.object : "")
      + (item.workId ? " · " + item.workId : "")
      + (item.quantity == null ? "" : " · " + item.quantity) + " ";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-text";
    button.textContent = "Изменить";
    button.addEventListener("click", function () { openLog(item); });
    row.append(button);
    logsBox.append(row);
  });
  const sheet = payroll.ok ? await payroll.json() : { rows: [] };
  const rows = Array.isArray(sheet) ? sheet : (sheet.rows || []);
  payrollBox.replaceChildren();
  if (!rows.length) payrollBox.textContent = "Начислений за период нет.";
  rows.forEach(function (item) {
    const row = document.createElement("p");
    row.textContent = (item.employee || "Сотрудник")
      + " · начислено " + sumText(item.accrued)
      + " · аванс " + sumText(item.advance)
      + " · выплачено " + sumText(item.paid)
      + " · к выплате " + sumText(item.due) + " ";
    if (item.id) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "btn btn-text";
      button.textContent = "Выплатить";
      button.addEventListener("click", function () {
        payingId = item.id;
        document.getElementById("pay-amount").value = item.due == null ? "" : String(item.due);
        document.getElementById("pay-date").value = todayKey();
        document.getElementById("pay-status").hidden = true;
        document.getElementById("pay").showModal();
      });
      row.append(button);
    }
    payrollBox.append(row);
  });
}

function openEmployee(person) {
  editingEmployee = person && person.id ? person.id : null;
  document.getElementById("employee-name").value = person && person.name ? person.name : "";
  document.getElementById("employee-type").value = person && person.payType ? person.payType : "piecework";
  document.getElementById("employee-rate").value = person && person.rate != null ? String(person.rate) : "";
  document.getElementById("employee-status").hidden = true;
  document.getElementById("employee").showModal();
}

function openLog(item) {
  editingLog = item && item.id ? item.id : null;
  fillEmployees(item && item.employeeId);
  document.getElementById("log-date").value = item && item.date ? item.date : todayKey();
  document.getElementById("log-object").value = item && item.object ? item.object : "";
  document.getElementById("log-work").value = item && item.workId ? item.workId : "";
  document.getElementById("log-quantity").value = item && item.quantity != null ? String(item.quantity) : "";
  document.getElementById("log-status").hidden = true;
  document.getElementById("log").showModal();
}

document.getElementById("employee-add").addEventListener("click", function () { openEmployee(null); });
document.getElementById("employee-close").addEventListener("click", function () { document.getElementById("employee").close(); });
document.getElementById("employee-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const body = {
    name: document.getElementById("employee-name").value.trim(),
    payType: document.getElementById("employee-type").value,
    rate: document.getElementById("employee-rate").value.trim()
  };
  const response = await api("/api/employees" + (editingEmployee ? "/" + editingEmployee : ""), {
    method: editingEmployee ? "PATCH" : "POST",
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    document.getElementById("employee-status").hidden = false;
    document.getElementById("employee-status").textContent = "Не удалось сохранить сотрудника.";
    return;
  }
  document.getElementById("employee").close();
  await load();
});

document.getElementById("log-add").addEventListener("click", function () { openLog(null); });
document.getElementById("log-close").addEventListener("click", function () { document.getElementById("log").close(); });
document.getElementById("log-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const body = {
    employeeId: Number(logEmployee.value),
    date: document.getElementById("log-date").value,
    object: document.getElementById("log-object").value.trim(),
    workId: document.getElementById("log-work").value.trim(),
    quantity: document.getElementById("log-quantity").value.trim()
  };
  const response = await api("/api/worklogs" + (editingLog ? "/" + editingLog : ""), {
    method: editingLog ? "PATCH" : "POST",
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    document.getElementById("log-status").hidden = false;
    document.getElementById("log-status").textContent = "Не удалось сохранить выработку.";
    return;
  }
  document.getElementById("log").close();
  await load();
});

document.getElementById("pay-close").addEventListener("click", function () { document.getElementById("pay").close(); });
document.getElementById("pay-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const response = await api("/api/payroll/" + payingId + "/payments", {
    method: "POST",
    body: JSON.stringify({
      amount: document.getElementById("pay-amount").value.trim(),
      date: document.getElementById("pay-date").value
    })
  });
  if (!response.ok) {
    document.getElementById("pay-status").hidden = false;
    document.getElementById("pay-status").textContent = "Не удалось отметить выплату.";
    return;
  }
  document.getElementById("pay").close();
  await load();
});

from.addEventListener("change", load);
to.addEventListener("change", load);
const today = todayKey();
from.value = today.slice(0, 8) + "01";
to.value = today;

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else {
  banner.hidden = false;
  banner.textContent = "Нет сети.";
}
