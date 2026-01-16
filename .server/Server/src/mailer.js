const nodemailer = require("nodemailer");
const { smtp } = require("./config");

let transporter = null;

function isSmtpConfigured() {
  return Boolean(smtp?.host && smtp?.from);
}

function getTransporter() {
  if (transporter) return transporter;

  const host = String(smtp.host || "").trim();
  const port = Number(smtp.port) || 587;
  const secure = Boolean(smtp.secure);
  const user = String(smtp.user || "").trim();
  const pass = String(smtp.pass || "");

  transporter = nodemailer.createTransport({
    host,
    port,
    secure,
    auth: user ? { user, pass } : undefined,
    tls: smtp.tlsRejectUnauthorized === false ? { rejectUnauthorized: false } : undefined
  });

  return transporter;
}

async function sendMail({ to, subject, text, html }) {
  if (!isSmtpConfigured()) return { ok: false, error: "smtp_not_configured" };

  const from = String(smtp.from || "").trim();
  const safeTo = String(to || "").trim();
  const safeSubject = String(subject || "").trim();

  try {
    await getTransporter().sendMail({
      from,
      to: safeTo,
      subject: safeSubject,
      text: text != null ? String(text) : undefined,
      html: html != null ? String(html) : undefined
    });
    return { ok: true };
  } catch (e) {
    return { ok: false, error: String(e?.message || "smtp_send_failed") };
  }
}

module.exports = { isSmtpConfigured, sendMail };

