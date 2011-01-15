package no.arktekk.cms

sealed class Positive(private val i: Int) {

  def +(p: Positive) = new Positive(this.i + p.i)
  def +(i: Int) = new Positive(this.i + i)
  def *(p: Positive) = new Positive(this.i * p.i)
  def *(i: Int) = new Positive(this.i * i)
  def -(p: Positive) = Positive(this.i - p.i)
  def -(i: Int) = Positive(this.i - i)
  def /(p: Positive) = Positive.fromInt(this.i / p.i)

  def <=(i: Int) = this.i <= i
  def <=(p: Positive) = this.i <= p.i

  override def toString = i.toString
  override def equals(other: Any) = other.isInstanceOf[Positive] &&
        other.asInstanceOf[Positive].i == i

  def toInt = i
}

object Positive {
  implicit def toInt(p: Positive) = p.i

  def fromInt(i: Int): Positive = if (i > 0) new Positive(i) else error("Not positive: " + i)

  def apply(i: Int): Option[Positive] = if (i > 0) Some(new Positive(i)) else None

//  def unapply(p: Positive): Option[Int] = Some(p.i)
}
