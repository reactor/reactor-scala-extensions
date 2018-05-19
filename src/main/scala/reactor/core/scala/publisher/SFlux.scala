package reactor.core.scala.publisher

import java.lang.{Boolean => JBoolean, Long => JLong}
import java.util.concurrent.Callable
import java.util.function.{BiFunction, Function, Supplier}
import java.util.{List => JList}

import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.FluxSink.OverflowStrategy
import reactor.core.publisher.{FluxSink, SynchronousSink, Flux => JFlux}
import reactor.core.scheduler.{Scheduler, Schedulers}
import reactor.util.concurrent.Queues.{SMALL_BUFFER_SIZE, XS_BUFFER_SIZE}
import reactor.util.function.{Tuple2, Tuple3, Tuple4, Tuple5, Tuple6}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.duration.Duration.Infinite
import scala.language.postfixOps
import scala.reflect.ClassTag

trait SFlux[T] extends SFluxLike[T, SFlux] with Publisher[T] {
  self =>

  final def all(predicate: T => Boolean): SMono[Boolean] = new ReactiveSMono[Boolean](coreFlux.all(predicate).map((b: JBoolean) => Boolean2boolean(b)))

  final def any(predicate: T => Boolean): SMono[Boolean] = new ReactiveSMono[Boolean](coreFlux.any(predicate).map((b: JBoolean) => Boolean2boolean(b)))

  final def as[P](transformer: Flux[T] => P): P = coreFlux.as(transformer)

  final def blockFirst(timeout: Duration = Duration.Inf): Option[T] = timeout match {
    case _: Infinite => Option(coreFlux.blockFirst())
    case t => Option(coreFlux.blockFirst(t))
  }

  final def blockLast(timeout: Duration = Duration.Inf): Option[T] = timeout match {
    case _: Infinite => Option(coreFlux.blockLast())
    case t => Option(coreFlux.blockLast(t))
  }

  final def buffer[C >: mutable.Buffer[T]](maxSize: Int = Int.MaxValue, bufferSupplier: () => C = () => mutable.ListBuffer.empty[T] ): SFlux[Seq[T]] = {
    new ReactiveSFlux[Seq[T]](coreFlux.buffer(maxSize, new Supplier[JList[T]] {
      override def get(): JList[T] = {
        bufferSupplier().asInstanceOf[mutable.Buffer[T]].asJava
      }
    }).map((l: JList[T]) => l.asScala))
  }

  final def bufferTimeSpan(timespan: Duration, timer: Scheduler = Schedulers.parallel())(timeshift: Duration = timespan): SFlux[Seq[T]] =
    new ReactiveSFlux[Seq[T]](coreFlux.buffer(timespan, timeshift, timer).map((l: JList[T]) => l.asScala))

  final def bufferPublisher(other: Publisher[_]): SFlux[Seq[T]] = new ReactiveSFlux[Seq[T]](coreFlux.buffer(other).map((l: JList[T]) => l.asScala))

  final def bufferTimeout[C >: mutable.Buffer[T]](maxSize: Int, timespan: Duration, timer: Scheduler = Schedulers.parallel(), bufferSupplier: () => C = () => mutable.ListBuffer.empty[T] ): SFlux[Seq[T]] = {
    new ReactiveSFlux[Seq[T]](coreFlux.bufferTimeout(maxSize, timespan, timer, new Supplier[JList[T]] {
      override def get(): JList[T] = {
        bufferSupplier().asInstanceOf[mutable.Buffer[T]].asJava
      }
    }).map((l: JList[T]) => l.asScala))
  }

  final def bufferUntil(predicate: T => Boolean, cutBefore: Boolean = false): SFlux[Seq[T]] = new ReactiveSFlux[Seq[T]](coreFlux.bufferUntil(predicate, cutBefore).map((l: JList[T]) => l.asScala))

  final def bufferWhen[U, V, C >: mutable.Buffer[T]](bucketOpening: Publisher[U], closeSelector: U => Publisher[V], bufferSupplier: () => C = () => mutable.ListBuffer.empty[T]): SFlux[Seq[T]] =
    new ReactiveSFlux[Seq[T]](coreFlux.bufferWhen(bucketOpening, closeSelector, new Supplier[JList[T]] {
      override def get(): JList[T] = {
        bufferSupplier().asInstanceOf[mutable.Buffer[T]].asJava
      }
    }).map((l: JList[T]) => l.asScala))

  final def bufferWhile(predicate: T => Boolean): SFlux[Seq[T]] = new ReactiveSFlux[Seq[T]](coreFlux.bufferWhile(predicate).map((l: JList[T]) => l.asScala))

  private[publisher] def coreFlux: JFlux[T]

  final def delayElements(delay: Duration, timer: Scheduler = Schedulers.parallel()): SFlux[T] = new ReactiveSFlux[T](coreFlux.delayElements(delay, timer))

  final def delaySubscription(delay: Duration, timer: Scheduler = Schedulers.parallel()): SFlux[T] = new ReactiveSFlux[T](coreFlux.delaySubscription(delay, timer))

