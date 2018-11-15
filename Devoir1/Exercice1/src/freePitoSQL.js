const sqlite = require("sqlite3").verbose();
const db = new sqlite.Database(":memory:");
const fs = require("fs");

db.serialize(() => {
	db.run(
		"CREATE TABLE spells(name TEXT, isWizardSpell INTEGER DEFAULT 0, level INTEGER, resistance INTEGER DEFAULT 0, components TEXT)"
	);
	const jsonFile = fs.readFileSync("resources/spells.json", {
		encoding: "utf8"
	});
	const spells = JSON.parse(jsonFile);
	const stmt = db.prepare(
		"INSERT INTO spells (name, isWizardSpell, level, resistance, components) VALUES (?, ?, ?, ?, ?)"
	);
	for (i in spells) {
		let components = "";
		spells[i].components.forEach(element => {
			components += element;
		});
		stmt.run([
			spells[i].name,
			spells[i].isWizardSpell,
			spells[i].level,
			spells[i].resistance,
			components
		]);
	}
	stmt.finalize();

	let nbSpellSelected = 0;
	db.each(
		"SELECT rowid AS id, name, level, isWizardSpell, components FROM spells WHERE isWizardSpell == 1 AND level <= 4 AND components == 'V'",
		[],
		(err, row) => {
			nbSpellSelected++;
			console.log(
				`${row.id}: ${row.name}; Wizard Spell: ${
					row.isWizardSpell ? "YES" : "NO"
				}; Level: ${row.level}; Components: ${
					row.components
				} [${nbSpellSelected}]`
			);
		}
	);
});

db.close();
