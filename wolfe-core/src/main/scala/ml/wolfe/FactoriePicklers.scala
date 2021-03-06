package ml.wolfe

import java.io.{FileOutputStream, FileInputStream, PrintStream, File}

import cc.factorie.la.{SparseTensor1, DenseTensor1}

import scala.io.Source
import scala.pickling._
import scala.pickling.binary.BinaryPickle
import scala.pickling.json.JSONPickle
import scala.pickling.pickler.{PrimitivePicklers, MapPicklers, PrimitiveArrayPicklers}

/**
 * @author riedel
 */
trait FactoriePicklers {
  implicit val denseTensor1Pickler: Pickler[DenseTensor1] with Unpickler[DenseTensor1] =
    new Pickler[DenseTensor1] with Unpickler[DenseTensor1] with PrimitiveArrayPicklers {

      implicit val arrayPickler = doubleArrayPickler


      def pickle(picklee: DenseVector, builder: PBuilder) = {
        builder.beginEntry(picklee)
        builder.putField("values",b => {
          b.hintTag(implicitly[FastTypeTag[Array[Double]]])
          b.hintStaticallyElidedType()
          arrayPickler.pickle(picklee.asArray,b)
        })
        builder.endEntry()
      }

      def tag = FastTypeTag[DenseTensor1]

      def unpickle(tag: String, reader: PReader) = {
        val reader1 = reader.readField("values")
        reader1.hintTag(implicitly[FastTypeTag[Array[Double]]])
        reader1.hintStaticallyElidedType()

        val tag = reader1.beginEntry()
        val values = arrayPickler.unpickle(tag,reader1)
        reader1.endEntry()
        new DenseVector(values.asInstanceOf[Array[Double]])
      }
    }

  implicit val sparseTensor1Pickler: Pickler[SparseTensor1] with Unpickler[SparseTensor1] =
    new Pickler[SparseTensor1] with Unpickler[SparseTensor1]
      with PrimitiveArrayPicklers with PrimitivePicklers {

      implicit val arrayPickler = doubleArrayPickler
      implicit val myIntArrayPickler = intArrayPickler


      def pickle(picklee: SparseTensor1, builder: PBuilder) = {
        builder.beginEntry(picklee)
        builder.putField("dim", b => {
          b.hintTag(implicitly[FastTypeTag[Int]])
          b.hintStaticallyElidedType()
          intPickler.pickle(picklee.dim1,b)
        })
        builder.putField("indices",b => {
          b.hintTag(implicitly[FastTypeTag[Array[Int]]])
          b.hintStaticallyElidedType()
          myIntArrayPickler.pickle(picklee._indices,b)
        })
        builder.putField("values",b => {
          b.hintTag(implicitly[FastTypeTag[Array[Double]]])
          b.hintStaticallyElidedType()
          arrayPickler.pickle(picklee._values,b)
        })
        builder.endEntry()
      }

      def tag = FastTypeTag[SparseTensor1]

      def unpickle(tag: String, reader: PReader) = {
        val dim1 = {
          val reader1 = reader.readField("dim")
          reader1.hintTag(implicitly[FastTypeTag[Int]])
          reader1.hintStaticallyElidedType()
          val tag = reader1.beginEntry()
          val result = intPickler.unpickle(tag, reader1)
          reader1.endEntry()
          result.asInstanceOf[Int]
        }
        val indices = {
          val reader1 = reader.readField("indices")
          reader1.hintTag(implicitly[FastTypeTag[Array[Int]]])
          reader1.hintStaticallyElidedType()
          val tag = reader1.beginEntry()
          val result = myIntArrayPickler.unpickle(tag, reader1)
          reader1.endEntry()
          result.asInstanceOf[Array[Int]]
        }
        val values = {
          val reader1 = reader.readField("values")
          reader1.hintTag(implicitly[FastTypeTag[Array[Double]]])
          reader1.hintStaticallyElidedType()
          val tag = reader1.beginEntry()
          val result = arrayPickler.unpickle(tag, reader1)
          reader1.endEntry()
          result.asInstanceOf[Array[Double]]
        }
        val result = new SparseVector(dim1)
        result.ensureCapacity(indices.length)
        for (i <- indices.indices) result(indices(i)) = values(i)
        result
      }
    }

}

trait IndexPicklers {
  implicit def simpleIndexPickler(implicit mapPickler:Pickler[Map[Any,Int]] with Unpickler[Map[Any,Int]]): Pickler[SimpleIndex] with Unpickler[SimpleIndex] =
    new Pickler[SimpleIndex] with Unpickler[SimpleIndex] with PrimitiveArrayPicklers {

      def tag = FastTypeTag[SimpleIndex]

      def pickle(picklee: SimpleIndex, builder: PBuilder) = {
        builder.beginEntry(picklee)
        builder.putField("keys",b => {
          b.hintTag(implicitly[FastTypeTag[Map[Any,Int]]])
          b.hintStaticallyElidedType()
          mapPickler.pickle(picklee.toMap,b)
        })
        builder.endEntry()
      }

      def unpickle(tag: String, reader: PReader) = {
        val reader1 = reader.readField("keys")
        reader1.hintTag(implicitly[FastTypeTag[Map[Any,Int]]])
        reader1.hintStaticallyElidedType()

        val tag = reader1.beginEntry()
        val keys = mapPickler.unpickle(tag,reader1)
        reader1.endEntry()
        val result = new SimpleIndex
        result ++= keys.asInstanceOf[Map[Any,Int]]
        result
      }
    }

}

object WolfePicklers extends FactoriePicklers with IndexPicklers {

  def writeToFile(pkl:JSONPickle,file:File) = {
    val writer = new PrintStream(file)
    writer.println(pkl.value)
    writer.close()
  }

  def writeToFile(pkl:BinaryPickle,file:File) = {
    val writer = new FileOutputStream(file)
    writer.write(pkl.value)
    writer.close()
  }
  def loadJSON(file:File):JSONPickle = {
    val string = Source.fromFile(file).getLines().mkString("\n")
    val pkl = JSONPickle(string)
    JSONPickle(string)
  }

  def loadBinary(file:File):BinaryPickle = {
    BinaryPickle(new FileInputStream(file))
  }

  implicit val symbolPickler: Pickler[Symbol] with Unpickler[Symbol] =
    new Pickler[Symbol] with Unpickler[Symbol] with PrimitivePicklers {

      def pickle(picklee: Symbol, builder: PBuilder) = {
        builder.beginEntry(picklee)
        builder.putField("name",b => {
          b.hintTag(implicitly[FastTypeTag[String]])
          b.hintStaticallyElidedType()
          stringPickler.pickle(picklee.name,b)
        })
        builder.endEntry()
      }

      def tag = FastTypeTag[Symbol]

      def unpickle(tag: String, reader: PReader) = {
        val reader1 = reader.readField("name")
        reader1.hintTag(implicitly[FastTypeTag[String]])
        reader1.hintStaticallyElidedType()

        val tag = reader1.beginEntry()
        val name = stringPickler.unpickle(tag,reader1)
        reader1.endEntry()
        Symbol(name.toString)
      }
    }

}