  final def delaySubscription[U](subscriptionDelay: Publisher[U]): SFlux[T] = new ReactiveSFlux[T](coreFlux.delaySubscription(subscriptionDelay))

  def doOnRequest(f: Long => Unit): SFlux[T] = new ReactiveSFlux[T](coreFlux.doOnRequest(f))

  final def elapsed(scheduler: Scheduler = Schedulers.parallel()): SFlux[(Long, T)] = new ReactiveSFlux[(Long, T)](coreFlux.elapsed(scheduler).map(new Function[Tuple2[JLong, T], (Long, T)] {
    override def apply(t: Tuple2[JLong, T]): (Long, T) = (Long2long(t.getT1), t.getT2)
  }))

  final def index(): SFlux[(Long, T)] = index[(Long, T)]((x, y) => (x, y))

  final def index[I](indexMapper: (Long, T) => I): SFlux[I] = new ReactiveSFlux[I](coreFlux.index[I](new BiFunction[JLong, T, I] {
    override def apply(t: JLong, u: T) = indexMapper(Long2long(t), u)
  }))

  override def subscribe(s: Subscriber[_ >: T]): Unit = coreFlux.subscribe(s)

  def take(n: Long): SFlux[T] = new ReactiveSFlux[T](coreFlux.take(n))
}

object SFlux {
  def apply[T](elements: T*): SFlux[T] = SFlux.fromIterable(elements)

  def combineLatest[T1, T2](p1: Publisher[T1], p2: Publisher[T2]): SFlux[(T1, T2)] =
    new ReactiveSFlux[(T1, T2)](JFlux.combineLatest(p1, p2, (t1: T1, t2: T2) => (t1, t2)))

  def combineLatest[T](sources: Publisher[T]*): SFlux[Seq[T]] =
    new ReactiveSFlux[Seq[T]](JFlux.combineLatest[T, Seq[T]]
      (sources, (arr: Array[AnyRef]) => arr.toSeq map (_.asInstanceOf[T])))

  def combineLatestMap[T1, T2, V](p1: Publisher[T1], p2: Publisher[T2], mapper: (T1, T2) => V): SFlux[V] =
    new ReactiveSFlux[V](JFlux.combineLatest(p1, p2, mapper))

  def combineLatestMap[T: ClassTag, V](mapper: Array[T] => V, sources: Publisher[T]*): SFlux[V] = {
    val f = (arr: Array[AnyRef]) => {
      val x: Seq[T] = arr.toSeq map (_.asInstanceOf[T])
      mapper(x.toArray)
    }
    new ReactiveSFlux[V](JFlux.combineLatest(sources, f))
  }

  def concat[T](sources: Publisher[T]*): SFlux[T] = new ReactiveSFlux(JFlux.concat(sources))

  def concatDelayError[T](sources: Publisher[T]*): SFlux[T] = new ReactiveSFlux[T](JFlux.concatDelayError(sources: _*))

  def create[T](emitter: FluxSink[T] => Unit, backPressure: FluxSink.OverflowStrategy = OverflowStrategy.BUFFER): SFlux[T] = new ReactiveSFlux[T](JFlux.create(emitter, backPressure))

