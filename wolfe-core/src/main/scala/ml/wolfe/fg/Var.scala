package ml.wolfe.fg

import ml.wolfe.MoreArrayOps._
import ml.wolfe.{Wolfe, FactorieVector, FactorGraph}
import ml.wolfe.FactorGraph.{Node, Edge}
import scalaxy.loops._
import scala.util.Random
import cc.factorie.la.DenseTensor1
import math._


/**
 * @author Sebastian Riedel
 */
trait Var {

  import Wolfe.notSupported

  def asDiscrete = this.asInstanceOf[DiscreteVar]
  def asVector = this.asInstanceOf[VectorVar]
  def asTuple = this.asInstanceOf[TupleVar]
  def setup(){}
  def initializeToNegInfinity():Unit = notSupported
  def initializeRandomly(eps:Double):Unit = notSupported
  def updateN2F(edge: FactorGraph.Edge):Unit = notSupported
  def updateDualN2F(edge: FactorGraph.Edge, stepSize:Double):Unit = notSupported
  def fixMapSetting(node: FactorGraph.Node, overwrite:Boolean = false):Unit = notSupported
  def setToArgmax():Unit = notSupported
  def deterministicN2F(edge: FactorGraph.Edge):Unit = notSupported
  def updateMaxMarginalBelief(node: FactorGraph.Node):Unit = notSupported
  def updateMarginalBelief(node: FactorGraph.Node):Unit = notSupported
  def updateAverageBelief(node: FactorGraph.Node):Unit = notSupported
  def entropy():Double = notSupported

}

final class DiscreteVar(var dim: Int) extends Var {
  /* node belief */
  var b = Array.ofDim[Double](dim)

  /* external message for this node. Will usually not be updated during inference */
  var in = Array.ofDim[Double](dim)

  /* the domain of values. By default this corresponds to [0,dim) but can be a subset if observations are given */
  var domain: Array[Int] = _

  /* indicates that variable is in a certain state */
  var setting: Int = 0

  /* indicates the value corresponding to the setting of the node */
  var value: Int = 0

  override def entropy() = {
    var result = 0.0
    for (i <- (0 until b.length).optimized) {
      result -= math.exp(b(i)) * b(i) //messages are in log space
    }
    result
  }

  override def initializeToNegInfinity() = {
    fill(b, Double.NegativeInfinity)
  }


  override def initializeRandomly(eps:Double) = {
    for (i <- b.indices) b(i) = Random.nextGaussian() * eps
  }

  override def setup() {
    if (domain == null || domain.length != dim) domain = Array.range(0, dim)
    if (b == null || b.length != dim) b = Array.ofDim[Double](dim)
    if (in == null || in.length != dim) in = Array.ofDim[Double](dim)
  }

  override def updateN2F(edge: Edge) = {
    val node = edge.n
    val m = edge.msgs.asDiscrete
    System.arraycopy(in, 0, m.n2f, 0, m.n2f.length)
    for (i <- (0 until dim).optimized) {
      for (e <- (0 until node.edges.length).optimized; if e != edge.indexInNode)
        m.n2f(i) += node.edges(e).msgs.asDiscrete.f2n(i)
    }
  }

  override def updateDualN2F(edge:Edge, stepSize:Double) = {
    val m = edge.msgs.asDiscrete
    for(i <- (0 until m.n2f.size).optimized)
      m.n2f(i) = m.n2f(i) - stepSize * (math.exp(m.f2n(i)) - math.exp(b(i)))
  }

  var fixedSetting = false
  override def fixMapSetting(node:Node, overwrite:Boolean = false):Unit = {
    if(! fixedSetting || overwrite) {
      var maxScore = Double.NegativeInfinity
      for (i <- (0 until dim).optimized) {
        var score = in(i)
        for (e <- (0 until node.edges.length).optimized)
          score += node.edges(e).msgs.asDiscrete.f2n(i)
        if (score > maxScore) {
          maxScore = score
          setting = i
        }
        fixedSetting = true
      }
    }
  }

  override def setToArgmax():Unit = {
    if(! fixedSetting)
      setting = ml.wolfe.MoreArrayOps.maxIndex(b)
  }

  override def deterministicN2F(edge: Edge) = {
    val m = edge.msgs.asDiscrete
    fill(m.n2f, Double.NegativeInfinity)
    m.n2f(setting) = 0
  }

  override def updateMaxMarginalBelief(node: Node) = {
    System.arraycopy(in, 0, b, 0, b.length)
    for (e <- 0 until node.edges.length) {
      for (i <- 0 until dim)
        b(i) += node.edges(e).msgs.asDiscrete.f2n(i)
      maxNormalize(b)
    }
  }
  override def updateMarginalBelief(node: Node) = {
    System.arraycopy(in, 0, b, 0, b.length)
    for (e <- 0 until node.edges.length) {
      for (i <- 0 until dim)
        b(i) += node.edges(e).msgs.asDiscrete.f2n(i)
      val logZ = math.log(b.view.map(exp).sum)
      -=(b,logZ)
    }
  }

  override def updateAverageBelief(node: Node) = {
    System.arraycopy(in, 0, b, 0, b.length)
    for (e <- 0 until node.edges.length)
      for (i <- 0 until dim)
        b(i) += node.edges(e).msgs.asDiscrete.f2n(i)
    for(i <- 0 until dim) b(i) = b(i) / node.edges.length
  }


}

final class VectorVar(val dim:Int) extends Var {
  var b:FactorieVector = new DenseTensor1(dim)
  var setting:FactorieVector = null


  override def updateN2F(edge: Edge) = {
    edge.msgs.asVector.n2f = b //todo: copy instead?
  }

  override def initializeRandomly(eps:Double) = {
    for (i <- 0 until dim) b(i) = Random.nextGaussian() * eps
  }
  override def initializeToNegInfinity() = {
    b := Double.NegativeInfinity
  }
}


