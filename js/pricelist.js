import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";

const rows = document.getElementById("rows");
const wrap = document.getElementById("table-wrap");
const skeleton = document.getElementById("skeleton");
const banner = document.getElementById("banner");
const grade = document.getElementById("grade");
const teaching = document.getElementById("teaching");
let previewRows = [];

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
  banner.hidden = true;
  const response = await api("/api/internal-rates?grade=" + encodeURIComponent(grade.value));
  skeleton.hidden = true;
  if (response.status === 401) {
    banner.hidden = false;
    banner.textContent = "Нет доступа к внутреннему прайсу.";
    return;
  }
  if (!response.ok) {
    banner.hidden = false;
    banner.textContent = "Сервер ещё не отдаёт внутренний прайс.";
    return;
  }
  const body = await response.json();
  teaching.hidden = !body.teaching;
  const rates = Array.isArray(body) ? body : (body.rates || []);
  rows.replaceChildren();
  wrap.hidden = rates.length === 0;
  rates.forEach(function (rate) {
    const tr = document.createElement("tr");
    tr.append(cell((rate.title || rate.workId || "Позиция") + (rate.version ? " · v" + rate.version : "")));
    tr.append(cell(rate.unit || "—"));
    tr.append(cell(sumText(rate.labor)));
    tr.append(cell(sumText(rate.material)));
    tr.append(cell((rate.teaching ? "Учебные. " : "") + sumText(rate.clientPrice)));
    tr.append(cell(sumText(rate.margin)));
    tr.append(cell(rate.validFrom || "—"));
    const action = document.createElement("td");
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-text";
    button.textContent = "Новая версия";
    button.addEventListener("click", function () { openEdit(rate); });
    action.append(button);
    tr.append(action);
    rows.append(tr);
  });
}

function openEdit(rate) {
  document.getElementById("work-id").value = rate && rate.workId ? rate.workId : "";
  document.getElementById("title").value = rate && rate.title ? rate.title : "";
  document.getElementById("unit").value = rate && rate.unit ? rate.unit : "";
  document.getElementById("labor").value = rate && rate.labor != null ? String(rate.labor) : "";
  document.getElementById("material").value = rate && rate.material != null ? String(rate.material) : "";
  document.getElementById("valid-from").value = new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Saratov" }).format(new Date());
  document.getElementById("edit-status").hidden = true;
  document.getElementById("edit").showModal();
}

document.getElementById("add-open").addEventListener("click", function () { openEdit(null); });
document.getElementById("edit-close").addEventListener("click", function () { document.getElementById("edit").close(); });
document.getElementById("edit-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const status = document.getElementById("edit-status");
  const response = await api("/api/internal-rates", {
    method: "PUT",
    body: JSON.stringify({
      workId: document.getElementById("work-id").value.trim(),
      title: document.getElementById("title").value.trim(),
      unit: document.getElementById("unit").value.trim(),
      labor: document.getElementById("labor").value.trim(),
      material: document.getElementById("material").value.trim(),
      validFrom: document.getElementById("valid-from").value,
      grade: grade.value
    })
  });
  if (!response.ok) {
    status.hidden = false;
    status.textContent = "Не удалось сохранить версию.";
    return;
  }
  document.getElementById("edit").close();
  await load();
});

function showPreview(body) {
  const box = document.getElementById("import-preview");
  box.replaceChildren();
  previewRows = body.rows || [];
  (body.errors || []).forEach(function (error) {
    const row = document.createElement("p");
    row.className = "late";
    row.textContent = "Строка " + (error.row || "") + ": " + (error.message || "ошибка");
    box.append(row);
  });
  if (!previewRows.length && !(body.errors || []).length) {
    box.textContent = "Файл не разобрался.";
  }
  document.getElementById("import-save").disabled = previewRows.length === 0;
}

async function sendImport(confirm) {
  const file = document.getElementById("import-file").files[0];
  const status = document.getElementById("import-status");
  status.hidden = true;
  if (!confirm && !file) {
    status.hidden = false;
    status.textContent = "Выберите файл Excel.";
    return;
  }
  const form = new FormData();
  if (file) form.append("file", file, file.name);
  form.append("preview", confirm ? "false" : "true");
  if (confirm) form.append("rows", JSON.stringify(previewRows));
  const response = await api("/api/internal-rates/import", { method: "POST", body: form });
  const body = await response.json().catch(function () { return {}; });
  if (!response.ok) {
    status.hidden = false;
    status.textContent = "Файл не подошёл.";
    return;
  }
  if (!confirm) {
    showPreview(body);
    return;
  }
  document.getElementById("import").close();
  await load();
}

document.getElementById("import-open").addEventListener("click", function () {
  previewRows = [];
  document.getElementById("import-preview").replaceChildren();
  document.getElementById("import-save").disabled = true;
  document.getElementById("import-status").hidden = true;
  document.getElementById("import").showModal();
});
document.getElementById("import-close").addEventListener("click", function () { document.getElementById("import").close(); });
document.getElementById("import-check").addEventListener("click", function () { sendImport(false); });
document.getElementById("import-save").addEventListener("click", function () { sendImport(true); });
grade.addEventListener("change", load);

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else {
  skeleton.hidden = true;
  banner.hidden = false;
  banner.textContent = "Нет сети.";
}
