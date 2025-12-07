// frontend/scripts/minify.js
const fs = require("fs/promises");
const path = require("path");
const {minify} = require("terser");
const JavaScriptObfuscator = require("javascript-obfuscator");
const os = require("os");

const srcDir = path.resolve(__dirname, "obfuscate");
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

                // ② Obfuscator（難読化）
                const obfuscated = JavaScriptObfuscator.obfuscate(code, {
                    compact: true,
                    controlFlowFlattening: true,
                    controlFlowFlatteningThreshold: 1,
                    deadCodeInjection: true,
                    deadCodeInjectionThreshold: 1,
                    stringArray: true,
                    rotateStringArray: true,
                    stringArrayThreshold: 1,
                }).getObfuscatedCode();

                // ③ Terser で最終 minify
                const minified = await minify(obfuscated, {
                    compress: true,
                    mangle: true
                });

                // ④ 出力
                await fs.writeFile(outputPath, minified.code, "utf8");
            }
        }

        await Promise.all(workers);

        console.log("✔ All obfuscated & minified!");
    } catch (err) {
        console.error(err);
        process.exit(1);
    }
})();
