import { api, bindLogout, errorCode, requireOwner } from "./api.js";
import { CONDITIONS, digits, GRADES, money, PACKAGES, shortWhen, SOURCES, stageMeta, STAGES, telHref, yearsLabel } from "./format.js";
import { clearErrors, fieldError, setBusy, toast } from "./ui.js";

const params = new URLSearchParams(location.search);
const id = Number(params.get("id"));
const banner = document.getElementById("banner");
const skeleton = document.getElementById("skeleton");
const card = document.getElementById("card");
const stage = document.getElementById("stage");
const grade = document.getElementById("grade");
const condition = document.getElementById("condition");
const packageSelect = document.getElementById("package");

let lead = null;
let saved = null;
let dirty = false;

function showBanner(message) {
  banner.hidden = !message;
  banner.textContent = message || "";
}

function fillSelect(select, pairs, current, emptyLabel) {
  select.replaceChildren();
  if (emptyLabel) select.append(new Option(emptyLabel, ""));
  pairs.forEach(function (pair) {
    select.append(new Option(pair[1], pair[0]));
  });
  if (current && ![...select.options].some(function (option) { return option.value === current; })) {
    select.append(new Option(String(current), String(current)));
  }
  select.value = current || "";
}

function numberOrNull(value) {
  const text = value.trim().replace(",", ".");
  if (!text) return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : NaN;
}

function payload() {
  const area = numberOrNull(document.getElementById("area").value);
  const body = {
    name: document.getElementById("name").value.trim(),
    phone: document.getElementById("phone").value.trim(),
    objectType: document.getElementById("object-type").value.trim(),
    stage: stage.value,
    floorArea: area,
    grade: grade.value || null,
    condition: condition.value || null,
    packageCode: packageSelect.value || null
  };
  if (!document.getElementById("account").hidden) {
    body.telegram = {
      username: document.getElementById("username").value.trim().replace(/^@+/, ""),
      firstName: document.getElementById("first").value.trim(),
      lastName: document.getElementById("last").value.trim(),
      bio: document.getElementById("bio").value.trim()
    };
  }
  return body;
}

function snapshot() {
  const body = payload();
  if (Number.isNaN(body.floorArea)) body.floorArea = document.getElementById("area").value;
  return JSON.stringify(body);
}

function markDirty() {
  dirty = snapshot() !== saved;
  if (dirty) sessionStorage.setItem(draftKey(), snapshot());
}

function draftKey() {
  return "mk-lead-draft-" + id;
}

function apply(data) {
  lead = data;
  document.title = (data.name || "Заявка") + " — Механика Комфорта";
  document.getElementById("card-title").textContent = data.name || "Заявка";
  const meta = stageMeta(data.stage);
  const pill = document.createElement("span");
  pill.className = "status";
  pill.dataset.tone = meta[2];
  pill.textContent = meta[1];
  const stageLine = document.getElementById("card-stage");
  stageLine.replaceChildren(pill);
  document.getElementById("name").value = data.name || "";
  document.getElementById("phone").value = data.phone || "";
  document.getElementById("object-type").value = data.objectType || "";
  fillSelect(stage, STAGES.map(function (item) { return [item[0], item[1]]; }), data.stage || "", "");
  document.getElementById("area").value = data.floorArea == null ? "" : String(data.floorArea);
  fillSelect(grade, GRADES, data.grade || "", "Не выбран");
  fillSelect(condition, CONDITIONS, data.condition || "", "Не выбрано");
  fillSelect(packageSelect, PACKAGES, data.packageCode || "", "Не выбран");
  document.getElementById("info-source").textContent = SOURCES[data.source] || data.source || "—";
  document.getElementById("info-date").textContent = shortWhen(data.createdAt) || "—";
  const phoneLink = document.createElement("a");
  phoneLink.href = telHref(data.phone);
  phoneLink.textContent = data.phone || "—";
  document.getElementById("info-phone").replaceChildren(phoneLink);
  const call = document.getElementById("call");
  call.href = telHref(data.phone);
  call.hidden = !data.phone;
  const account = document.getElementById("account");
  const profile = document.getElementById("profile");
  profile.hidden = true;
  profile.removeAttribute("src");
  if (data.telegram) {
    account.hidden = false;
    document.getElementById("first").value = data.telegram.firstName || "";
    document.getElementById("last").value = data.telegram.lastName || "";
    document.getElementById("username").value = data.telegram.username || "";
    document.getElementById("bio").value = data.telegram.bio || "";
    if (data.telegram.photo) {
      profile.hidden = false;
      profile.src = "/api/leads/" + id + "/telegram-photo";
      profile.addEventListener("error", function () { profile.hidden = true; }, { once: true });
    }
  } else {
    account.hidden = true;
  }
  saved = snapshot();
  dirty = false;
  card.hidden = false;
  showDialog(data);
  offerDraft();
}

