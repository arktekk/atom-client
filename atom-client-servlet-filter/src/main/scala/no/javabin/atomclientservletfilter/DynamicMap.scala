package no.javabin.atomclientservletfilter

class DynamicMap[K, V] extends java.util.Map[K, V] {
  private def notImplemented: Nothing = sys.error("Not implemented")
  override def entrySet = notImplemented
  override def values = notImplemented
  override def keySet = notImplemented
  override def clear() { notImplemented }
  override def putAll(map: java.util.Map[_ <: K, _ <: V]) { notImplemented }
  override def remove(o: java.lang.Object) = notImplemented
  override def get(o: java.lang.Object): V = notImplemented
  override def put(k: K, v: V): V = notImplemented
  override def containsKey(o: java.lang.Object): Boolean = notImplemented
  override def containsValue(o: java.lang.Object): Boolean = notImplemented
  override def size(): Int = notImplemented
  override def isEmpty: Boolean = notImplemented
}