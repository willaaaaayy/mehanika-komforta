export function csrf() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

export async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  const method = (options.method || "GET").toUpperCase();
  const init = { credentials: "same-origin", method, cache: "no-store", headers };
  if (method !== "GET" && method !== "HEAD") {
    const token = csrf();
    if (token) headers.set("X-XSRF-TOKEN", token);
    if (options.body != null && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    init.body = options.body;
  }
  const response = await fetch(path, init);
  if (response.status === 403 && !options.retried) {
    await fetch("/api/session", { credentials: "same-origin", cache: "no-store" });
    return api(path, { ...options, retried: true });
  }
  return response;
}

export async function errorCode(response) {
  try {
    const body = await response.json();
    return body && body.error ? String(body.error) : "";
  } catch {
    return "";
  }
}

export async function requireOwner() {
  try {
    const response = await api("/api/session");
    if (response.status === 204) return "in";
    if (response.status === 401) {
      location.replace("login.html");
      return "out";
    }
    return "error";
  } catch {
    return "offline";
  }
}

export function bindLogout(button) {
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      const response = await api("/api/session", { method: "DELETE" });
      if (response.ok || response.status === 401) {
        location.href = "login.html";
        return;
      }
    } catch {
      /* stay on the page */
    }
    button.disabled = false;
    const note = document.getElementById("banner");
    if (note) {
      note.hidden = false;
      note.textContent = "Не удалось выйти. Попробуйте ещё раз.";
    }
  });
}
