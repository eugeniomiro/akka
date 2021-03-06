package docs.akka.typed.coexistence

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.testkit.TestKit
//#adapter-import
// adds support for typed actors to an untyped actor system and context
import akka.actor.typed.scaladsl.adapter._
//#adapter-import
import akka.testkit.TestProbe
//#import-alias
import akka.{ actor ⇒ untyped }
//#import-alias
import org.scalatest.WordSpec
import scala.concurrent.duration._

object TypedWatchingUntypedSpec {

  //#typed
  object Typed {
    final case class Ping(replyTo: akka.actor.typed.ActorRef[Pong.type])
    sealed trait Command
    case object Pong extends Command

    val behavior: Behavior[Command] =
      Behaviors.deferred { context ⇒
        // context.spawn is an implicit extension method
        val untyped = context.actorOf(Untyped.props(), "second")

        // context.watch is an implicit extension method
        context.watch(untyped)

        // illustrating how to pass sender, toUntyped is an implicit extension method
        untyped.tell(Typed.Ping(context.self), context.self.toUntyped)

        Behaviors.immutablePartial[Command] {
          case (ctx, Pong) ⇒
            // it's not possible to get the sender, that must be sent in message
            // context.stop is an implicit extension method
            ctx.stop(untyped)
            Behaviors.same
        } onSignal {
          case (_, akka.actor.typed.Terminated(_)) ⇒
            Behaviors.stopped
        }
      }
  }
  //#typed

  //#untyped
  object Untyped {
    def props(): untyped.Props = untyped.Props(new Untyped)
  }
  class Untyped extends untyped.Actor {
    override def receive = {
      case Typed.Ping(replyTo) ⇒
        replyTo ! Typed.Pong
    }
  }
  //#untyped
}

class TypedWatchingUntypedSpec extends WordSpec {

  import TypedWatchingUntypedSpec._

  "Typed -> Untyped" must {
    "support creating, watching and messaging" in {
      //#create
      val system = untyped.ActorSystem("TypedWatchingUntyped")
      val typed = system.spawn(Typed.behavior, "Typed")
      //#create
      val probe = TestProbe()(system)
      probe.watch(typed.toUntyped)
      probe.expectTerminated(typed.toUntyped, 200.millis)
      TestKit.shutdownActorSystem(system)
    }
  }
}
