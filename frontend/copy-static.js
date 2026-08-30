const fs = require("fs/promises");
const path = require("path");

const srcDir = path.resolve(__dirname, "js");
const outDir = path.resolve(__dirname, "../src/main/resources/static/js");

(async () => {
    await fs.mkdir(outDir, {recursive: true});
    const files = (await fs.readdir(srcDir)).filter(file => file.endsWith(".js"));

    await Promise.all(files.map(async file => {
        await fs.copyFile(path.join(srcDir, file), path.join(outDir, file));
        console.log(`Copied: ${file}`);
    }));
})();
