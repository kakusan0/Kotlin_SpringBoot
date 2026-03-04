// frontend/scripts/minify.js
const fs = require("fs/promises");
const path = require("path");
const {minify} = require("terser");
const JavaScriptObfuscator = require("javascript-obfuscator");
const os = require("os");

const srcDir = path.resolve(__dirname, "js");
const outDir = path.resolve(__dirname, "../src/main/resources/static/js");

const MAX_WORKERS = Math.max(1, Math.min(os.cpus().length, 8));

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
                const outputPath = path.join(outDir, file);

                const [inputStat, outputStat] = await Promise.all([
                    fs.stat(inputPath),
                    fs.stat(outputPath).catch(() => null)
                ]);

                // Skip unchanged files to avoid expensive obfuscation on every build.
                if (outputStat && outputStat.mtimeMs >= inputStat.mtimeMs) {
                    console.log(`Skip (up-to-date): ${file}`);
                    continue;
                }

                console.log(`Processing: ${file}`);

                const code = await fs.readFile(inputPath, "utf8");

                const preMinified = await minify(code, {
                    compress: true,
                    mangle: true
                });

                const obfuscated = JavaScriptObfuscator.obfuscate(
                    preMinified.code,
                    {
                        compact: true,
                        controlFlowFlattening: false,
                        deadCodeInjection: false,
                        stringArray: true,
                        rotateStringArray: true,
                        stringArrayThreshold: 0.8
                    }
                ).getObfuscatedCode();

                const finalMinified = await minify(obfuscated, {
                    compress: true,
                    mangle: true
                });

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
