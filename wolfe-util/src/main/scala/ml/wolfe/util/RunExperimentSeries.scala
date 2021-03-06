package ml.wolfe.util

import java.io.{File, FileWriter}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import RichCollections._

/**
 * @author rockt
 */
object RunExperimentSeries {
  type Series = Map[String, Seq[Any]]
  type Confs = Seq[Map[String, Any]]

  def apply(series: Series, threads: Int = 1, confPath: String = "wolfe-apps/conf/mf.conf")(body: String => Any): Unit = {
    val confSeq: Confs = series.map { case (key, value) =>
      (value zip (Stream continually Seq(key)).flatten).map(_.swap)
    }.foldLeft(Seq[Any](Nil)) { case (seqs, seq) => (seqs flatCross seq).toSeq }
                         .map { case seq: Seq[_] => seq.asInstanceOf[Seq[(String, Any)]].toMap }

    val confSeqWithCollectFields = if (Conf.hasPath("logFile")) {
      val fields = confSeq.head.keys.toList
      //write header to collectResults
      val fileWriter = new FileWriter(Conf.getString("logFile"), true)
      fileWriter.write("\n//" + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()) + "\t" + confPath + "\n")
      fileWriter.write("MAP\twMAP\t" + fields.mkString("", "\t", "\n"))
      fileWriter.close()

      confSeq.map(_ ++ Map(
        "logFields" -> fields
      ))
    } else confSeq

    runConfs(confSeqWithCollectFields, threads, confPath)(body)
  }

  private def runConfs(confs: Confs, threads: Int = 1, confPath: String = "wolfe-apps/conf/mf.conf")(body: String => Any): Unit = {
    import scala.concurrent.duration._
    import scala.concurrent.{Await, Future, blocking, future}

    implicit val context = new ExecutionContext {
      val threadPool = Executors.newFixedThreadPool(threads)

      def execute(runnable: Runnable) {
        threadPool.submit(runnable)
      }

      def reportFailure(t: Throwable) {}
    }

    val configsFut: Traversable[Future[Any]] = confs.map {
      c => Future {
        blocking {
          val newConfPath = File.createTempFile(System.nanoTime().toString, null).getAbsolutePath
          OverrideConfig(c, newConfPath, confPath)
          body(newConfPath)
        }
      }
    }

    //waiting until experiments are finished; not longer than a year ;)
    val resultsFut: Future[Traversable[Any]] = Future.sequence(configsFut)
    import scala.language.postfixOps
    Await.result(resultsFut, 365 days)
  }
}