  def defer[T](f: => SFlux[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.defer(() => f))

  def empty[T]: SFlux[T] = new ReactiveSFlux(JFlux.empty[T]())

  def error[T](e: Throwable, whenRequested: Boolean = false): SFlux[T] = new ReactiveSFlux[T](JFlux.error(e, whenRequested))

  def firstEmitter[I](sources: Publisher[_ <: I]*): SFlux[I] = new ReactiveSFlux[I](JFlux.first[I](sources: _*))

  def fromArray[T <: AnyRef](array: Array[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromArray[T](array))

  def fromIterable[T](iterable: Iterable[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromIterable(iterable.asJava))

  def fromPublisher[T](source: Publisher[_ <: T]): SFlux[T] = new ReactiveSFlux[T](JFlux.from(source))

  def fromStream[T](streamSupplier: () => Stream[T]): SFlux[T] = new ReactiveSFlux[T](JFlux.fromStream[T](streamSupplier()))

  def generate[T, S](generator: (S, SynchronousSink[T]) => S,
                     stateSupplier: Option[Callable[S]] = None,
                     stateConsumer: Option[S => Unit] = None): SFlux[T] = new ReactiveSFlux[T](
    JFlux.generate[T, S](stateSupplier.orNull[Callable[S]], generator, stateConsumer.orNull[S => Unit])
  )

  def interval(period: Duration, scheduler: Scheduler = Schedulers.parallel())(implicit delay: Duration = period): SFlux[Long] =
    new ReactiveSFlux[Long](JFlux.interval(delay, period).map((l: JLong) => Long2long(l)))

  def just[T](data: T*): SFlux[T] = apply[T](data: _*)

  def mergeSequentialPublisher[T](sources: Publisher[Publisher[T]], delayError: Boolean = false, maxConcurrency: Int = SMALL_BUFFER_SIZE, prefetch: Int = XS_BUFFER_SIZE): SFlux[T] =
    new ReactiveSFlux[T](
      if (delayError) JFlux.mergeSequentialDelayError[T](sources, maxConcurrency, prefetch)
      else JFlux.mergeSequential[T](sources, maxConcurrency, prefetch)
    )

  def mergeSequential[I](sources: Seq[Publisher[_ <: I]], delayError: Boolean = false, prefetch: Int = XS_BUFFER_SIZE): SFlux[I] =
    new ReactiveSFlux[I](
      if (delayError) JFlux.mergeSequentialDelayError(prefetch, sources: _*)
      else JFlux.mergeSequential(prefetch, sources: _*)
    )

  def mergeSequentialIterable[I](sources: Iterable[Publisher[_ <: I]], delayError: Boolean = false, maxConcurrency: Int = SMALL_BUFFER_SIZE, prefetch: Int = XS_BUFFER_SIZE) =
    new ReactiveSFlux[I](
      if (delayError) JFlux.mergeSequentialDelayError[I](sources, maxConcurrency, prefetch)
      else JFlux.mergeSequential[I](sources, maxConcurrency, prefetch))

  def never[T](): SFlux[T] = new ReactiveSFlux[T](JFlux.never[T]())

  def push[T](emitter: FluxSink[T] => Unit, backPressure: FluxSink.OverflowStrategy = OverflowStrategy.BUFFER): SFlux[T] = new ReactiveSFlux[T](JFlux.push(emitter, backPressure))

  def range(start: Int, count: Int): SFlux[Int] = new ReactiveSFlux[Int](JFlux.range(start, count).map((i: java.lang.Integer) => Integer2int(i)))

  def using[T, D](resourceSupplier: () => D, sourceSupplier: D => Publisher[_ <: T], resourceCleanup: D => Unit, eager: Boolean = false): SFlux[T] =
    new ReactiveSFlux[T](JFlux.using[T, D](resourceSupplier, sourceSupplier, resourceCleanup, eager))

  def zip[T1, T2](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2]): SFlux[(T1, T2)] =
    new ReactiveSFlux[(T1, T2)](JFlux.zip[T1, T2, (T1, T2)](source1, source2, (t1: T1, t2: T2) => (t1, t2)))

  def zip3[T1, T2, T3](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3]): SFlux[(T1, T2, T3)] = {
    new ReactiveSFlux[(T1, T2, T3)](JFlux.zip[T1, T2, T3](source1, source2, source3)
      .map[(T1, T2, T3)]((t: Tuple3[T1, T2, T3]) => tupleThree2ScalaTuple3[T1, T2, T3](t)))
  }

  def zip4[T1, T2, T3, T4](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4]): SFlux[(T1, T2, T3, T4)] =
    new ReactiveSFlux[(T1, T2, T3, T4)](JFlux.zip[T1, T2, T3, T4](source1, source2, source3, source4)
      .map[(T1, T2, T3, T4)]((t: Tuple4[T1, T2, T3, T4]) => tupleFour2ScalaTuple4(t)))

  def zip5[T1, T2, T3, T4, T5](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5]): SFlux[(T1, T2, T3, T4, T5)] =
    new ReactiveSFlux[(T1, T2, T3, T4, T5)](JFlux.zip[T1, T2, T3, T4, T5](source1, source2, source3, source4, source5)
      .map[(T1, T2, T3, T4, T5)]((t: Tuple5[T1, T2, T3, T4, T5]) => tupleFive2ScalaTuple5(t)))

  def zip6[T1, T2, T3, T4, T5, T6](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], source3: Publisher[_ <: T3], source4: Publisher[_ <: T4], source5: Publisher[_ <: T5], source6: Publisher[_ <: T6]): SFlux[(T1, T2, T3, T4, T5, T6)] =
    new ReactiveSFlux[(T1, T2, T3, T4, T5, T6)](JFlux.zip[T1, T2, T3, T4, T5, T6](source1, source2, source3, source4, source5, source6)
      .map((t: Tuple6[T1, T2, T3, T4, T5, T6]) => tupleSix2ScalaTuple6(t)))

  def zipMap[T1, T2, O](source1: Publisher[_ <: T1], source2: Publisher[_ <: T2], combinator: (T1, T2) => O): SFlux[O] =
    new ReactiveSFlux[O](JFlux.zip[T1, T2, O](source1, source2, combinator))

  def zipMapIterable[O](sources: Iterable[_ <: Publisher[_]], combinator: Array[_] => O, prefetch: Int = SMALL_BUFFER_SIZE): SFlux[O] =
    new ReactiveSFlux[O](JFlux.zip[O](sources, prefetch, combinator))

  def zipMap[I, O](combinator: Array[AnyRef] => O, sources: Seq[Publisher[_ <: I]], prefetch: Int = XS_BUFFER_SIZE): SFlux[O] =
    new ReactiveSFlux[O](JFlux.zip[I, O](combinator, prefetch, sources: _*))
}

private[publisher] class ReactiveSFlux[T](publisher: Publisher[T]) extends SFlux[T] {
  override private[publisher] def coreFlux = JFlux.from(publisher)
}
