import sbt._
import Keys._
import java.util.concurrent.atomic.AtomicReference
import com.yoppworks.sbt.SbtBufPlugin
import com.yoppworks.sbt.SbtBufPlugin.autoImport.Buf._

object TestPlugin extends AutoPlugin {
  override def requires = SbtBufPlugin
  override def trigger = allRequirements
  lazy val orderOfGeneratingBufImages = new AtomicReference[List[String]](List.empty[String])

  object autoImport {
    val testDelayTask = TaskKey[Unit]("delay", "Task for adding a time delay")

    val action: (State, String) => State = {
      (state,expectedOrder) =>
        val order = orderOfGeneratingBufImages.get().mkString(",")
        require(order == expectedOrder,s"Order of generating Buf images is not correct, expected $expectedOrder, but got $order")
        state
    }
    val expectedOrderOfModules: Command = Command.single("expectedOrderOfModules")(action)
  }
  import autoImport._

  override lazy val projectSettings = Seq(
    testDelayTask := {
      Thread.sleep(1000)
    },
    generateBufImage := {
      orderOfGeneratingBufImages.updateAndGet(_ :+ name.value)
      generateBufImage.value
    },
    commands ++= Seq(expectedOrderOfModules)
  )
}
