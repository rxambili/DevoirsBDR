const mongoClient = require('mongodb').MongoClient

mongoClient.connect('mongodb://localhost:27017/', function(err, client) {

    const db = client.db("PageRankDB");

    // Create a test collection
    const collection = db.collection("pageRank");

    const graph =
       [
		   { _id: "PageA" ,  value: {
			   type: "full", changed: false, pageRank: 1, adjlist: ["PageB", "PageC"]}},
           { _id: "PageB", value: {
			   type: "full", changed: false, pageRank: 1, adjlist: ["PageC"]}},
           {_id: "PageC", value: {
			   type: "full", changed: false, pageRank: 1, adjlist: ["PageA"]}},
           { _id: "PageD", value: {
			   type: "full", changed: false, pageRank: 1, adjlist: ["PageC"]}},
       ];

    collection.removeMany(); //Efface tout (sync)

    // Insert some documents to perform map reduce over
    // Utilise une promise
    collection.insertMany(graph, {w:1}).then(function (result) {

        const mapper = function () {
            const vertex = this._id;
            const adjlist = this.value.adjlist;
            const pageRank = this.value.pageRank;

            printjson(this);

            emit(vertex, this.value);

            if (adjlist.length > 0) {
                const outboundPageRank = pageRank / adjlist.length;
                for (let i = 0; i < adjlist.length; i++) {
                    let adj = adjlist[i];
                    let objet = {type: "compact", pageRank: outboundPageRank};
                    emit(adj, objet);
                }
            }

            // Pour avoir 2 valeurs mini sur toutes les clés (la full et une vide)
            // sinon toute les clés ne passe pas par le reduce
            emit(vertex, {type: "compact", pageRank: 0});

        };

        const reducer = function (name, pages) {
            const DAMPING_FACTOR = 0.85;
            const epsilon = 0.0001;
            let summedPageRanks = 0;
            let full;

            // First, find the original one
            for (let i = 0; i < pages.length; i++) {
                const val = pages[i];
                if (val.type === "full") {
                    full = val;
                } else {
                    summedPageRanks += val.pageRank;
                }
            }
            
            const dampingFactor = 1.0 - DAMPING_FACTOR;
            const newPageRank = dampingFactor + (DAMPING_FACTOR * summedPageRanks);
            const delta = full.pageRank - newPageRank;

            if (delta < epsilon) {
                full.changed = false;
            } else {
                full.changed = true;
            }
            
            full.pageRank = newPageRank;
            print("FULL: ");
            printjson(full);
            return full;

        };


        function pagerank_iteration(i, max, cb) {
            // Peform the map reduce
            collection.mapReduce(
                mapper,
                reducer, {
                    out: {replace: "pageRank"}
                    }
                ).then(function (collection) {
                    collection.find().toArray()
                        .then(function (docs) {
                            console.log("************************************************************************");
                            console.log("************************************************************************");
                            console.log("VALEUR DE I = ", i);
                            console.log("************************************************************************");
                            console.log(docs);
                            console.log("************************************************************************");

                            // 1ere condition d'arrêt. Le graphe converge (algo fini)
                            // Sinon, on a egalement un maximum d'iterations
                            // Nombre max d'iterations atteint
                            if (i == max) {
                                console.log("SORTIE CAR MAX ITER");
                                cb();
                            } else {
                                // Peform a simple find and return all the documents
                                collection.find({ "value.changed": true }).toArray()
                                    .then(function (docs) {                                        
                                        if (docs.length == 0) {
                                            console.log("SORTIE CAR CONVERGE");
                                            cb();
                                        }
                                        else {
                                            console.log("LOG DES DOCS CHANGES");
                                            console.log("************************************************************************");
                                            console.log(docs);
                                            console.log("************************************************************************");
                                            pagerank_iteration(i + 1, max, cb);
                                        }                                        
                                    });
                            }
                        });
                });
        }

        pagerank_iteration(0, 40, function fin() {
            console.log("Fin du programme!!!");
            client.close();
        })
    });
});