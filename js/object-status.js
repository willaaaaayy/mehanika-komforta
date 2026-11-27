export const OBJECT_STATUSES = [
  ["prep", "Подготовка"],
  ["work", "В работе"],
  ["acceptance", "Приёмка"],
  ["done", "Сдан"],
  ["warranty", "Гарантия"]
];

export function objectStatus(code) {
  const found = OBJECT_STATUSES.find(function (item) { return item[0] === code; });
  return found ? found[1] : (code || "—");
}
