package tutorials
import neuralnetwork._
import breeze.stats.distributions._

// This code is simple test unit for converting a chain to binary tree in a greedy way
// Next step to implement Recursive Auto-Encoder Paper:
//     Semi-Supervised Recursive Autoencoders for Predicting Sentiment Distributions
object GMGTest extends Optimizable with Workspace {
  def f (x: NeuronVector, y: NeuronVector) : (Double, NeuronVector) = {
    val z = x concatenate y
    val mem = new SetOfMemorables
    val seed = ((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString
    enc.init(seed, mem).allocate(seed, mem)
    (L2Distance(enc(z, mem), z), enc.encode(z, mem))
  }
  
  val wordLength = 10
  val chainLength= 10
  val enc  = new RecursiveSimpleAE()(wordLength, 0.0, 0.01).create()
  val input = new IdentityTransform(wordLength).create()
  val output = (new SingleLayerNeuralNetwork(1) TIMES new LinearNeuralNetwork(wordLength, 1)).create()
  
  def getDynamicNeuralNetwork(x:NeuronVector): InstanceOfNeuralNetwork = {
    val tgmc = new GreedyMergeChain(f)
    tgmc.loadChain(x, wordLength)
    tgmc.greedyMerge()// tgmc.nodes is set of trees
    val node = tgmc.nodes.iterator.next
    (output TIMES new RecursiveNeuralNetwork(node.t, enc, input)).create()
  }
  
   override def getObj(w: WeightVector, distance:DistanceFunction = L2Distance) : Double = { // doesnot compute gradient or backpropagation
    val size = xData.length
    assert (size >= 1 && size == yData.length)
    var totalCost: Double = 0.0
    val dw = new WeightVector(w.length)
    
    
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)    
    totalCost = (0 until size).par.map(i => {
      val inn = getDynamicNeuralNetwork(xData(i))
      distance(inn(xData(i), initMemory(inn)), yData(i))
    }).reduce(_+_)
    
    val regCost = nn.getDerativeOfWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, dw, size)
    totalCost/size + regCost
  }
    
  override def getObjAndGrad(w:WeightVector, distance:DistanceFunction = L2Distance) : (Double, NeuronVector) = {
    val size = xData.length
    assert(size >= 1 && size == yData.length)
    var totalCost:Double = 0.0
    /*
     * Compute objective and gradients in batch mode
     * which can be run in parallel 
     */
    
    val dw = new WeightVector(w.length)
    
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)
    
    (0 until size).par.foreach(i => {
      val inn = getDynamicNeuralNetwork(xData(i))
      inn(xData(i),initMemory(inn))
    })
        
    nn.setWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, w)
    
    totalCost = (0 until size).par.map(i => {
      val inn = getDynamicNeuralNetwork(xData(i))
      val mem = initMemory(inn)
      
      val x = inn(xData(i), mem); val y = yData(i)
      val z = distance.grad(x, yData(i))
      inn.backpropagate(z, mem) // update dw !
      distance(x,y)
    }).reduce(_+_)
    /*
     * End parallel loop
     */
    // println(totalCost/size, regCost)
    
    val regCost = nn.getDerativeOfWeights(((randomGenerator.nextInt()*System.currentTimeMillis())%100000).toString, dw, size)
    (totalCost/size + regCost, dw/size)    
  }
  
  def main(args: Array[String]): Unit = {
    
    
    val numOfSamples = 100
	xData = new Array(numOfSamples)
    yData = new Array(numOfSamples)
    for (i<- 0 until numOfSamples) yield {
      xData(i) = new NeuronVector(wordLength*chainLength, new Uniform(0,1))
      yData(i) = new NeuronVector(1, new Uniform(-1,1))
    }   
    
    nn = getDynamicNeuralNetwork(xData(0)) // default neural network
    val w = getRandomWeightVector()
    
	  var time: Long = 0
	  
	  //val obj = getObj(w); println(obj)
	  
	  time = System.currentTimeMillis();
	  val (obj, grad) = getObjAndGrad(w)
	  println(System.currentTimeMillis() - time, obj, grad.data)
	  
	  // gradient checking
	  // Note: the gradient check fails, because we are to optimize stochastically
	  /*
	  time = System.currentTimeMillis();
	  val (obj2, grad2) = getApproximateObjAndGrad(w)
	  println(System.currentTimeMillis() - time, obj2, grad2.data)
	  //println((grad2 - grad).euclideanSqrNorm)
	  */
	  
	  
	  time = System.currentTimeMillis();
	  val (obj3, w2) = train(w)
	  println(System.currentTimeMillis() - time, obj3)
	
  }

}