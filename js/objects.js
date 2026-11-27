import { api, bindLogout, requireOwner } from "./api.js";
import { money } from "./format.js";
import { objectStatus } from "./object-status.js";

const grid = document.getElementById("grid");
const empty = document.getElementById("empty");
const skeleton = document.getElementById("skeleton");
const banner = document.getElementById("banner");
const params = new URLSearchParams(location.search);

function sumText(value) {
  if (value == null || value === "") return "—";
  return money(value);
}

function tile(object) {
  const link = document.createElement("a");
  link.className = "object-tile";
  link.href = "object.html?id=" + object.id;
  const photo = document.createElement("img");
  photo.alt = "";
  if (object.photo) photo.src = object.photo;
  else photo.hidden = true;
  const address = document.createElement("h2");
  address.textContent = object.address || "Объект";
  const status = document.createElement("p");
  status.textContent = objectStatus(object.status);
  const due = document.createElement("p");
  due.className = "num";
  due.textContent = "Остаток " + sumText(object.due);
  link.append(photo, address, status, due);
  return link;
}

async function load() {
  const query = params.get("client") ? "?client=" + encodeURIComponent(params.get("client")) : "";
  const response = await api("/api/objects" + query);
  skeleton.hidden = true;
  if (response.status === 401) {
    location.replace("login.html");
    return;
  }
  if (!response.ok) {
    banner.hidden = false;
    banner.textContent = "Сервер ещё не отдаёт объекты.";
    return;
  }
  const body = await response.json();
  const objects = Array.isArray(body) ? body : (body.objects || []);
  empty.hidden = objects.length !== 0;
  objects.forEach(function (object) { grid.append(tile(object)); });
}

document.getElementById("create-open").addEventListener("click", function () {
  if (params.get("client")) document.getElementById("client-id").value = params.get("client");
  document.getElementById("create").showModal();
});
document.getElementById("create-close").addEventListener("click", function () {
  document.getElementById("create").close();
});
document.getElementById("create-form").addEventListener("submit", async function (event) {
  event.preventDefault();
  const status = document.getElementById("create-status");
  const response = await api("/api/objects", {
    method: "POST",
    body: JSON.stringify({
      clientId: Number(document.getElementById("client-id").value),
      address: document.getElementById("address").value.trim(),
      type: document.getElementById("kind").value.trim(),
      area: document.getElementById("area").value.trim() || null,
      status: "prep"
    })
  });
  if (response.ok) {
    const created = await response.json();
    location.href = "object.html?id=" + (created.id || "");
    return;
  }
  status.hidden = false;
  status.textContent = "Не удалось создать объект.";
});

const access = await requireOwner();
if (access === "in") {
  bindLogout(document.getElementById("logout"));
  await load();
} else {
  skeleton.hidden = true;
  banner.hidden = false;
  banner.textContent = "Нет сети.";
}
