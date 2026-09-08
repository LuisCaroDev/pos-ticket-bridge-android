import net from "node:net";
import path from "node:path";
import { pathToFileURL } from "node:url";

const desktopRoot = process.argv[2];
if (!desktopRoot) throw new Error("Usage: tsx capture-desktop-escpos.ts <desktop-repo>");

const printerModule = await import(
  pathToFileURL(path.join(desktopRoot, "src/core/printer.ts")).href
);
const received: Buffer[] = [];
const server = net.createServer((socket) =>
  socket.on("data", (chunk) => received.push(Buffer.from(chunk))),
);
await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
const address = server.address();
if (!address || typeof address === "string") throw new Error("No test port");

try {
  await printerModule.printJob(
    {
      id: "parity",
      nombre: "XPrinter XP-E260L",
      tipo: "network",
      anchoMm: 80,
      enabled: true,
      abreCajon: true,
      printProfile: {
        mode: "auto",
        language: "es",
        profileId: "xprinter-xp-e260l",
      },
      connection: { host: "127.0.0.1", port: address.port },
    },
    {
      version: 1,
      widthMm: 80,
      reason: "receipt",
      blocks: [
        { type: "text", content: "Atendió: José", font: "standard" },
        { type: "text", content: "Fuente B", font: "compact" },
        { type: "text", content: "Fuente C", font: "compact-tall" },
        { type: "table-row", left: "Subtotal", right: "S/ 195.00" },
        { type: "table-row", left: "TOTAL", right: "S/ 195.00", bold: true },
        { type: "separator", style: "dotted" },
        { type: "feed", lines: 2 },
        { type: "qr", content: "hello", size: 4 },
        { type: "barcode", content: "123456789012", format: "EAN13" },
        { type: "barcode", content: "ABC123", format: "CODE128" },
        { type: "open-drawer" },
        { type: "cut", partial: true },
      ],
    },
  );
} finally {
  await new Promise<void>((resolve) => server.close(() => resolve()));
}

await new Promise((resolve) => setTimeout(resolve, 50));
process.stdout.write(Buffer.concat(received).toString("hex"));
