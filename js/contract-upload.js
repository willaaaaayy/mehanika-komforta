import { api } from "./api.js";
import { money } from "./format.js";

const CONFIDENCE = { high: "confidence-high", check: "confidence-check", none: "confidence-none" };

function dueLabel(row) {
  if (row && row.due) return row.due;
  const rule = row && row.dueRule;
  if (rule && rule.basis === "act") {
    return rule.businessDays ? "по акту, " + rule.businessDays + " банк. дн." : "по акту";
  }
  return "";
}

function numberOrNull(value) {
  const text = String(value || "").trim().replace(/\s/g, "").replace(",", ".");
  if (!text) return null;
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
}

function marked(key, draft) {
  const level = draft.confidence && draft.confidence[key];
  return CONFIDENCE[level] || "";
}

export function openContractUpload(options) {
  options = options || {};
  const dialog = document.createElement("dialog");
  dialog.className = "dialog-wide";
  const form = document.createElement("form");
  form.className = "dialog-body";
  const title = document.createElement("h2");
  title.textContent = options.addendumOf ? "Допсоглашение" : "Договор";
  const hint = document.createElement("p");
  hint.className = "muted";
  hint.textContent = "PDF или DOCX, не больше 20 МБ. До кнопки «Создать» в карточку и финансы ничего не попадает.";
  const formats = document.createElement("div");
  formats.className = "formats";
  const pdf = document.createElement("button");
  pdf.type = "button";
  pdf.className = "btn";
  pdf.textContent = "PDF";
  const docx = document.createElement("button");
  docx.type = "button";
  docx.className = "btn";
  docx.textContent = "DOCX";
  formats.append(pdf, docx);
  const fileInput = document.createElement("input");
  fileInput.type = "file";
  fileInput.hidden = true;
  const status = document.createElement("p");
  status.className = "note";
  status.hidden = true;
  const review = document.createElement("div");
  review.hidden = true;
  const actions = document.createElement("div");
  actions.className = "dialog-actions";
  const submit = document.createElement("button");
  submit.type = "submit";
  submit.className = "btn btn-primary";
  submit.textContent = "Создать";
  submit.disabled = true;
  const close = document.createElement("button");
  close.type = "button";
  close.className = "btn";
  close.textContent = "Закрыть";
  actions.append(submit, close);
  form.append(title, hint, formats, fileInput, status, review, actions);
  dialog.append(form);
  document.body.append(dialog);
  dialog.showModal();

  let draft = null;
  let localUrl = "";

  function say(text) {
    status.hidden = !text;
    status.textContent = text || "";
  }

  function paint(next) {
    draft = next || {};
    review.hidden = false;
    submit.disabled = false;
    review.replaceChildren();
    const split = document.createElement("div");
    split.className = "review-split";
    const doc = document.createElement("div");
    doc.className = "doc-pane";
    if (localUrl && draft.fileType === "pdf") {
      const frame = document.createElement("iframe");
      frame.title = "Договор";
      frame.src = localUrl;
      doc.append(frame);
    }
    const text = document.createElement("div");
    text.className = "doc-text";
    const paragraphs = Array.isArray(draft.paragraphs) ? draft.paragraphs : String(draft.text || "").split(/\n+/).filter(Boolean);
    if (!paragraphs.length) {
      const empty = document.createElement("p");
      empty.textContent = draft.scan ? "В файле нет текста, точность ниже." : "Текст документа сервер не прислал.";
      text.append(empty);
    }
    paragraphs.forEach(function (paragraph, index) {
      const row = document.createElement("p");
      row.dataset.paragraph = String(index);
      row.textContent = typeof paragraph === "string" ? paragraph : (paragraph.text || "");
      text.append(row);
    });
    if (draft.scan) {
      const warn = document.createElement("p");
      warn.className = "note teaching";
      warn.textContent = "В файле нет текста, точность ниже.";
      doc.append(warn);
    }
    doc.append(text);
    const fields = document.createElement("div");
    if (draft.compare) {
      const compare = document.createElement("div");
      compare.className = "note";
      const amount = draft.compare.amount;
      if (amount) compare.append(document.createTextNode("Сумма: " + money(amount.was) + " → " + money(amount.now) + ". "));
      const dates = draft.compare.schedule || draft.compare.due;
      if (dates && (dates.was || dates.now)) {
        compare.append(document.createTextNode("Сроки: " + (dates.was || "—") + " → " + (dates.now || "—") + "."));
      }
      fields.append(compare);
    }
    function bind(key, label, value, node) {
      const wrap = document.createElement("div");
      wrap.className = "field";
      const name = document.createElement("label");
      name.textContent = label;
      node.classList.add(marked(key, draft));
      node.value = value == null ? "" : String(value);
      node.addEventListener("focus", function () {
        const source = draft.source && draft.source[key];
        text.querySelectorAll(".is-marked").forEach(function (item) { item.classList.remove("is-marked"); });
        if (!source) return;
        const target = text.querySelector('[data-paragraph="' + source.paragraph + '"]');
        if (target) {
          target.classList.add("is-marked");
          target.scrollIntoView({ block: "nearest" });
        }
      });
      wrap.append(name, node);
      fields.append(wrap);
      return node;
    }
    const inputs = {};
    inputs.number = bind("number", "Номер", draft.number, document.createElement("input"));
    inputs.date = bind("date", "Дата", draft.date, document.createElement("input"));
    inputs.subject = bind("subject", "Предмет", draft.subject, document.createElement("input"));
    inputs.amount = bind("amount", "Сумма, ₽", draft.amount, document.createElement("input"));
    inputs.vat = bind("vat", "НДС", draft.vat, document.createElement("input"));
    const client = draft.client || {};
    inputs.clientName = bind("client.name", "Клиент", client.name, document.createElement("input"));
    inputs.inn = bind("client.inn", "ИНН", client.inn, document.createElement("input"));
    inputs.kpp = bind("client.kpp", "КПП", client.kpp, document.createElement("input"));
    inputs.ogrn = bind("client.ogrn", "ОГРН", client.ogrn, document.createElement("input"));
    inputs.clientAddress = bind("client.address", "Адрес стороны", client.address, document.createElement("input"));
    inputs.signatory = bind("client.signatory", "Подписант", client.signatory, document.createElement("input"));
    inputs.objectAddress = bind("object.address", "Объект", draft.object && draft.object.address, document.createElement("input"));
    const schedule = document.createElement("div");
    (draft.schedule || [{ title: "", kind: "advance", amount: null, due: null }]).forEach(function (row, index) {
      const line = document.createElement("div");
      line.className = "schedule-edit";
      const rowTitle = document.createElement("input");
      rowTitle.value = row.title || "";
      const amount = document.createElement("input");
      amount.value = row.amount == null ? "" : String(row.amount);
      const due = document.createElement("input");
      due.type = "date";
      due.value = row.due || "";
      const kind = document.createElement("select");
      [["advance", "Аванс"], ["stage", "Этап"], ["final", "Окончательный"]].forEach(function (pair) {
        kind.append(new Option(pair[1], pair[0]));
      });
      kind.value = row.kind || "advance";
      const rule = document.createElement("span");
      rule.className = "muted";
      rule.textContent = dueLabel(row);
      line.dataset.rule = row.dueRule ? JSON.stringify(row.dueRule) : "";
      line.append(rowTitle, amount, due, kind, rule);
      amount.classList.add(marked("schedule." + index + ".amount", draft));
      schedule.append(line);
    });
    fields.append(schedule);
    split.append(doc, fields);
    review.append(split);
  }

  async function send(file) {
    say("");
    const name = (file.name || "").toLowerCase();
    const kind = file.type === "application/pdf" || name.endsWith(".pdf") ? "pdf" : "docx";
    if (file.size > 20 * 1024 * 1024) {
      say("Файл больше 20 МБ.");
      return;
    }
    if (localUrl) URL.revokeObjectURL(localUrl);
    localUrl = URL.createObjectURL(file);
    const body = new FormData();
    body.append("file", file, file.name);
    if (options.clientId) body.append("clientId", String(options.clientId));
    if (options.leadId) body.append("leadId", String(options.leadId));
    if (options.addendumOf) body.append("addendumOf", String(options.addendumOf));
    submit.disabled = true;
    submit.textContent = "Читаем документ…";
    try {
      const response = await api("/api/contracts", { method: "POST", body: body });
      const payload = await response.json().catch(function () { return {}; });
      if (response.status === 401) {
        location.replace("login.html");
        return;
      }
      if (response.status === 409) {
        say("Такой договор уже загружен.");
        return;
      }
      if (response.status === 400) {
        say("Документ не читается. Введите поля вручную.");
        paint({ fileType: kind, scan: true, schedule: [{ title: "", kind: "advance", amount: null, due: null }] });
        return;
      }
      if (!response.ok) {
        say("Сервер пока не принял договор.");
        return;
      }
      payload.fileType = kind;
      paint(payload);
    } catch {
      say("Не удалось загрузить файл.");
    } finally {
      submit.disabled = review.hidden;
      submit.textContent = "Создать";
    }
  }

  function choose(accept) {
    fileInput.accept = accept;
    fileInput.value = "";
    fileInput.click();
  }
  pdf.addEventListener("click", function () { choose(".pdf,application/pdf"); });
  docx.addEventListener("click", function () { choose(".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"); });
  fileInput.addEventListener("change", function () {
    if (fileInput.files[0]) send(fileInput.files[0]);
  });
  close.addEventListener("click", function () { dialog.close(); });
  dialog.addEventListener("close", function () {
    if (localUrl) URL.revokeObjectURL(localUrl);
    dialog.remove();
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    if (!draft) return;
    const schedule = [...review.querySelectorAll(".schedule-edit")].map(function (line) {
      const nodes = line.querySelectorAll("input, select");
      let dueRule = null;
      if (line.dataset.rule) {
        try { dueRule = JSON.parse(line.dataset.rule); } catch { dueRule = null; }
      }
      return {
        title: nodes[0].value.trim(),
        amount: numberOrNull(nodes[1].value),
        due: nodes[2].value || null,
        kind: nodes[3].value,
        dueRule: nodes[2].value ? null : dueRule
      };
    });
    const body = {
      number: review.querySelectorAll("input")[0] ? undefined : undefined,
      clientId: options.clientId || null,
      leadId: options.leadId || null,
      addendumOf: options.addendumOf || null,
      schedule: schedule
    };
    const named = review.querySelectorAll(".field input");
    const keys = ["number", "date", "subject", "amount", "vat", "clientName", "inn", "kpp", "ogrn", "clientAddress", "signatory", "objectAddress"];
    const values = {};
    named.forEach(function (node, index) { values[keys[index]] = node.value.trim(); });
    body.number = values.number;
    body.date = values.date || null;
    body.subject = values.subject;
    body.amount = numberOrNull(values.amount);
    body.vat = numberOrNull(values.vat);
    body.client = {
      name: values.clientName,
      inn: values.inn,
      kpp: values.kpp,
      ogrn: values.ogrn,
      address: values.clientAddress,
      signatory: values.signatory
    };
    body.object = { address: values.objectAddress };
    submit.disabled = true;
    try {
      const path = draft.id ? "/api/contracts/" + draft.id : "/api/contracts";
      const response = await api(path, { method: draft.id ? "PATCH" : "POST", body: JSON.stringify(body) });
      const payload = await response.json().catch(function () { return {}; });
      if (response.status === 409) {
        say("Такой договор уже загружен.");
        return;
      }
      if (!response.ok) {
        say("Не удалось записать договор.");
        return;
      }
      dialog.close();
      const clientId = payload.clientId || options.clientId;
      if (clientId && !options.stay) location.href = "client.html?id=" + clientId + "&tab=contracts";
      else if (options.onDone) options.onDone();
    } catch {
      say("Не удалось записать договор.");
    } finally {
      submit.disabled = false;
    }
  });
}

export function contractBlock(contract, onAddendum) {
  const block = document.createElement("article");
  block.className = "panel";
  const title = document.createElement("h3");
  title.textContent = contract.number ? "Договор " + contract.number : "Договор";
  block.append(title);
  const party = contract.client || {};
  const object = contract.object || {};
  [
    ["Сумма", contract.amount == null ? "—" : money(contract.amount)],
    ["Клиент", party.name || contract.clientName || "—"],
    ["ИНН", party.inn || "—"],
    ["Объект", object.address || contract.objectAddress || "—"]
  ].forEach(function (pair) {
    const row = document.createElement("p");
    row.textContent = pair[0] + ": " + pair[1];
    block.append(row);
  });
  const list = document.createElement("ul");
  (contract.schedule || []).forEach(function (row) {
    const item = document.createElement("li");
    const when = row.due ? row.due : dueLabel(row) || "—";
    item.textContent = (row.title || "Платёж") + " · " + (row.amount == null ? "—" : money(row.amount)) + " · " + when;
    list.append(item);
  });
  block.append(list);
  if (onAddendum && contract.id) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn";
    button.textContent = "Допсоглашение";
    button.addEventListener("click", function () { onAddendum(contract.id); });
    block.append(button);
  }
  return block;
}
