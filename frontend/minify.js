// frontend/scripts/minify.js
const fs = require("fs/promises");
const path = require("path");
const {minify} = require("terser");
const JavaScriptObfuscator = require("javascript-obfuscator");
const os = require("os");

const srcDir = path.resolve(__dirname, "js");
const outDir = path.resolve(__dirname, "../src/main/resources/static/js");

const MAX_WORKERS = os.cpus().length;

(async () => {
    try {
        await fs.mkdir(outDir, {recursive: true});

        const files = (await fs.readdir(srcDir)).filter(f => f.endsWith(".js"));
        const queue = [...files];
        const workers = Array.from({length: MAX_WORKERS}, () => worker());

        async function worker() {
            while (queue.length > 0) {
                const file = queue.shift();
                if (!file) return;

                const inputPath = path.join(srcDir, file);
                const outputPath = path.join(outDir, file.replace(".js", ".min.js"));

                console.log(`Processing: ${file}`);

                // ① 元コード読み込み
                const code = await fs.readFile(inputPath, "utf8");

                // ② 一旦 Terser で軽量 minify（高速化のため）
                const preMinified = await minify(code, {
                    compress: true,
                    mangle: true
                });

                // ③ Obfuscator（難読化）
                const obfuscated = JavaScriptObfuscator.obfuscate(
                    preMinified.code,
                    {
                        compact: true,
                        controlFlowFlattening: false,       // ← 重いので無効でも強い難読化
                        deadCodeInjection: false,           // ← 重い
                        stringArray: true,
                        rotateStringArray: true,
                        stringArrayThreshold: 0.8
                    }
                ).getObfuscatedCode();

                // ④ 最後にもう一度 Terser（任意）
                const finalMinified = await minify(obfuscated, {
                    compress: true,
                    mangle: true
                });

                // ⑤ 出力
                await fs.writeFile(outputPath, finalMinified.code, "utf8");
            }
        }

        await Promise.all(workers);

        console.log("✔ All processed (minify → obfuscate → minify)!");
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
})();
