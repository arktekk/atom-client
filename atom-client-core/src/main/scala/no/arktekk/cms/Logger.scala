package no.arktekk.cms

trait Logger {
  def warn(message: String)
  def info(message: String)
}

class ConsoleLogger extends Logger {
  def warn(message: String) {
    print(message)
  }

  def info(message: String) {
    print(message)
  }
}

object ConsoleLogger extends Logger {
  def warn(message: String) { println(message) }
  def info(message: String) { println(message) }
}
