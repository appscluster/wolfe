//package ml.wolfe.fg20
//
//
//// ----------- Factor Graph ---------------
//class BPMsgs(dim: Int) {
//  val n2f = Array.ofDim[Double](dim)
//  val f2n = Array.ofDim[Double](dim)
//}
//
//class BPFG(problem: Problem) /*extends FG*/ {
//  type NodeContent = Array[Double]
//  type Msgs = BPMsgs
//  type FactorContent = Null
//
//  val discNodes = problem.discVars.map(v =>
//    v -> new Node[DiscVar[Any], NodeContent, Msgs](v, Array.ofDim[Double](v.dom.size))
//  ).toMap
//
//  def createFactor(pot:Potential) = {
//    val factor = new Factor[Msgs, FactorContent](pot, null)
//    def addEdge[VarType<:DiscVar[_]](node:Node[VarType, NodeContent, Msgs]): Edge[Msgs] = {
//      val msgs = new BPMsgs(node.variable.dom.size)
//      val edge =  new Edge[Msgs](node, factor, msgs)
//      node.edgesBuffer = edge :: node.edgesBuffer
//      edge
//    }
//    val discEdges = pot.discVars.map(v => addEdge(discNodes(v)))
//    factor.discEdges = discEdges
//    factor
//  }
//
//  val factors = problem.pots.map(createFactor)
//  val discEdges = factors.flatMap(_.discEdges)
//  discNodes.foreach(_._2.build())
//}
//
//
//// ------------- Inference ---------------
//object MaxProduct {
//
//  type Direction = Boolean
//  val N2F = true
//  val F2N = false
//
//  def apply(fg: BPFG): MAPResult = {
//    def schedule:Seq[(Edge[BPMsgs], Direction)] = ???
//
//    for ((edge, direction) <- schedule) direction match {
//      case N2F =>
//        for (i <- edge.msgs.n2f.length) {
//          edge.msgs.n2f(i) = (for (e <- edge.node.edges if e != edge) yield e.msgs.f2n(i)).sum
//        }
//      case F2N =>
//        for (i <- edge.msgs.f2n.length) {
//          edge.msgs.n2f(i) = (for (e <- edge.node.edges if e != edge) yield e.msgs(i)).sum
//        }
//    }
//    ???
//  }
//
//  def updateN2F(edge: Edge[BPMsgs]) = {
//
//  }
//}
