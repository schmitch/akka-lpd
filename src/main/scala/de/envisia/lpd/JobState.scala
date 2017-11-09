package de.envisia.lpd

sealed trait JobState

object JobState {

  // Job State as defined in IPP RFC
  // https://tools.ietf.org/html/rfc2911#section-4.3.7

  case object Pending extends JobState
  case object PendingHeld extends JobState
  case object Processing extends JobState
  case object ProcessingStopped extends JobState
  case object Canceled extends JobState
  case object Aborted extends JobState
  case object Completed extends JobState
  case object Unknown extends JobState

  def fromInt(value: Int): JobState = {
    value match {
      case 3 => Pending
      case 4 => PendingHeld
      case 5 => Processing
      case 6 => ProcessingStopped
      case 7 => Canceled
      case 8 => Aborted
      case 9 => Completed
      case _ => Unknown
    }
  }

}
