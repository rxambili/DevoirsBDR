// Combat1


package Combat1 {

  import org.apache.spark.SparkConf
  import org.apache.spark.SparkContext
  import org.apache.spark.graphx.{Edge, EdgeContext, Graph, _}

  import scala.util.Random
  import scala.util.matching.Regex

  //tiebreak value is generated randomly before every graph iteration
  class node(val id: Int, val name: String, val hp: Int = 1, val armor: Int, val regen: Int, val atk: Int, val target: Int = 0, val nbAtk: Int = 1, val damages: String, val death: Boolean = false) extends Serializable {
    override def toString: String = s"id : $id name : $name hp : $hp dead? : $death"
  }

  class Combat1 extends Serializable {

    def parseDamages(damages : String): Int = {
      val pattern = "([0-9]+)d([0-9]+) . ([0-9]+)".r
      val pattern(nbDes, nbFaces, constant) = damages
      return getJetDes(nbFaces.toInt, nbDes.toInt) + constant.toInt
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

    def sendDamageValue(ctx: EdgeContext[node, String, Int]): Unit = {
      if (ctx.srcAttr.death == false && ctx.dstAttr.death == false) {

        if (ctx.srcAttr.target == ctx.dstAttr.id) {
          var damageToDst = 0
          for (i <- 1 to ctx.srcAttr.nbAtk){
            if (getJetDes(20, 1) + ctx.srcAttr.atk >= ctx.dstAttr.armor) {
              damageToDst += parseDamages(ctx.srcAttr.damages)
            }
          }
          ctx.sendToDst(damageToDst)
        }

        if (ctx.dstAttr.target == ctx.srcAttr.id) {
          var damageToSrc = 0;
          for (i <- 1 to ctx.dstAttr.nbAtk) {
            if (getJetDes(20, 1) + ctx.dstAttr.atk >= ctx.srcAttr.armor) {
              damageToSrc += parseDamages(ctx.dstAttr.damages)
            }
          }
          ctx.sendToSrc(damageToSrc)
        }

      }
    }

    def sendNode(ctx: EdgeContext[node, String, node]): Unit = {
      if (ctx.srcAttr.death == false && ctx.dstAttr.death == false) {
        ctx.sendToDst(ctx.srcAttr)
        ctx.sendToSrc(ctx.dstAttr)
      }
    }

    def selectBestTarget(target1: node, target2: node): node = {
      if (target1.atk > target2.atk) target1
      else target2
    }

    def sumTotalDamages(damage1: Int, damage2: Int): Int = {
      return damage1 + damage2
    }

    def takeDamages(vid: VertexId, sommet: node, totalDamages: Int): node = {
      val newHp = sommet.hp - totalDamages
      return new node(sommet.id, sommet.name, newHp, sommet.armor, sommet.regen, sommet.atk, sommet.target, sommet.nbAtk, sommet.damages, death = newHp <= 0)
    }

    def setTarget(vid: VertexId, sommet: node, target: node): node = {
      return new node(sommet.id, sommet.name, sommet.hp, sommet.armor, sommet.regen, sommet.atk, target.id, sommet.nbAtk, sommet.damages, sommet.death)
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

          val messagesTarget = myGraph.aggregateMessages[node](
            sendNode,
            selectBestTarget,
            fields //use an optimized join strategy (we don't need the edge attribute)
          )
          if (messagesTarget.isEmpty()) return

          myGraph = myGraph.joinVertices(messagesTarget)(
            (vid, sommet, target) => setTarget(vid, sommet, target))

          myGraph = myGraph.mapVertices(
            (vid, sommet) => new node(sommet.id, sommet.name, sommet.hp + sommet.regen, sommet.armor, sommet.regen, sommet.atk, sommet.target, sommet.nbAtk, sommet.damages, sommet.death))


          val messages = myGraph.aggregateMessages[Int](
            sendDamageValue,
            sumTotalDamages,
            fields //use an optimized join strategy (we don't need the edge attribute)
          )



          //Ignorez : Code de debug
          //var printedMessages = messagesTarget.collect()
          //printedMessages = printedMessages.sortBy(_._1)
          //printedMessages.foreach(
          //  elem => println(elem._2)
          //)

          myGraph = myGraph.joinVertices(messages)(
            (vid, sommet, totalDamages) => takeDamages(vid, sommet, totalDamages))

          // Regen
          myGraph = myGraph.mapVertices(
            (vid, sommet) => new node(sommet.id, sommet.name, sommet.hp + sommet.regen, sommet.armor, sommet.regen, sommet.atk, sommet.target, sommet.nbAtk, sommet.damages, sommet.death))

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
      (1L, new node(id = 1, name = "Solar", hp = 363, armor = 44, regen = 15, atk = 35, nbAtk = 4, damages = "3d6 + 18", death = false)),
      (2L, new node(id = 2, name = "Worg Rider 1", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (3L, new node(id = 3, name = "Worg Rider 2", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (4L, new node(id = 4, name = "Worg Rider 3", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (5L, new node(id = 5, name = "Worg Rider 4", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (6L, new node(id = 6, name = "Worg Rider 5", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (7L, new node(id = 7, name = "Worg Rider 6", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (8L, new node(id = 8, name = "Worg Rider 7", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (9L, new node(id = 9, name = "Worg Rider 8", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (10L, new node(id = 10, name = "Worg Rider 9", hp = 13, armor = 18, regen = 0, atk = 6, damages = "1d8 + 2", death = false)),
      (11L, new node(id = 11, name = "Brutal Warlord", hp = 141, armor = 27, regen = 0, atk = 20, damages = "1d8 + 10", death = false)),
      (12L, new node(id = 12, name = "Barbare Orc 1", hp = 142, armor = 17, regen = 0, atk = 19, damages = "1d8 + 10", death = false)),
      (13L, new node(id = 13, name = "Barbare Orc 2", hp = 142, armor = 17, regen = 0, atk = 19, damages = "1d8 + 10", death = false)),
      (14L, new node(id = 14, name = "Barbare Orc 3", hp = 142, armor = 17, regen = 0, atk = 19, damages = "1d8 + 10", death = false)),
      (15L, new node(id = 15, name = "Barbare Orc 4", hp = 142, armor = 17, regen = 0, atk = 19, damages = "1d8 + 10", death = false))))

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
    val res = algoCombat1.execute(myGraph, 30, sc)
    //println("\nNombre de couleur trouvées: " + algoCombat1.getChromaticNumber(res))
  }
}