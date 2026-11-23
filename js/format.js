export const STAGES = [
  ["new", "Новая", "idle"],
  ["measure", "Замер", "wait"],
  ["contract", "Договор", "work"],
  ["work", "В работе", "work"],
  ["done", "Сдана", "ok"],
  ["warranty", "Гарантия", "info"]
];

export const SOURCES = {
  site: "Сайт",
  telegram: "Telegram",
  manual: "Вручную"
};

export const GRADES = [
  ["economy", "Эконом"],
  ["standard", "Стандарт"],
  ["premium", "Премиум"]
];

export const CONDITIONS = [
  ["new", "Новостройка"],
  ["old", "Вторичка"]
];

export const PACKAGES = [
  ["paint", "Под покраску"],
  ["turnkey", "Под ключ без санузлов"]
];

export function stageMeta(code) {
  return STAGES.find((item) => item[0] === code) || [code, code || "—", "idle"];
}

export function money(value) {
  if (value == null || value === "") return "";
  const number = Number(value);
  if (!Number.isFinite(number)) return String(value);
  return new Intl.NumberFormat("ru-RU").format(Math.round(number)).replace(/[\u00a0\u202f]/g, " ") + " ₽";
}

export function yearsLabel(value) {
  const number = Math.abs(Number(value));
  const mod10 = number % 10;
  const mod100 = number % 100;
  if (mod10 === 1 && mod100 !== 11) return "год";
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "года";
  return "лет";
}

export function dayKey(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Saratov" }).format(date);
}

export function shortWhen(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const options = { timeZone: "Europe/Saratov" };
  const day = new Intl.DateTimeFormat("ru-RU", { ...options, day: "2-digit", month: "2-digit", year: "2-digit" }).format(date);
  const time = new Intl.DateTimeFormat("ru-RU", { ...options, hour: "2-digit", minute: "2-digit" }).format(date);
  return day + ", " + time;
}

export function ago(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const minutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (minutes < 1) return "только что";
  if (minutes < 60) return minutes + " мин назад";
  const hours = Math.round(minutes / 60);
  if (hours < 24) return hours + " ч назад";
  const days = Math.round(hours / 24);
  if (days === 1) return "вчера";
  if (days < 7) return days + " дн назад";
  return "";
}

export function telHref(phone) {
  const compact = String(phone || "").trim().replace(/[^\d+]/g, "");
  return compact ? "tel:" + compact : "";
}

export function digits(value) {
  return String(value || "").replace(/\D/g, "");
}

export function fileSize(bytes) {
  const size = Number(bytes) || 0;
  if (size < 1024) return size + " Б";
  if (size < 1024 * 1024) return Math.max(1, Math.round(size / 1024)) + " КБ";
  return (size / (1024 * 1024)).toFixed(1).replace(".", ",") + " МБ";
}

export function fileKind(file) {
  const name = (file.name || "").toLowerCase();
  const type = (file.type || "").toLowerCase();
  if (name.endsWith(".pdf") || type === "application/pdf") return "pdf";
  if (name.endsWith(".xlsx") || name.endsWith(".xls") || type.includes("spreadsheet") || type === "application/vnd.ms-excel") return "excel";
  if (name.endsWith(".json") || type === "application/json") return "json";
  return "";
}