const WORK_TITLES = {
  screed: "Стяжка пола",
  plaster: "Штукатурка стен по маякам",
  putty: "Шпаклёвка стен под покраску",
  paint_walls: "Покраска стен",
  paint_ceiling: "Покраска потолка",
  drywall: "Перегородки и обшивка ГКЛ",
  tile_floor: "Плитка на пол",
  tile_walls: "Плитка на стены",
  laminate: "Ламинат",
  wallpaper: "Обои",
  stretch: "Натяжной потолок"
};

const COMPLEX_TITLES = {
  panel: "Сборка щита",
  power: "Промышленная электрика",
  air: "Сжатый воздух",
  acs: "СКУД",
  audio: "Аудиосистема",
  cctv: "Видеонаблюдение нестандарт"
};

function fact(list, label, value) {
  if (value == null || value === "") return;
  const term = document.createElement("dt");
  term.textContent = label;
  const detail = document.createElement("dd");
  detail.textContent = value;
  list.append(term, detail);
}

function titled(ids, titles) {
  if (ids == null) return null;
  if (!ids.length) return "не выбраны";
  return ids.map(function (id) { return titles[id] || id; }).join(", ");
}

function showDialog(data) {
  const box = document.getElementById("dialog-facts");
  const list = document.getElementById("dialog-facts-list");
  list.replaceChildren();
  const sections = {
    finish: "Отделка",
    eng: "Инженерия",
    both: "Отделка и инженерия",
    complex: "Сложная система"
  };
  fact(list, "Раздел", data.section == null || data.section === "" ? null : (sections[data.section] || data.section));
  fact(list, "Город", data.city);
  fact(list, "Работы", titled(data.works, WORK_TITLES));
  fact(list, "Электроточки", data.electro);
  fact(list, "Сантехнические точки", data.water);
  fact(list, "Камеры", data.cameras);
  fact(list, "Интернет и СКС", data.lan);
  fact(list, "Штробление", data.chase == null ? null : (data.chase ? "Да" : "Нет"));
  fact(list, "Высота потолка, м", data.heightM);
  fact(list, "Сложная система", titled(data.complexItems, COMPLEX_TITLES));
  fact(list, "Комментарий", data.complexNote);
  box.hidden = list.childElementCount === 0;
}

function offerDraft() {
  const raw = sessionStorage.getItem(draftKey());
  if (!raw || raw === saved) return;
  let draft;
  try {
    draft = JSON.parse(raw);
  } catch {
    sessionStorage.removeItem(draftKey());
    return;
  }
  const keep = window.confirm("На этом устройстве есть несохранённые правки. Восстановить?");
  if (!keep) {
    sessionStorage.removeItem(draftKey());
    return;
  }
  document.getElementById("name").value = draft.name || "";
  document.getElementById("phone").value = draft.phone || "";
  document.getElementById("object-type").value = draft.objectType || "";
  stage.value = draft.stage || "";
  document.getElementById("area").value = draft.floorArea == null ? "" : String(draft.floorArea);
  grade.value = draft.grade || "";
  condition.value = draft.condition || "";
  packageSelect.value = draft.packageCode || "";
  if (draft.telegram && !document.getElementById("account").hidden) {
    document.getElementById("first").value = draft.telegram.firstName || "";
    document.getElementById("last").value = draft.telegram.lastName || "";
    document.getElementById("username").value = draft.telegram.username || "";
    document.getElementById("bio").value = draft.telegram.bio || "";
  }
  markDirty();
}

