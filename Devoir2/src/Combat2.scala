// combat 2

import org.apache.spark.SparkConf
  import org.apache.spark.SparkContext
  import org.apache.spark.graphx.{Edge, EdgeContext, Graph, _}

  import scala.util.Random
  import scala.util.matching.Regex

  //tiebreak value is generated randomly before every graph iteration
  class node(val id: Int, val name: String, val maxHp: Int = 0, val hp: Int = 0, val armor: Int, val regen: Int, val atk: Int, val target: Int = 0, val healTarget: Int = 0, val nbAtk: Int = 1, val damages: String, val heal: String = "3d8 + 20", val nbHeals : Int = 0, val priorityTarget: String = "", val portee: Int = 10, val vitesse: Int = 10, val death: Boolean = false) extends Serializable {
    override def toString: String = s"id : $id name : $name hp : $hp dead? : $death"
  }

  class edge(val distance: Int, val ally: Boolean = false) extends Serializable

  class Combat2 extends Serializable {

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

    def sendDamageValue(ctx: EdgeContext[node, edge, Int]): Unit = {
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

    def sendHealValue(ctx: EdgeContext[node, edge, Int]): Unit = {
      if (ctx.srcAttr.death == false && ctx.dstAttr.death == false) {

        if (ctx.srcAttr.target == ctx.dstAttr.id) {
          ctx.sendToDst(parseDamages(ctx.srcAttr.heal))
        }

        if (ctx.dstAttr.target == ctx.srcAttr.id) {
          ctx.sendToSrc(parseDamages(ctx.dstAttr.heal))
        }

      }
    }

    def sendNode(ctx: EdgeContext[node, edge, (String, node)]): Unit = {
      if (ctx.srcAttr.death == false && ctx.dstAttr.death == false) {
        if (ctx.attr.distance <= ctx.dstAttr.portee)
          ctx.sendToDst((ctx.dstAttr.priorityTarget, ctx.srcAttr))
        if (ctx.attr.distance <= ctx.srcAttr.portee)
          ctx.sendToSrc((ctx.srcAttr.priorityTarget, ctx.dstAttr))
      }
    }

    def sendHp(ctx: EdgeContext[node, edge, node]): Unit = {
      if (ctx.srcAttr.death == false && ctx.dstAttr.death == false && ctx.attr.ally) {
        if (ctx.srcAttr.hp/ctx.srcAttr.maxHp <= 0.3 && ctx.dstAttr.nbHeals > 0) ctx.sendToDst(ctx.srcAttr)
        if (ctx.dstAttr.hp/ctx.dstAttr.maxHp <= 0.3 && ctx.srcAttr.nbHeals > 0) ctx.sendToSrc(ctx.dstAttr)
      }
    }

    def selectHealTarget(target1: node, target2: node): node = {
      if (target1.hp < target2.hp) target1
      else target2
    }

    def selectBestTarget(target1: (String, node), target2: (String, node)): (String, node) = {
      if (target1._1 == target1._2.name) {
        target1
      } else {
        if (target2._1 == target2._2.name){
          target2
        } else {
          if (target1._2.armor < target2._2.armor) target1
          else target2
        }
      }
    }

    def sumTotalDamages(damage1: Int, damage2: Int): Int = {
      return damage1 + damage2
    }

    def takeDamages(vid: VertexId, sommet: node, totalDamages: Int): node = {
      val newHp = sommet.hp - totalDamages
      return new node(sommet.id, sommet.name, sommet.maxHp, newHp, sommet.armor, sommet.regen, sommet.atk, sommet.target, sommet.healTarget, sommet.nbAtk, sommet.damages, death = newHp <= 0)
    }

    def takeHeal(vid: VertexId, sommet: node, totalHeal: Int): node = {
      val newHp = Math.min(sommet.maxHp, sommet.hp + totalHeal)
      return new node(sommet.id, sommet.name, sommet.maxHp, newHp, sommet.armor, sommet.regen, sommet.atk, sommet.target, sommet.healTarget, sommet.nbAtk, sommet.damages, death = newHp <= 0)
    }

    def setTarget(vid: VertexId, sommet: node, target: node): node = {
      return new node(sommet.id, sommet.name, sommet.maxHp, sommet.hp, sommet.armor, sommet.regen, sommet.atk, target.id, sommet.healTarget, sommet.nbAtk, sommet.damages, sommet.heal, sommet.nbHeals, sommet.priorityTarget, sommet.portee, sommet.vitesse, sommet.death)
    }

    def setHealTarget(vid: VertexId, sommet: node, target: node): node = {
      return new node(sommet.id, sommet.name, sommet.maxHp, sommet.hp, sommet.armor, sommet.regen, sommet.atk, sommet.target, target.id, sommet.nbAtk, sommet.damages, sommet.heal, sommet.nbHeals, sommet.priorityTarget, sommet.portee, sommet.vitesse, sommet.death)
    }

    def execute(g: Graph[node, edge], maxIterations: Int, sc: SparkContext): Graph[node, edge] = {
      var myGraph = g
      var counter = 0
      val fields = new TripletFields(true, true, false) //join strategy

      def loop1: Unit = {
        while (true) {

          println("TOUR NUMERO : " + (counter + 1))
          counter += 1
          if (counter == maxIterations) return

          val messagesTarget = myGraph.aggregateMessages[(String, node)](
            sendNode,
            selectBestTarget,
            fields //use an optimized join strategy (we don't need the edge attribute)
          )
          if (messagesTarget.isEmpty()) return

          myGraph = myGraph.joinVertices(messagesTarget)(
            (vid, sommet, target) => setTarget(vid, sommet, target._2))

          myGraph = myGraph.mapVertices(
            (vid, sommet) => new node(sommet.id, sommet.name, sommet.maxHp, sommet.hp + sommet.regen, sommet.armor, sommet.regen, sommet.atk, sommet.target, sommet.healTarget, sommet.nbAtk, sommet.damages, sommet.heal, sommet.nbHeals, sommet.priorityTarget, sommet.portee, sommet.vitesse, sommet.death))

          myGraph = myGraph.mapTriplets[edge](
            (e: EdgeTriplet[node, edge]) => {
              var newDist = e.attr.distance
              if (e.srcAttr.target == 0 && e.attr.distance >= e.srcAttr.portee) {
                newDist -= e.srcAttr.vitesse
              }
              if (e.dstAttr.target == 0 && e.attr.distance >= e.dstAttr.portee) {
                newDist -= e.dstAttr.vitesse
              }
              new edge(newDist, e.attr.ally)
            })

          val messages = myGraph.aggregateMessages[Int](
            sendDamageValue,
            sumTotalDamages,
            fields //use an optimized join strategy (we don't need the edge attribute)
          )

          //Ignorez : Code de debug
          //println("Attack Target")
          //var printedMessages = messagesTarget.collect()
          //printedMessages = printedMessages.sortBy(_._1)
          //printedMessages.foreach(
          // elem => println(elem._2)
          //)

          myGraph = myGraph.joinVertices(messages)(
            (vid, sommet, totalDamages) => takeDamages(vid, sommet, totalDamages))

          // Regen
          myGraph = myGraph.mapVertices(
            (vid, sommet) => new node(sommet.id, sommet.name, sommet.maxHp, Math.min(sommet.maxHp, sommet.hp + sommet.regen), sommet.armor, sommet.regen, sommet.atk, 0, sommet.healTarget, sommet.nbAtk, sommet.damages, sommet.heal, sommet.nbHeals, sommet.priorityTarget, sommet.portee, sommet.vitesse, sommet.death))

          val messagesHealTarget = myGraph.aggregateMessages[node](
            sendHp,
            selectHealTarget,
            fields //use an optimized join strategy (we don't need the edge attribute)
          )

          myGraph = myGraph.joinVertices(messagesHealTarget)(
            (vid, sommet, target) => setHealTarget(vid, sommet, target))

          myGraph = myGraph.mapVertices(
            (vid, sommet) => new node(sommet.id, sommet.name, sommet.maxHp, sommet.hp, sommet.armor, sommet.regen, sommet.atk, sommet.target, sommet.healTarget, sommet.nbAtk, sommet.damages, sommet.heal, Math.max(0, sommet.nbHeals - 1),sommet.priorityTarget, sommet.portee, sommet.vitesse, sommet.death))

          //Ignorez : Code de debug
          //println("Heal Target")
          //var printedMessages1 = messagesHealTarget.collect()
          //printedMessages1 = printedMessages1.sortBy(_._1)
          //printedMessages1.foreach(
          // elem => println(elem._2)
          //)

          val messagesHeal = myGraph.aggregateMessages[Int](
            sendHealValue,
            sumTotalDamages,
            fields //use an optimized join strategy (we don't need the edge attribute)
          )

          myGraph = myGraph.joinVertices(messagesHeal)(
            (vid, sommet, totalHeal) => takeHeal(vid, sommet, totalHeal))

          myGraph = myGraph.mapVertices(
            (vid, sommet) => new node(sommet.id, sommet.name, sommet.maxHp, sommet.hp, sommet.armor, sommet.regen, sommet.atk, 0, 0, sommet.nbAtk, sommet.damages, sommet.heal, sommet.nbHeals, sommet.priorityTarget, sommet.portee, sommet.vitesse, sommet.death))


          //Ignorez : Code de debug
          var printedGraph = myGraph.vertices.collect()
          printedGraph = printedGraph.sortBy(_._1)
          printedGraph.foreach(
            elem => if (!elem._2.death) println(elem._2)
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

    verticesArray(0) = (1L, new node(id = 1, name = "Solar", maxHp = 363, hp = 363, armor = 44, regen = 15, atk = 35, nbAtk = 4, damages = "3d6 + 18", nbHeals = 3, priorityTarget = "Dragon", portee = 30,  death = false))
    var indice = 1
    for (i <- 0 to 1) {
      verticesArray(indice) = ((indice+1), new node(id = indice+1, name = "Planetar", maxHp = 229, hp = 229, armor = 32, regen = 10, atk = 27, nbAtk = 3, priorityTarget = "Dragon", damages = "3d6 + 15", portee = 30, death = false))
      indice = indice + 1
    }
    for (i <- 0 to 1) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Movanic Deva", maxHp = 126, hp = 126, armor = 24, regen = 0, atk = 12, nbAtk = 1, damages = "3d6 + 7", priorityTarget = "Angel Slayer", portee = 30, death = false))
      indice = indice + 1
    }
    for (i <- 0 to 4) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Astral Deva", maxHp = 172, hp = 172, armor = 29, regen = 0, atk = 14, nbAtk = 1, damages = "3d8 + 42", priorityTarget = "Barbarian Orc", portee = 30, death = false))
      indice = indice + 1
    }
    verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Dragon", maxHp = 449, hp = 449, armor = 39, regen = 0, atk = 37, nbAtk = 1, damages = "4d8 + 24", priorityTarget = "Solar", portee = 30, death = false))
    indice = indice + 1
    for (i <- 0 to 199) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Barbarian Orc", maxHp = 42, hp = 42, armor = 15, regen = 0, atk = 11, nbAtk = 1, damages = "1d12 + 10", vitesse = 2, death = false))
      indice = indice + 1
    }
    for (i <- 0 to 9) {
      verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Angel Slayer", maxHp = 112, hp = 112, armor = 26, regen = 0, atk = 27, nbAtk = 3, damages = "1d8 + 14", portee = 10, death = false))
      indice = indice + 1
    }
    verticesArray(indice) = (indice+1, new node(id = indice+1, name = "Tiger", maxHp = 105, hp = 105, armor = 17, regen = 0, atk = 18, nbAtk = 2, damages = "2d4 + 8", death = true))
    indice = indice + 1

    var myVertices = sc.makeRDD(verticesArray)

    // DEBUG
    //verticesArray.foreach(
    //   elem => println(elem)
    //)

    // création des arêtes

    var edgesArray = new Array[Edge[edge]](2129);


    var edgeInd = 0;
    val rnd = new Random
    val minDist = 20;
    val maxDist = 80;
    for (i <- 1 to 10) {
      for (j <- 11 to 222) {
        edgesArray(edgeInd) = Edge(i,j, new edge(minDist + rnd.nextInt(maxDist - minDist + 1)))
        edgeInd = edgeInd + 1
      }
    }
    for (i <- 2 to 10) {
      edgesArray(edgeInd) = Edge(1,i, new edge(minDist + rnd.nextInt(maxDist - minDist + 1), true))
      edgeInd = edgeInd + 1
    }


    // DEBUG
    //edgesArray.foreach(
    //  elem => println(elem.srcId + "->" + elem.dstId + " : " + elem.attr.distance)
    //)

    var myEdges = sc.makeRDD(edgesArray)

    var myGraph = Graph(myVertices, myEdges)
    val algoCombat2 = new Combat2()
    val res = algoCombat2.execute(myGraph, 50, sc)
    //println("\nNombre de couleur trouvées: " + algoCombat1.getChromaticNumber(res))
  }