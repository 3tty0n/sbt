package sbt
package internal

import sbt.internal.util._
import BasicKeys._
import java.io.File
import sbt.protocol.EventMessage

private[sbt] final class ConsoleChannel(name: String) extends CommandChannel {
  private var askUserThread: Option[Thread] = None
  def makeAskUserThread(s: State): Thread = new Thread("ask-user-thread") {
    val history = (s get historyPath) getOrElse Some(new File(s.baseDir, ".history"))
    val prompt = (s get shellPrompt) match {
      case Some(pf) => pf(s)
      case None     => "> "
    }
    val reader = new FullReader(history, s.combinedParser, JLine.HandleCONT, true)
    override def run(): Unit = {
      // This internally handles thread interruption and returns Some("")
      val line = reader.readLine(prompt)
      line match {
        case Some(cmd) => append(Exec(cmd, Some(CommandSource(name))))
        case None      => append(Exec("exit", Some(CommandSource(name))))
      }
      askUserThread = None
    }
  }

  def run(s: State): State = s

  def publishBytes(bytes: Array[Byte]): Unit = ()

  def publishEvent(event: EventMessage): Unit =
    event match {
      case e: ConsolePromptEvent =>
        askUserThread match {
          case Some(x) => //
          case _ =>
            val x = makeAskUserThread(e.state)
            askUserThread = Some(x)
            x.start
        }
      case e: ConsoleUnpromptEvent =>
        e.lastSource match {
          case Some(src) if src.channelName != name =>
            askUserThread match {
              case Some(x) =>
                shutdown()
              case _ =>
            }
          case _ =>
        }
      case _ => //
    }

  def shutdown(): Unit =
    askUserThread match {
      case Some(x) if x.isAlive =>
        x.interrupt
        askUserThread = None
      case _ => ()
    }
}