function estimateText(estimate) {
  if (!estimate) return "";
  const parts = [];
  if (estimate.teaching) parts.push("Учебный прайс.");
  if (estimate.low != null && estimate.high != null) {
    parts.push(money(estimate.low) + " – " + money(estimate.high) + (estimate.middle != null ? ", середина " + money(estimate.middle) : "") + ".");
  }
  if (estimate.labor != null || estimate.material != null) {
    parts.push("Работа " + money(estimate.labor) + ", материал " + money(estimate.material) + ".");
  }
  if (estimate.warrantyYears != null) {
    parts.push("Гарантия " + estimate.warrantyYears + " " + yearsLabel(estimate.warrantyYears) + ".");
  }
  parts.push("Цена в договоре фиксируется.");
  if (estimate.belowMin) parts.push("Ниже минимального заказа, менеджер подтверждает выезд.");
  return parts.join(" ");
}

function showEstimate(estimate) {
  const box = document.getElementById("estimate-box");
  const text = document.getElementById("estimate");
  box.classList.toggle("is-teaching", Boolean(estimate && estimate.teaching));
  if (estimate) {
    text.textContent = estimateText(estimate);
    return;
  }
  const area = document.getElementById("area").value.trim();
  const chosen = grade.value;
  text.textContent = !area || !chosen
    ? "Пока площади или класса нет, суммы нет."
    : "Расчёт не пришёл.";
}

async function loadPhotos() {
  const box = document.getElementById("photos");
  const empty = document.getElementById("photos-empty");
  box.replaceChildren();
  const response = await api("/api/leads/" + id + "/photos");
  if (!response.ok) {
    empty.hidden = false;
    empty.textContent = "Не удалось загрузить фото.";
    return;
  }
  const items = await response.json();
  if (!Array.isArray(items) || items.length === 0) {
    empty.hidden = false;
    empty.textContent = "Фото объекта нет.";
    return;
  }
  empty.hidden = true;
  items.forEach(function (item) {
    const button = document.createElement("button");
    button.type = "button";
    const image = document.createElement("img");
    image.alt = "Фото объекта";
    image.loading = "lazy";
    image.src = "/api/leads/" + id + "/photos/" + item.id;
    button.append(image);
    button.addEventListener("click", function () {
      document.getElementById("viewer-image").src = image.src;
      document.getElementById("viewer").showModal();
    });
    box.append(button);
  });
}

function validate() {
  clearErrors(document.getElementById("editor"));
  const name = document.getElementById("name");
  const phone = document.getElementById("phone");
  const objectType = document.getElementById("object-type");
  const area = document.getElementById("area");
  const nameText = name.value.trim();
  const objectText = objectType.value.trim();
  fieldError(name, nameText.length < 2 || nameText.length > 80 ? "Имя — от 2 до 80 символов." : "");
  const count = digits(phone.value).length;
  fieldError(phone, phone.value.trim().length > 40 || count < 10 || count > 15 ? "Нужен телефон из 10–15 цифр." : "");
  fieldError(objectType, objectText.length < 2 || objectText.length > 80 ? "Укажите тип объекта, от 2 до 80 символов." : "");
  fieldError(stage, stage.value ? "" : "Выберите этап.");
  const areaNumber = numberOrNull(area.value);
  fieldError(area, Number.isNaN(areaNumber) || (areaNumber != null && areaNumber <= 0) ? "Нужна площадь числом больше нуля. Или оставьте поле пустым." : "");
  const first = [name, phone, objectType, stage, area].find(function (input) {
    return input.classList.contains("is-invalid");
  });
  if (first) first.focus();
  return !first;
}

