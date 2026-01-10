export function getFileExtension(nameOrPath: string): string {
  const baseName = nameOrPath.split("/").pop() ?? nameOrPath;
  const dot = baseName.lastIndexOf(".");
  if (dot <= 0 || dot === baseName.length - 1) return "";
  return baseName.slice(dot + 1).toLowerCase();
}

export function stripExtension(fileName: string): string {
  const dot = fileName.lastIndexOf(".");
  if (dot <= 0) return fileName;
  return fileName.slice(0, dot);
}

export function getFileTypeLabel(nameOrPath: string): string | null {
  const ext = getFileExtension(nameOrPath);
  if (!ext || ext === "md") return null;
  if (ext === "jpeg" || ext === "jpg") return "JPG";
  if (ext === "png") return "PNG";
  if (ext === "gif") return "GIF";
  if (ext === "webp") return "WEBP";
  if (ext === "svg") return "SVG";
  if (ext === "pdf") return "PDF";
  if (ext === "zhixu") return "ZHIXU";
  return ext.toUpperCase();
}

export function isTextFile(nameOrPath: string): boolean {
  const ext = getFileExtension(nameOrPath);
  return ext === "md" || ext === "zhixu";
}

