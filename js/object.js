import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";
import { contractBlock } from "./contract-upload.js";
import { OBJECT_STATUSES, objectStatus } from "./object-status.js";

const params = new URLSearchParams(location.search);
const id = Number(params.get("id"));
const tab = params.get("tab") || "overview";
const banner = document.getElementById("banner");
const skeleton = document.getElementById("skeleton");
const card = document.getElementById("card");
let object = null;

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function showTab(name) {
  ["overview", "contracts", "payments", "costs"].forEach(function (key) {
    document.getElementById("panel-" + key).hidden = key !== name;
  });
  document.querySelectorAll("#tabs a").forEach(function (link) {
    const next = new URLSearchParams();
    next.set("id", String(id));
    next.set("tab", link.dataset.tab);
    link.href = "object.html?" + next.toString();
    if (link.dataset.tab === name) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
}

function render() {
  document.getElementById("card-title").textContent = object.address || "Объект";
  document.getElementById("card-status").textContent = objectStatus(object.status);
  const overview = document.getElementById("panel-overview");
  overview.replaceChildren();
  const form = document.createElement("form");
  form.className = "form-grid";
  const address = document.createElement("input");
  address.value = object.address || "";
  const kind = document.createElement("input");
  kind.value = object.type || "";
  const area = document.createElement("input");
  area.value = object.area == null ? "" : String(object.area);
  const status = document.createElement("select");
  OBJECT_STATUSES.forEach(function (pair) { status.append(new Option(pair[1], pair[0])); });
  status.value = object.status || "prep";
  [["Адрес", address], ["Тип", kind], ["Площадь, м²", area], ["Статус", status]].forEach(function (pair) {
    const wrap = document.createElement("div");
    wrap.className = "field";
    const label = document.createElement("label");
    label.textContent = pair[0];
    wrap.append(label, pair[1]);
    form.append(wrap);
  });
  overview.append(form);
  const photos = document.createElement("div");
  photos.className = "photos";
  const pictures = Array.isArray(object.photos) && object.photos.length ? object.photos : (object.leadPhotos || []);
  pictures.forEach(function (photo) {
    const image = document.createElement("img");
    image.alt = "Фото объекта";
    image.src = photo.url || photo;
    photos.append(image);
  });
  if (!pictures.length && object.leadId) {
    const image = document.createElement("img");
    image.alt = "Фото из заявки";
    image.src = "/api/leads/" + object.leadId + "/photos";
    image.hidden = true;
    photos.append(image);
  }
  overview.append(photos);
  document.getElementById("save").onclick = async function () {
    const response = await api("/api/objects/" + id, {
      method: "PATCH",
      body: JSON.stringify({
        address: address.value.trim(),
        type: kind.value.trim(),
        area: area.value.trim() || null,
        status: status.value
      })
    });
    banner.hidden = response.ok;
    banner.textContent = response.ok ? "" : "Не удалось сохранить.";
    if (response.ok) document.getElementById("card-status").textContent = objectStatus(status.value);
  };

  const contracts = document.getElementById("panel-contracts");
  contracts.replaceChildren();
  const list = object.contracts || [];
  if (!list.length) contracts.textContent = "Договоров пока нет.";
  list.forEach(function (item) { contracts.append(contractBlock(item)); });

  const payments = document.getElementById("panel-payments");
  payments.replaceChildren();
  let any = false;
  list.forEach(function (contract) {
    (contract.schedule || []).forEach(function (row) {
      any = true;
      const line = document.createElement("p");
      const when = row.due || (row.dueRule && row.dueRule.basis === "act" ? "по акту" : "—");
      line.textContent = (contract.number ? contract.number + " · " : "") + (row.title || "Платёж")
        + " · " + sumText(row.amount) + " · " + when;
      payments.append(line);
    });
    (contract.addenda || []).forEach(function (addendum) {
      const line = document.createElement("p");
      line.textContent = "Допсоглашение " + (addendum.number || "") + (addendum.amount == null ? "" : " · " + sumText(addendum.amount));
      payments.append(line);
    });
  });
  if (!any) payments.textContent = "Графика платежей нет.";
  const costs = document.getElementById("panel-costs");
  costs.replaceChildren();
  const lines = object.costs || [];
  if (object.threshold != null) {
    const note = document.createElement("p");
    note.className = "muted";
    note.textContent = "Порог отклонения " + object.threshold + " %.";
    costs.append(note);
  }
  if (!lines.length) {
    const empty = document.createElement("p");
    empty.textContent = "Себестоимости по объекту нет.";
    costs.append(empty);
  }
  lines.forEach(function (line) {
    const row = document.createElement("p");
    if (line.over) row.className = "late";
    row.textContent = (line.title || "Позиция")
      + " · план " + (line.plan == null ? "—" : line.plan)
      + " · факт " + (line.fact == null ? "—" : line.fact)
      + " · отклонение " + (line.deviation == null ? "—" : line.deviation + " %");
    costs.append(row);
  });
  const labor = object.labor || [];
  if (labor.length) {
    const title = document.createElement("h3");
    title.textContent = "Зарплата в себестоимости";
    costs.append(title);
    labor.forEach(function (item) {
      const row = document.createElement("p");
      row.textContent = (item.employee || "Сотрудник") + " · " + sumText(item.amount);
      costs.append(row);
    });
  }
  const materials = object.materials || [];
  if (materials.length) {
    const title = document.createElement("h3");
    title.textContent = "Материалы";
    costs.append(title);
    materials.forEach(function (item) {
      const row = document.createElement("p");
      row.textContent = (item.title || "Позиция") + " · " + sumText(item.amount)
        + (item.supplier ? " · " + item.supplier : "");
      costs.append(row);
    });
  }
}

async function loadLeadPhotos() {
  if (!object.leadId || (object.photos && object.photos.length)) return;
  const response = await api("/api/leads/" + object.leadId + "/photos");
  if (!response.ok) return;
  const items = await response.json();
  if (!Array.isArray(items)) return;
  const box = document.querySelector("#panel-overview .photos");
  if (!box) return;
  box.replaceChildren();
  items.forEach(function (item) {
    const image = document.createElement("img");
    image.alt = "Фото из заявки";
    image.src = "/api/leads/" + object.leadId + "/photos/" + item.id;
    box.append(image);
  });
}

async function load() {
  if (!Number.isInteger(id) || id <= 0) {
    skeleton.hidden = true;
    banner.hidden = false;
    banner.textContent = "Не указан объект.";
    return;
  }
  const response = await api("/api/objects/" + id);
  skeleton.hidden = true;
  if (response.status === 401) {
    location.replace("login.html");
    return;
  }
  if (!response.ok) {
    banner.hidden = false;
    banner.textContent = "Объект не открылся.";
    return;
  }
  object = await response.json();
  card.hidden = false;
  render();
  await loadLeadPhotos();
  showTab(tab);
}

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else {
  skeleton.hidden = true;
  banner.hidden = false;
  banner.textContent = "Нет сети.";
}
