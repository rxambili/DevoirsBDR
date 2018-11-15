const mongojs = require("mongojs");
const db = mongojs("SpellDB", ["sourceData"]);
const fs = require("fs");

console.log("Begin json file opening >>");
const jsonFile = fs.readFileSync("resources/spells.json", { encoding: "utf8" });
//console.log(jsonFile);

console.log("Begin Database Insert >>");

db.sourceData.remove(() => {
	console.log("DB Cleanup Completd");
});

db.sourceData.insert(JSON.parse(jsonFile), () => {
	console.log("DB Insert Completed");
});
