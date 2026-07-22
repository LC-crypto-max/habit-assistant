import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/actuator": "http://localhost:8080"
    }
  },
  build: {
    outDir: fileURLToPath(new URL("../src/main/resources/static", import.meta.url)),
    emptyOutDir: true,
    assetsDir: "assets"
  }
});
