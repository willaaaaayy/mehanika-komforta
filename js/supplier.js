import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";

const params = new URLSearchParams(location.search);
const id = Number(params.get("id"));
const banner = document.getElementById("banner");
const card = document.getElementById("card");
let supplier = null;

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function todayKey() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Saratov" }).format(new Date());
}

function addLine(line) {
  const row = document.createElement("div");
  row.className = "schedule-edit";
  const title = document.createElement("input");
  title.placeholder = "Позиция";
  title.value = line && line.title ? line.title : "";
  const amount = document.createElement("input");
  amount.inputMode = "decimal";
  amount.placeholder = "Сумма";
  amount.value = line && line.amount != null ? String(line.amount) : "";
  const object = document.createElement("input");
  object.placeholder = "Объект";
  object.value = line && line.object ? line.object : "";
  row.dataset.id = line && line.id ? String(line.id) : "";
  row.append(title, amount, object);
  document.getElementById("lines").append(row);
}

function render() {
  document.getElementById("card-title").textContent = supplier.name || "Поставщик";
  document.getElementById("balance").textContent = "Закуплено " + sumText(supplier.purchased)
    + " · оплачено " + sumText(supplier.paid)
    + " · долг " + sumText(supplier.debt)
    + " · срок " + (supplier.nextDue || "—")
    + " · просрочено " + sumText(supplier.overdue);
  const box = document.getElementById("documents");
  box.replaceChildren();
  const docs = supplier.documents || [];
  if (!docs.length) box.textContent = "Накладных и счетов нет.";
  docs.forEach(function (doc) {
    const block = document.createElement("article");
    const title = document.createElement("h3");
    title.textContent = (doc.kind === "invoice" ? "Счёт " : "Накладная ") + (doc.number || "");
    block.append(title);
    (doc.lines || []).forEach(function (line) {
      const row = document.createElement("p");
      row.textContent = (line.title || "Позиция") + " · " + sumText(line.amount)
        + " · " + (line.object || "не разнесено");
      if (!line.object && line.id) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "btn btn-text";
        button.textContent = "Разнести";
        button.addEventListener("click", async function () {
          const object = window.prompt("Объект");
          if (!object) return;
          await api("/api/supplier-lines/" + line.id, {
            method: "PATCH",
            body: JSON.stringify({ object: object.trim() })
          });
          await load();
        });
        row.append(button);
      }
      block.append(row);
    });
    box.append(block);
  });
  const today = todayKey();
  document.getElementById("from").value = today.slice(0, 8) + "01";
  document.getElementById("to").value = today;
  updateStatement();
}

function updateStatement() {
  const query = new URLSearchParams();
  if (document.getElementById("from").value) query.set("from", document.getElementById("from").value);
  if (document.getElementById("to").value) query.set("to", document.getElementById("to").value);
  document.getElementById("statement").href = "/api/suppliers/" + id + "/statement?" + query.toString();
}

async function load() {
  const response = await api("/api/suppliers/" + id);
  if (response.status === 401) {
    location.replace("login.html");
    return;
  }
  if (!response.ok) {
    banner.hidden = false;
    banner.textContent = "Поставщик не открылся.";
    return;
  }
  supplier = await response.json();
  card.hidden = false;
  render();
}

document.getElementById("edit").addEventListener("click", function () {
  document.getElementById("name").value = supplier.name || "";
  document.getElementById("inn").value = supplier.inn || "";
  document.getElementById("phone").value = supplier.phone || "";
  document.getElementById("address").value = supplier.address || "";
  document.getElementById("contract").value = supplier.contract || "";
  document.getElementById("requisites").showModal();
});
document.getElementById("requisites-close").addEventListener("click", function () {
  document.getElementById("requisites").close();
});
document.getElementById("requisites-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const response = await api("/api/suppliers/" + id, {
    method: "PATCH",
    body: JSON.stringify({
      name: document.getElementById("name").value.trim(),
      inn: document.getElementById("inn").value.trim(),
      phone: document.getElementById("phone").value.trim(),
      address: document.getElementById("address").value.trim(),
      contract: document.getElementById("contract").value.trim()
    })
  });
  if (!response.ok) return;
  document.getElementById("requisites").close();
  await load();
});

document.getElementById("doc-open").addEventListener("click", function () {
  document.getElementById("lines").replaceChildren();
  addLine(null);
  document.getElementById("doc-date").value = todayKey();
  document.getElementById("doc-status").hidden = true;
  document.getElementById("document").showModal();
});
document.getElementById("add-line").addEventListener("click", function () { addLine(null); });
document.getElementById("document-close").addEventListener("click", function () {
  document.getElementById("document").close();
});
document.getElementById("document-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const lines = [...document.querySelectorAll("#lines .schedule-edit")].map(function (row) {
    const fields = row.querySelectorAll("input");
    return {
      title: fields[0].value.trim(),
      amount: fields[1].value.trim(),
      object: fields[2].value.trim()
    };
  });
  const form = new FormData();
  form.append("kind", document.getElementById("doc-kind").value);
  form.append("number", document.getElementById("doc-number").value.trim());
  form.append("date", document.getElementById("doc-date").value);
  form.append("lines", JSON.stringify(lines));
  const file = document.getElementById("doc-file").files[0];
  if (file) form.append("file", file, file.name);
  const response = await api("/api/suppliers/" + id + "/documents", { method: "POST", body: form });
  if (!response.ok) {
    document.getElementById("doc-status").hidden = false;
    document.getElementById("doc-status").textContent = "Не удалось записать документ.";
    return;
  }
  document.getElementById("document").close();
  await load();
});

document.getElementById("pay-open").addEventListener("click", function () {
  document.getElementById("pay-date").value = todayKey();
  document.getElementById("pay-amount").value = supplier.debt == null ? "" : String(supplier.debt);
  document.getElementById("pay-status").hidden = true;
  document.getElementById("pay").showModal();
});
document.getElementById("pay-close").addEventListener("click", function () { document.getElementById("pay").close(); });
document.getElementById("pay-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const response = await api("/api/suppliers/" + id + "/payments", {
    method: "POST",
    body: JSON.stringify({
      amount: document.getElementById("pay-amount").value.trim(),
      date: document.getElementById("pay-date").value
    })
  });
  if (!response.ok) {
    document.getElementById("pay-status").hidden = false;
    document.getElementById("pay-status").textContent = "Не удалось отметить оплату.";
    return;
  }
  document.getElementById("pay").close();
  await load();
});
document.getElementById("from").addEventListener("change", updateStatement);
document.getElementById("to").addEventListener("change", updateStatement);

if (!Number.isInteger(id) || id <= 0) {
  banner.hidden = false;
  banner.textContent = "Не указан поставщик.";
} else {
  const access = await requireOwner();
  if (access === "in") {
    bindLogout(document.getElementById("logout"));
    await load();
  } else {
    banner.hidden = false;
    banner.textContent = "Нет сети.";
  }
}
