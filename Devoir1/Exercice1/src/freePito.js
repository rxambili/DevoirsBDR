const mongojs = require("mongojs");

const db = mongojs("SpellDB", ["sourceData", "spell_results"]);
const mapper = function() {
	if (
		this.level <= 4 &&
		this.isWizardSpell &&
		this.components.length === 1 &&
		this.components.includes("V")
	) {
		emit(this.number, this);
	}
};

const reducer = function(number, spell) {};

db.sourceData.mapReduce(mapper, reducer, {
	out: "spell_results"
});

db.spell_results.find(function(err, docs) {
	if (err) {
		console.log(err);
	}
	console.log("\n", docs);
	console.log("\n nombre de spell éligibles: ", docs.length);
});
