package com.uncharted.mosaic.generation

import com.uncharted.mosaic.generation.analytic.{Aggregator, ValueExtractor}
import com.uncharted.mosaic.generation.projection.Projection
import scala.reflect.ClassTag
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.{AccumulableParam, Accumulable}
import org.apache.spark.sql.Row
import scala.util.Try

/**
 * Accumulator which aggregates bin values for a tile
 * @tparam T Input data type for aggregators
 * @tparam U Intermediate data type for bin aggregators
 * @tparam V Output data type for bin aggregators, and input for tile aggregator
 */
class TileGenerationAccumulableParam[T, U: ClassTag, V](
    bProjection: Broadcast[Projection],
    bExtractor: Broadcast[ValueExtractor[T]],
    bBinAggregator: Broadcast[Aggregator[T, U, V]]
  ) extends AccumulableParam[Array[U], ((Int, Int), Row)]() {

  //will store intermediate values for the bin analytic
  private def makeBins [A:ClassTag] (length: Int, default: A): Array[A] = {
    Array.fill[A](length)(default)
  }

  override def addAccumulator(r: Array[U], t: ((Int, Int), Row)): Array[U] = {
    val bin = t._1
    val row = t._2
    val index = bin._1 + bin._2*bProjection.value.xBins
    val current = r(index)
    Try({
      val value: Option[T] = bExtractor.value.rowToValue(row)
      r(index) = bBinAggregator.value.add(current, value)
    })
    r
  }

  override def addInPlace(r1: Array[U], r2: Array[U]): Array[U] = {
    val limit = bProjection.value.xBins*bProjection.value.yBins
    val binAggregator = bBinAggregator.value
    for (i <- 0 until limit) {
      r1(i) = binAggregator.merge(r1(i), r2(i))
    }
    r1
  }

  override def zero(initialValue: Array[U]): Array[U] = {
    makeBins(bProjection.value.xBins*bProjection.value.yBins, bBinAggregator.value.default)
  }
}
