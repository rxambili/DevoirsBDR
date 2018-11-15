//Petit programme exemple pour 8inf803
//Edmond La Chance

/*
Ce petit programme en scala execute un algorithme de graphe itératif sur Spark GraphX. Cet algorithme
essaie de colorier un graphe avec un nombre de couleurs minimal (mais l'algorithme est très random et ne
donne pas de très bons résultats!). Par contre le code est très court donc il est intéressant comme exemple.

Voici comment ce programme fonctionne :

1. L'exécution commence à testPetersenGraph.
2. On crée le graphe directement dans le code. Le tiebreaking value est une valeur random (ici hardcodée)
qui permet a l'algorithme glouton de coloriage de graphe de trancher dans ses décisions
3. La boucle itérative se trouve dans la fonction execute
4. L'algorithme FC2 fonctionne de la façon suivante :
  Chaque itération, les noeuds du graphe s'envoient des messages. Si on est un noeud, et qu'on trouve un voisin qui a un meilleur
  tiebreak, on doit augmenter notre couleur. Les noeuds qui n'augmentent pas gardent une couleur fixe et
  arrêtent d'envoyer des messages.
  L'algorithme s'arrête lorsqu'il n'y a plus de messages envoyés

 C'est donc un algorithme très simple de coloriage (pas le meilleur).
 */

package Combat1 {

  import org.apache.spark.SparkConf
  import org.apache.spark.SparkContext
  import org.apache.spark.graphx.{Edge, EdgeContext, Graph, _}

  import scala.util.Random

  //tiebreak value is generated randomly before every graph iteration
  class node(val id: Int, val name: String, val hp: Int = 1, val armor: Int, val regen: Int, val atk: Int, val death: Boolean = false) extends Serializable {
    override def toString: String = s"id : $id name : $name hp : $hp dead? : $death"
  }

  class Combat1 extends Serializable {
    def getChromaticNumber(g: Graph[node, String]): Int = {
      val aa = g.vertices.collect()
      var maxColor = 0
      for (i <- aa) {
        if (i._2.color > maxColor) maxColor = i._2.color
      }
      maxColor
    }

    def getJetDes(nbFaces: Int, nbDes: Int): Int = {
      val rnd = new Random
      var res = 0
      var i = 0
      for(i <- 1 to nbDes) {
        res = res + 1 + rnd.nextInt((nbFaces - 1) + 1)
      }
      return res
    }

    def sendAtkValue(ctx: EdgeContext[node, String, Long]): Unit = {
      if (ctx.srcAttr.death == false && ctx.dstAttr.death == false) {
        ctx.sendToDst(ctx.srcAttr.tiebreakValue)
        ctx.sendToSrc(ctx.dstAttr.tiebreakValue)
      }
    }

    def sendHpValue(ctx: EdgeContext[node, String, Long]): Unit = {
      if (ctx.srcAttr.death == false && ctx.dstAttr.death == false) {
        ctx.sendToDst(ctx.srcAttr.hp)
        ctx.sendToSrc(ctx.dstAttr.hp)
      }
    }

    def selectBestTarget(hp: Int, id2: Int): Long = {
      if (id1 < id2) id1
      else id2
    }

    def execute(g: Graph[node, String], maxIterations: Int, sc: SparkContext): Graph[node, String] = {
      var myGraph = g
      var counter = 0
      val fields = new TripletFields(true, true, false) //join strategy

      def loop1: Unit = {
        while (true) {

          println("TOUR NUMERO : " + (counter + 1))
          counter += 1
          if (counter == maxIterations) return

          val messages = myGraph.aggregateMessages[Long](
            sendHpValue,
            selectBestTarget,
            fields //use an optimized join strategy (we don't need the edge attribute)
          )

          if (messages.isEmpty()) return

          myGraph = myGraph.joinVertices(messages)(
            (vid, sommet, bestId) => increaseColor(vid, sommet, bestId))

          //Ignorez : Code de debug
          var printedGraph = myGraph.vertices.collect()
          printedGraph = printedGraph.sortBy(_._1)
          printedGraph.foreach(
            elem => println(elem._2)
          )
        }

      }

      loop1 //execute loop
      myGraph //return the result graph
    }
  }

  object testCombat1 extends App {
    val conf = new SparkConf()
      .setAppName("Petersen Graph (10 nodes)")
      .setMaster("local[*]")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")
    var myVertices = sc.makeRDD(Array(
      (1L, new node(id = 1, name = "Solar", hp = 363, armor = 44, regen = 15, atk = 35, death = false)),
      (2L, new node(id = 2, name = "Worg Rider 1", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (3L, new node(id = 3, name = "Worg Rider 2", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (4L, new node(id = 4, name = "Worg Rider 3", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (5L, new node(id = 5, name = "Worg Rider 4", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (6L, new node(id = 6, name = "Worg Rider 5", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (7L, new node(id = 7, name = "Worg Rider 6", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (8L, new node(id = 8, name = "Worg Rider 7", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (9L, new node(id = 9, name = "Worg Rider 8", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (10L, new node(id = 10, name = "Worg Rider 9", hp = 13, armor = 18, regen = 0, atk = 6, death = false)),
      (11L, new node(id = 11, name = "Brutal Warlord", hp = 141, armor = 27, regen = 0, atk = 20, death = false)),
      (12L, new node(id = 12, name = "Barbare Orc 1", hp = 142, armor = 17, regen = 0, atk = 19, death = false)),
      (13L, new node(id = 13, name = "Barbare Orc 2", hp = 142, armor = 17, regen = 0, atk = 19, death = false)),
      (14L, new node(id = 14, name = "Barbare Orc 3", hp = 142, armor = 17, regen = 0, atk = 19, death = false)),
      (15L, new node(id = 15, name = "Barbare Orc 4", hp = 142, armor = 17, regen = 0, atk = 19, death = false))))

    var myEdges = sc.makeRDD(Array(
      Edge(1L, 2L, "1"),
      Edge(1L, 3L, "2"),
      Edge(1L, 4L, "3"),
      Edge(1L, 5L, "4"),
      Edge(1L, 6L, "5"),
      Edge(1L, 7L, "6"),
      Edge(1L, 8L, "7"),
      Edge(1L, 9L, "8"),
      Edge(1L, 10L, "9"),
      Edge(1L, 11L, "10"),
      Edge(1L, 12L, "11"),
      Edge(1L, 13L, "12"),
      Edge(1L, 14L, "13"),
      Edge(1L, 15L, "14")
    ))

    var myGraph = Graph(myVertices, myEdges)
    val algoCombat1 = new Combat1()
    val res = algoCombat1.execute(myGraph, 20, sc)
    println("\nNombre de couleur trouvées: " + algoCombat1.getChromaticNumber(res))
  }
}