async function save() {
  if (!validate()) return false;
  const body = payload();
  const previous = saved;
  const button = document.getElementById("save");
  setBusy(button, true, "Сохраняем…");
  try {
    const response = await api("/api/leads/" + id, {
      method: "PATCH",
      body: JSON.stringify(body)
    });
    if (response.status === 401) {
      location.replace("login.html");
      return false;
    }
    if (!response.ok) {
      const code = await errorCode(response);
      const map = { name: "name", phone: "phone", objectType: "object-type", stage: "stage" };
      const input = document.getElementById(map[code] || "name");
      fieldError(input, "Проверьте поле.");
      input.focus();
      return false;
    }
    saved = snapshot();
    dirty = false;
    sessionStorage.removeItem(draftKey());
    document.getElementById("card-title").textContent = body.name;
    toast("Сохранили.", {
      undo: async function () {
        const revert = JSON.parse(previous);
        await api("/api/leads/" + id, { method: "PATCH", body: JSON.stringify(revert) });
        apply(Object.assign({}, lead, revert));
      }
    });
    await refreshEstimate();
    return true;
  } catch {
    showBanner("Не удалось сохранить.");
    return false;
  } finally {
    setBusy(button, false);
  }
}

async function refreshEstimate() {
  const response = await api("/api/leads/" + id);
  if (!response.ok) return;
  const detail = await response.json();
  showEstimate(detail && detail.estimate);
}

function listHref() {
  const search = sessionStorage.getItem("mk-list-query") || "";
  return "leads.html" + search;
}

function askLeave() {
  const dialog = document.getElementById("leave");
  dialog.returnValue = "stay";
  dialog.showModal();
  return new Promise(function (resolve) {
    dialog.addEventListener("close", function () {
      resolve(dialog.returnValue || "stay");
    }, { once: true });
  });
}

async function goList() {
  if (dirty) {
    const choice = await askLeave();
    if (choice === "stay" || !choice) return;
    if (choice === "save") {
      const ok = await save();
      if (!ok) return;
    } else {
      sessionStorage.removeItem(draftKey());
      dirty = false;
    }
  }
  location.href = listHref();
}

document.getElementById("editor").addEventListener("input", markDirty);
document.getElementById("editor").addEventListener("change", markDirty);
document.getElementById("save").addEventListener("click", save);
document.getElementById("back").addEventListener("click", goList);
document.querySelector(".bar a").addEventListener("click", function (event) {
  if (!dirty) return;
  event.preventDefault();
  goList();
});
document.getElementById("viewer-close").addEventListener("click", function () {
  document.getElementById("viewer").close();
});

window.addEventListener("beforeunload", function (event) {
  if (!dirty) return;
  event.preventDefault();
  event.returnValue = "";
});

async function load() {
  if (!Number.isInteger(id) || id <= 0) {
    skeleton.hidden = true;
    showBanner("Не указана заявка.");
    return;
  }
  try {
    const response = await api("/api/leads/" + id);
    if (response.status === 401) {
      location.replace("login.html");
      return;
    }
    if (response.status === 404) {
      skeleton.hidden = true;
      showBanner("Такой заявки нет.");
      return;
    }
    let detail = null;
    if (response.ok) detail = await response.json();
    let data = detail && detail.name ? detail : null;
    if (!data) {
      const list = await api("/api/leads");
      if (list.ok) {
        const rows = await list.json();
        data = rows.find(function (item) { return item.id === id; }) || null;
      }
    }
    if (!data) {
      skeleton.hidden = true;
      showBanner("Не удалось открыть заявку.");
      return;
    }
    if (detail && detail.estimate) data.estimate = detail.estimate;
    skeleton.hidden = true;
    apply(data);
    showEstimate(data.estimate);
    await loadPhotos();
    const opened = new Set(JSON.parse(localStorage.getItem("mk-opened-leads") || "[]"));
    opened.add(id);
    localStorage.setItem("mk-opened-leads", JSON.stringify([...opened]));
  } catch {
    skeleton.hidden = true;
    showBanner("Нет сети. Карточка не открылась.");
  }
}

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else if (access !== "out") {
  skeleton.hidden = true;
  showBanner(access === "offline" ? "Нет сети." : "Не удалось проверить вход.");
}
