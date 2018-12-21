// combat 2

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

  object testCombat2 extends App {
    val conf = new SparkConf()
      .setAppName("Petersen Graph (10 nodes)")
      .setMaster("local[*]")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")

    // création des sommets
    var verticesArray = new Array[(Long, node)](222);

    verticesArray(0) = (1L, new node(id = 1, name = "Solar", hp = 363, armor = 44, regen = 15, atk = 35, nbAtk = 4, damages = "3d6 + 18", death = false))
    var indice = 1
    for (i <- 0 to 1) {
      verticesArray(indice) = ((indice+1), new node(id = indice+1, name = "Planetar", hp = 229, armor = 32, regen = 10, atk = 27, nbAtk = 3, damages = "3d6 + 15", death = false))
      indice = indice + 1
    }
    for (i <- 0 to 1) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Movanic Deva", hp = 126, armor = 24, regen = 0, atk = 12, nbAtk = 1, damages = "3d6 + 7", death = false))
      indice = indice + 1
    }
    for (i <- 0 to 4) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Astral Deva", hp = 172, armor = 29, regen = 0, atk = 14, nbAtk = 1, damages = "3d8 + 42", death = false))
      indice = indice + 1
    }
    verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Dragon", hp = 449, armor = 39, regen = 0, atk = 37, nbAtk = 1, damages = "4d8 + 24", death = false))
    indice = indice + 1
    for (i <- 0 to 199) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Barbarian Orc", hp = 143, armor = 16, regen = 0, atk = 22, nbAtk = 1, damages = "3d6 + 14", death = false))
      indice = indice + 1
    }
    for (i <- 0 to 9) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Angel Slayer", hp = 112, armor = 26, regen = 0, atk = 21, nbAtk = 1, damages = "3d8 + 15", death = false))
      indice = indice + 1
    }
    verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Tiger", hp = 105, armor = 17, regen = 0, atk = 18, nbAtk = 2, damages = "2d4 + 8", death = true))
    indice = indice + 1

    var myVertices = sc.makeRDD(verticesArray)

    // DEBUG
    verticesArray.foreach(
       elem => println(elem)
    )

    // création des arêtes

    var edgesArray = new Array[Edge[String]](2120);

    var edgeInd = 0;
    for (i <- 1 to 10) {
      for (j <- 11 to 222) {
        edgesArray(edgeInd) = Edge(i,j, edgeInd.toString())
        edgeInd = edgeInd + 1
      }
    }

    // DEBUG
    edgesArray.foreach(
      elem => println(elem)
    )

    var myEdges = sc.makeRDD(edgesArray)

    var myGraph = Graph(myVertices, myEdges)
    val algoCombat1 = new Combat1()
    val res = algoCombat1.execute(myGraph, 30, sc)
    //println("\nNombre de couleur trouvées: " + algoCombat1.getChromaticNumber(res))
  }