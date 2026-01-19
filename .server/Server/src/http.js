function normalizeIp(raw) {
  let ip = String(raw || "").trim();
  if (!ip) return "";

  // IPv4-mapped IPv6 format: ::ffff:1.2.3.4
  if (ip.toLowerCase().startsWith("::ffff:")) ip = ip.slice("::ffff:".length);

  // [::1]:1234
  const bracketMatch = ip.match(/^\[([^\]]+)\]:(\d{1,5})$/);
  if (bracketMatch) ip = bracketMatch[1];

  // 1.2.3.4:1234
  if (/^\d{1,3}(\.\d{1,3}){3}:\d{1,5}$/.test(ip)) ip = ip.split(":")[0];

  return ip;
}

function getRequestIp(req) {
  const xff = req?.headers?.["x-forwarded-for"];
  if (typeof xff === "string") {
    const first = xff.split(",")[0]?.trim();
    if (first) return normalizeIp(first);
  } else if (Array.isArray(xff) && xff.length) {
    const first = String(xff[0] || "").split(",")[0]?.trim();
    if (first) return normalizeIp(first);
  }

  const xReal = req?.headers?.["x-real-ip"];
  if (typeof xReal === "string" && xReal.trim()) return normalizeIp(xReal.trim());
  if (Array.isArray(xReal) && xReal.length) return normalizeIp(String(xReal[0] || "").trim());

  return normalizeIp(req?.ip || req?.socket?.remoteAddress || "");
}

module.exports = { getRequestIp, normalizeIp };

